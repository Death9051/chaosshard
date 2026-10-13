package ru.death.chaosshard.hero;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.anchor.Corruption;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.order.ShrineData;
import ru.death.chaosshard.order.Shrines;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.structure.OrderStructureData;
import ru.death.chaosshard.structure.OrderStructures;
import ru.death.chaosshard.structure.TileOrderBeacon;
import ru.death.chaosshard.structure.bp.Blueprint;
import ru.death.chaosshard.structure.bp.Structures;
import ru.death.chaosshard.structure.bp.VeilPillar;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Опоры Завесы (Акт 4). Сами в мире не появляются: их вызывает Знамение — Опора встаёт
 * в ещё не созданных землях, а Гримуар называет координаты. Проходятся строго по порядку.
 * Падает Светоч — падает Опора: колонна рушится, край вокруг оскверняется, святыни окрест гаснут.
 */
public class Pillars {

    // ------------------------------------------------------------------ Знамение

    /** Применить сферу Знамения в точке at. false — не вышло (сообщение отправлено), сфера не тратится. */
    public static boolean omen(EntityPlayerMP p, World world, BlockPos at, Lightbearer h) {
        if (world.provider.getDimension() != Breach.luchezariyaDim()) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.omen.wrong_world"), true);
            return false;
        }
        if (!h.ready()) {
            p.sendMessage(Breach.grimoire("chaosshard.omen.silent", name(h)));
            return false;
        }
        HeroData data = HeroData.get(world);
        Lightbearer prev = h.previous();
        if (prev != null && !data.fallen(prev)) {
            p.sendMessage(Breach.grimoire("chaosshard.omen.order", name(prev)));
            return false;
        }
        HeroData.Pillar pl = data.of(h);
        if (pl.state == HeroData.FALLEN) {
            p.sendMessage(Breach.grimoire("chaosshard.omen.fallen", name(h)));
            return false;
        }
        if (pl.state != HeroData.NONE) {
            // уже указана: напомнить, сферу не тратить
            p.sendMessage(Breach.grimoire("chaosshard.omen.already", name(h), pl.x, pl.z, dist(at, pl.x, pl.z), side(at, pl.x, pl.z)));
            return false;
        }
        WorldServer ws = (WorldServer) world;
        int[] range = ChaosConfig.order.omenDistance;
        int near = range.length > 0 ? range[0] : 400, far = range.length > 1 ? Math.max(near, range[1]) : near * 6;
        int[] spot = findSpot(ws, at, p.rotationYaw, h.structure(), near, far, 96);
        if (spot == null) {
            p.sendMessage(Breach.grimoire("chaosshard.omen.nowhere"));
            return false;
        }
        OrderStructureData sd = OrderStructureData.get(world);
        OrderStructureData.Entry e = OrderStructures.plan(sd, h.structure(), spot[0], spot[1], world.rand.nextInt(4), world.rand.nextLong());
        pl.state = HeroData.PLANNED;
        pl.entry = e.id;
        pl.x = spot[0];
        pl.z = spot[1];
        pl.y = 0;
        data.markDirty();
        ws.addWeatherEffect(new net.minecraft.entity.effect.EntityLightningBolt(world, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, true));
        p.sendMessage(Breach.grimoire("chaosshard.omen.found", name(h), pl.x, pl.z, dist(at, pl.x, pl.z), side(at, pl.x, pl.z)));
        if (h == Lightbearer.IRENA) {
            planMonasteries(ws, pl, p, at);
        } else if (h == Lightbearer.VELMOR) {
            planWards(ws, pl);
            p.sendMessage(Breach.grimoire("chaosshard.omen.wards", WARDS));
        }
        Milestones.grant(p, h.omenMilestone());
        ChaosShard.LOG.info("Знамение: Опора {} будет у {} {}", h.id(), pl.x, pl.z);
        return true;
    }

    /**
     * Место для постройки: суша, все чанки площади ещё не созданы, вдали от других построек (spacing).
     * Ищем кольцами от ближнего к дальнему, начиная с направления взгляда.
     */
    public static int[] findSpot(WorldServer ws, BlockPos at, float yaw, String structure, int near, int far, int spacing) {
        Blueprint b = Structures.build(structure, new Random(0));
        int reach = OrderStructures.reach(b) + 18;   // запас: площадь + соседние чанки, которые заселяются вместе с ней
        ChunkProviderServer cp = ws.getChunkProvider();
        // направление взгляда: в Minecraft yaw 0 — на юг (+z)
        double look = Math.toRadians(yaw);
        double lx = -Math.sin(look), lz = Math.cos(look);
        double base = Math.atan2(lz, lx);
        for (int r = near; r <= far; r += 24) {
            int steps = Math.max(12, (int) (2 * Math.PI * r / 64));
            for (int i = 0; i < steps; i++) {
                // 0, +1, -1, +2, -2 … — сначала прямо по взгляду
                int k = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1);
                double a = base + k * 2 * Math.PI / steps;
                int x = at.getX() + (int) Math.round(Math.cos(a) * r);
                int z = at.getZ() + (int) Math.round(Math.sin(a) * r);
                if (fresh(cp, x, z, reach) && land(ws, x, z) && clearOfOthers(ws, x, z, spacing)) {
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }

    /** Ирена: три монастыря вокруг её Опоры, тоже в нетронутых землях. */
    static void planMonasteries(WorldServer ws, HeroData.Pillar pl, EntityPlayerMP p, BlockPos at) {
        OrderStructureData sd = OrderStructureData.get(ws);
        BlockPos c = new BlockPos(pl.x, 64, pl.z);
        pl.linked.clear();
        pl.shrines.clear();
        float yaw = ws.rand.nextFloat() * 360;
        for (int i = 0; i < MONASTERIES; i++) {
            int[] m = findSpot(ws, c, yaw + i * 120, "monastery", 110, 260, 90);
            if (m == null) {
                continue;
            }
            OrderStructureData.Entry e = OrderStructures.plan(sd, "monastery", m[0], m[1], ws.rand.nextInt(4), ws.rand.nextLong());
            pl.linked.add(e.id);
            p.sendMessage(Breach.grimoire("chaosshard.omen.monastery", m[0], m[1], dist(at, m[0], m[1]), side(at, m[0], m[1])));
        }
        HeroData.get(ws).markDirty();
        ChaosShard.LOG.info("Знамение Ирены: монастырей {}", pl.linked.size());
    }

    public static final int MONASTERIES = 3, WARDS = 3;

    /** Вельмор: печати отступления рядом с Опорой — координат Знамение не даёт, их надо найти. */
    static void planWards(WorldServer ws, HeroData.Pillar pl) {
        OrderStructureData sd = OrderStructureData.get(ws);
        BlockPos c = new BlockPos(pl.x, 64, pl.z);
        pl.linked.clear();
        pl.shrines.clear();
        float yaw = ws.rand.nextFloat() * 360;
        for (int i = 0; i < WARDS; i++) {
            int[] m = findSpot(ws, c, yaw + i * 120, "velmor_ward", 45, 90, 24);
            if (m == null) {
                continue;
            }
            OrderStructureData.Entry e = OrderStructures.plan(sd, "velmor_ward", m[0], m[1], 0, ws.rand.nextLong());
            pl.linked.add(e.id);
        }
        HeroData.get(ws).markDirty();
    }

    /** Печать встала. */
    public static void sealPlaced(WorldServer ws, OrderStructureData.Entry e, BlockPos pos) {
        HeroData data = HeroData.get(ws);
        for (HeroData.Pillar pl : data.pillars) {
            if (pl.linked.contains(e.id) && !pl.shrines.contains(pos)) {
                pl.shrines.add(pos.toImmutable());
                data.markDirty();
            }
        }
    }

    /** Целые печати героя (из вставших). */
    public static List<BlockPos> intactSeals(World w, Lightbearer h) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos s : HeroData.get(w).of(h).shrines) {
            if (w.isBlockLoaded(s) && w.getBlockState(s).getBlock() == ru.death.chaosshard.init.ModBlocks.VELMOR_SEAL) {
                out.add(s);
            }
        }
        return out;
    }

    /** Печать разбита (зовёт блок). */
    public static void sealBroken(WorldServer ws, BlockPos pos) {
        HeroData data = HeroData.get(ws);
        HeroData.Pillar pl = data.of(Lightbearer.VELMOR);
        if (!pl.shrines.contains(pos)) {
            return;
        }
        int left = intactSeals(ws, Lightbearer.VELMOR).size();
        int hidden = Math.max(0, pl.linked.size() - pl.shrines.size());
        for (EntityPlayer p : ws.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(pos).grow(24))) {
            p.sendMessage(Breach.grimoire("chaosshard.hero.velmor.seal_broken", left, hidden));
            if (p instanceof EntityPlayerMP) {
                Milestones.grant((EntityPlayerMP) p, "seal_broken");
            }
        }
        EntityHero v = pl.state == HeroData.STANDING ? heroOf(ws, Lightbearer.VELMOR, pl) : null;
        if (v == null) {
            List<EntityHero> l = ws.getEntitiesWithinAABB(EntityHero.class, new AxisAlignedBB(pos).grow(12), x -> x.hero() == Lightbearer.VELMOR);
            v = l.isEmpty() ? null : l.get(0);
        }
        if (v != null) {
            v.onSealBroken(pos);
        }
    }

    /** Святыня встала в постройке (вызывает OrderStructures.place). */
    public static void shrinePlaced(WorldServer ws, OrderStructureData.Entry e, BlockPos pos) {
        Shrines.register(ws, pos);
        HeroData data = HeroData.get(ws);
        for (HeroData.Pillar pl : data.pillars) {
            if (pl.linked.contains(e.id) && !pl.shrines.contains(pos)) {
                pl.shrines.add(pos.toImmutable());
                data.markDirty();
            }
        }
    }

    /** Святой камень монастыря какого-нибудь Светоча. */
    public static boolean isMonasteryShrine(World w, BlockPos pos) {
        for (HeroData.Pillar pl : HeroData.get(w).pillars) {
            if (pl.shrines.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Сколько монастырей героя ещё горит (не вставшие считаются горящими). */
    public static int lit(World w, Lightbearer h) {
        HeroData.Pillar pl = HeroData.get(w).of(h);
        int out = 0;
        java.util.Set<BlockPos> live = ShrineData.get(w).shrines;
        for (BlockPos s : pl.shrines) {
            if (!live.contains(s)) {
                out++;
            }
        }
        return Math.max(0, pl.linked.size() - out);
    }

    /** Ни один чанк площади ещё не создан. */
    static boolean fresh(ChunkProviderServer cp, int x, int z, int reach) {
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
                if (cp.isChunkGeneratedAt(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Под площадью суша: ни моря, ни реки, ни пляжа (биомы известны и до создания чанков). */
    static boolean land(WorldServer ws, int x, int z) {
        for (int dx = -16; dx <= 16; dx += 8) {
            for (int dz = -16; dz <= 16; dz += 8) {
                Biome b = ws.getBiomeProvider().getBiome(new BlockPos(x + dx, 64, z + dz));
                if (BiomeDictionary.hasType(b, BiomeDictionary.Type.OCEAN) || BiomeDictionary.hasType(b, BiomeDictionary.Type.RIVER)
                        || BiomeDictionary.hasType(b, BiomeDictionary.Type.BEACH) || BiomeDictionary.hasType(b, BiomeDictionary.Type.WATER)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean clearOfOthers(WorldServer ws, int x, int z, int spacing) {
        return OrderStructureData.get(ws).near(new BlockPos(x, 0, z), spacing) == null;
    }

    // ------------------------------------------------------------------ установка

    /** Опора встала (вызывает OrderStructures.place): запомнить высоту, поставить Светоча. */
    public static void placed(WorldServer ws, OrderStructureData.Entry e, BlockPos heroAt) {
        Lightbearer h = Lightbearer.byStructure(e.name);
        if (h == null) {
            return;
        }
        HeroData data = HeroData.get(ws);
        HeroData.Pillar pl = data.of(h);
        if (pl.state == HeroData.FALLEN) {
            return;
        }
        pl.state = HeroData.STANDING;
        pl.entry = e.id;
        pl.x = e.x;
        pl.y = e.y;
        pl.z = e.z;
        data.markDirty();
        EntityHero hero = new EntityHero(ws);
        hero.setup(h);
        hero.structureId = e.id;
        hero.setLocationAndAngles(heroAt.getX() + 0.5, heroAt.getY(), heroAt.getZ() + 0.5, ws.rand.nextFloat() * 360, 0);
        hero.setHomePosAndDistance(e.origin().up(VeilPillar.FLOOR + 1), VeilPillar.R + 4);
        ws.spawnEntity(hero);
    }

    // ------------------------------------------------------------------ падение

    /** Светоч пал: Опора рушится. killer — кто добил (может быть null). */
    public static void fall(WorldServer ws, Lightbearer h, BlockPos heroAt, EntityPlayerMP killer) {
        HeroData data = HeroData.get(ws);
        HeroData.Pillar pl = data.of(h);
        if (pl.state == HeroData.FALLEN) {
            return;
        }
        OrderStructureData sd = OrderStructureData.get(ws);
        OrderStructureData.Entry e = pl.entry >= 0 ? sd.byId(pl.entry) : null;
        BlockPos o = e != null ? e.origin() : new BlockPos(pl.x, pl.y, pl.z);
        pl.state = HeroData.FALLEN;
        pl.x = o.getX();
        pl.y = o.getY();
        pl.z = o.getZ();
        pl.collapseAt = ws.getTotalWorldTime() + COLLAPSE_DELAY;
        data.markDirty();
        if (e != null) {
            e.keeperDead = true;
            sd.markDirty();
        }
        // трофей — к ногам павшего
        ItemStack trophy = Trophies.of(h);
        if (!trophy.isEmpty()) {
            EntityItem ei = new EntityItem(ws, heroAt.getX() + 0.5, heroAt.getY() + 1, heroAt.getZ() + 0.5, trophy);
            ei.setEntityInvulnerable(true);
            ei.setNoDespawn();
            ws.spawnEntity(ei);
        }
        int reward = ChaosConfig.order.pillarReward * (2 + h.ordinal()) / 2;
        for (EntityPlayerMP p : witnesses(ws, o, killer)) {
            ThreadManager.addPoints(p, reward);
            p.sendMessage(Breach.grimoire("chaosshard.pillar.fallen", name(h), reward));
            Milestones.grant(p, h.fallenMilestone());
            if (!(p instanceof net.minecraftforge.common.util.FakePlayer)) {
                ru.death.chaosshard.cutscene.CutsceneManager.play(p, HeroScenes.fall(ws, o, p, h), HeroScenes.NONE);
            }
        }
        ChaosShard.LOG.info("Опора {} пала у {}", h.id(), o);
    }

    public static final int COLLAPSE_DELAY = 25;

    static List<EntityPlayerMP> witnesses(WorldServer ws, BlockPos o, EntityPlayerMP killer) {
        List<EntityPlayerMP> out = new ArrayList<>();
        for (EntityPlayer pl2 : ws.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(o).grow(96))) {
            if (pl2 instanceof EntityPlayerMP) {
                out.add((EntityPlayerMP) pl2);
            }
        }
        if (killer != null && !out.contains(killer)) {
            out.add(killer);
        }
        return out;
    }

    /** Колонна рушится по-настоящему: луч гаснет, святыни окрест гаснут, пошла волна Скверны. */
    static void collapseNow(WorldServer ws, Lightbearer h, HeroData.Pillar pl) {
        pl.collapseAt = 0;
        pl.fallRing = 0;
        BlockPos o = pl.pos();
        OrderStructureData.Entry e = pl.entry >= 0 ? OrderStructureData.get(ws).byId(pl.entry) : null;
        if (e != null) {
            for (BlockPos b : e.beams) {
                TileEntity te = ws.getTileEntity(b);
                if (te instanceof TileOrderBeacon) {
                    ((TileOrderBeacon) te).setLit(false);
                }
            }
        }
        collapse(ws, o);
        int sr = ChaosConfig.order.pillarShrineRadius;
        int out = 0;
        for (BlockPos s : new ArrayList<>(ShrineData.get(ws).shrines)) {
            if (s.distanceSq(o.getX(), s.getY(), o.getZ()) <= (double) sr * sr) {
                Shrines.extinguish(ws, s);
                out++;
            }
        }
        for (EntityPlayerMP p : witnesses(ws, o, null)) {
            if (out > 0) {
                p.sendMessage(Breach.grimoire("chaosshard.pillar.shrines", out));
            }
            p.sendMessage(Breach.grimoire("chaosshard.hero." + h.id() + ".memory"));
        }
        HeroData.get(ws).markDirty();
    }

    /** Колонна рушится: верх исчезает, обломки сыплются на площадь, остаётся пень. */
    static void collapse(WorldServer ws, BlockPos o) {
        int floor = o.getY() + VeilPillar.FLOOR;
        int stump = floor + 6;
        int top = floor + VeilPillar.COLUMN_H + 3;
        int cr = (int) Math.ceil(VeilPillar.COLUMN_R + 1);
        Random r = ws.rand;
        IBlockState rubbleA = Blocks.STONEBRICK.getStateFromMeta(2);   // треснувший
        IBlockState rubbleB = Blocks.QUARTZ_BLOCK.getDefaultState();
        for (int y = top; y > stump; y--) {
            for (int dx = -cr; dx <= cr; dx++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    BlockPos p = new BlockPos(o.getX() + dx, y, o.getZ() + dz);
                    if (!ws.isAirBlock(p)) {
                        ws.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
            if ((y - stump) % 4 == 0) {
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, o.getX() + 0.5, y, o.getZ() + 0.5, 2, 1.5, 0.5, 1.5, 0.0);
            }
        }
        // обломки: падающие блоки разлетаются от колонны
        for (int i = 0; i < 36; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double h = stump + 2 + r.nextInt(14);
            EntityFallingBlock fb = new EntityFallingBlock(ws, o.getX() + 0.5 + Math.cos(a) * 2, h, o.getZ() + 0.5 + Math.sin(a) * 2,
                    r.nextInt(3) == 0 ? rubbleB : rubbleA);
            double sp = 0.25 + r.nextDouble() * 0.45;
            fb.motionX = Math.cos(a) * sp;
            fb.motionZ = Math.sin(a) * sp;
            fb.motionY = 0.2 + r.nextDouble() * 0.3;
            fb.fallTime = 1;
            fb.shouldDropItem = false;
            ws.spawnEntity(fb);
        }
        ws.playSound(null, o.up(VeilPillar.FLOOR + 10), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0F, 0.5F);
        ws.playSound(null, o.up(VeilPillar.FLOOR + 10), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 2.0F, 0.6F);
        ws.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, o.getX() + 0.5, stump + 4, o.getZ() + 0.5, 6, 3, 4, 3, 0.0);
        ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, o.getX() + 0.5, floor + 2, o.getZ() + 0.5, 300, 8, 2, 8, 0.1);
    }

    // ------------------------------------------------------------------ волна Скверны

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || event.world.provider.getDimension() != Breach.luchezariyaDim()) {
            return;
        }
        WorldServer ws = (WorldServer) event.world;
        long now = ws.getTotalWorldTime();
        HeroData data = HeroData.get(ws);
        for (Lightbearer h : Lightbearer.values()) {
            HeroData.Pillar pl = data.of(h);
            if (pl.collapseAt > 0 && now >= pl.collapseAt) {
                collapseNow(ws, h, pl);
            }
            if (pl.state == HeroData.STANDING && !pl.introShown && now % 10 == 3) {
                tryIntro(ws, h, pl);
            }
            if (pl.fallRing >= 0 && now % 4 == 0) {
                defileRing(ws, pl.pos(), pl.fallRing);
                pl.fallRing++;
                if (pl.fallRing > ChaosConfig.order.pillarDefileRadius) {
                    pl.fallRing = -1;
                }
                data.markDirty();
            }
        }
    }

    /** Первый, кто подошёл к Светочу, видит его появление. */
    static void tryIntro(WorldServer ws, Lightbearer h, HeroData.Pillar pl) {
        EntityHero hero = heroOf(ws, h, pl);
        if (hero == null) {
            return;
        }
        for (EntityPlayer p : ws.playerEntities) {
            if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer) && !p.isSpectator()
                    && p.getDistanceSq(hero) < INTRO_RANGE * INTRO_RANGE && !ru.death.chaosshard.cutscene.CutsceneManager.playing(p)) {
                pl.introShown = true;
                HeroData.get(ws).markDirty();
                intro((EntityPlayerMP) p, hero);
                return;
            }
        }
    }

    public static final int INTRO_RANGE = 30;

    /** Показать появление героя игроку (и для команды отладки). */
    public static void intro(EntityPlayerMP p, EntityHero hero) {
        // повернуть игрока к герою — в конце сцены камера вернётся в его глаза без рывка
        double dx = hero.posX - p.posX, dz = hero.posZ - p.posZ, dy = hero.posY + 1.6 - (p.posY + p.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.connection.setPlayerLocation(p.posX, p.posY, p.posZ, yaw, pitch);
        p.rotationYaw = p.rotationYawHead = yaw;
        p.rotationPitch = pitch;
        hero.scripted = true;
        ru.death.chaosshard.cutscene.CutsceneManager.play(p, HeroScenes.intro((WorldServer) p.world, hero, p), new HeroScenes.Intro(hero));
    }

    /** Светоч этой Опоры (загруженный) или null. */
    public static EntityHero heroOf(WorldServer ws, Lightbearer h, HeroData.Pillar pl) {
        List<EntityHero> l = ws.getEntitiesWithinAABB(EntityHero.class, new AxisAlignedBB(pl.pos()).grow(40, 48, 40),
                x -> x.isEntityAlive() && x.hero() == h);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Осквернить кольцо радиуса r вокруг Опоры: верхние слои земли и биом. */
    public static int defileRing(WorldServer ws, BlockPos o, int r) {
        int n = 0;
        Set<Long> chunks = new HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5 || d <= r - 0.5) {
                    continue;
                }
                int x = o.getX() + dx, z = o.getZ() + dz;
                // края волны рваные
                if (d > ChaosConfig.order.pillarDefileRadius - 6 && ws.rand.nextInt(3) == 0) {
                    continue;
                }
                BlockPos top = ws.getHeight(new BlockPos(x, 0, z));
                for (int y = top.getY(); y >= Math.max(1, top.getY() - 5); y--) {
                    if (Corruption.corrupt(ws, new BlockPos(x, y, z))) {
                        n++;
                    }
                }
                if (Corruption.corruptBiome(ws, top)) {
                    chunks.add(net.minecraft.util.math.ChunkPos.asLong(x >> 4, z >> 4));
                }
            }
        }
        if (!chunks.isEmpty()) {
            Corruption.resendChunks(ws, chunks);
        }
        if (r % 4 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                BlockPos t = ws.getHeight(new BlockPos(o.getX() + Math.cos(a) * r, 0, o.getZ() + Math.sin(a) * r));
                ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, 6, 0.6, 0.3, 0.6, 0.0);
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ мелочи

    public static TextComponentTranslation name(Lightbearer h) {
        return new TextComponentTranslation(h.nameKey());
    }

    static int dist(BlockPos at, int x, int z) {
        return (int) Math.sqrt((double) (x - at.getX()) * (x - at.getX()) + (double) (z - at.getZ()) * (z - at.getZ()));
    }

    static String side(BlockPos at, int x, int z) {
        long dx = x - at.getX(), dz = z - at.getZ();
        String ns = dz < -Math.abs(dx) / 2 ? "С" : dz > Math.abs(dx) / 2 ? "Ю" : "";
        String ew = dx > Math.abs(dz) / 2 ? "В" : dx < -Math.abs(dz) / 2 ? "З" : "";
        String s = ns + ew;
        return s.isEmpty() ? "здесь" : s;
    }
}
