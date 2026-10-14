package ru.death.chaosshard.stars;

import hellfirepvp.astralsorcery.common.constellation.ConstellationRegistry;
import hellfirepvp.astralsorcery.common.constellation.IConstellation;
import hellfirepvp.astralsorcery.common.constellation.IMajorConstellation;
import hellfirepvp.astralsorcery.common.constellation.perk.AbstractPerk;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.PerkTree;
import hellfirepvp.astralsorcery.common.data.research.PlayerProgress;
import hellfirepvp.astralsorcery.common.data.research.ResearchManager;
import hellfirepvp.astralsorcery.common.data.research.ResearchProgression;
import hellfirepvp.astralsorcery.common.tile.TileAttunementAltar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.quest.Milestones;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Связь с Хаосом: настройка на созвездие Хаоса на Алтаре настройки Astral.
 *
 * Хаос — не «главное» созвездие, и обычная настройка игрока его не принимает. Поэтому связь идёт своим путём:
 * Осколок стоит на алтаре, где сложено созвездие Хаоса, — и через 10 секунд Хаос считается настройкой
 * на все пять главных созвездий: открываются все корни древа перков.
 *  - Не настроен ни на что: настраиваемся на первое известное главное созвездие, остальные корни — сверху.
 *  - Уже настроен: взятые перки остаются, недостающие корни добавляются.
 * Каждый лишний корень оплачивается свободным очком перков (токен на созвездие), так что очки не теряются.
 * Если игрок потом перенастроится на алтаре (Astral сбросит древо), корни вернутся сами.
 */
public final class ChaosLink {

    public static final String TAG = "chaosshard_chaos_link";
    public static final String TOKEN_PREFIX = "chaosshard-link-";
    /** Сколько тиков стоять на алтаре. */
    public static final int NEED = 200;
    private static final int STEP = 10;

    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    private static Field activeFound;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || !(event.player instanceof EntityPlayerMP)
                || event.player instanceof net.minecraftforge.common.util.FakePlayer || AstralCompat.CHAOS == null) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        long now = p.world.getTotalWorldTime();
        if (now % STEP != 0) {
            return;
        }
        if (linked(p) && now % 200 == 0) {
            ensure(p);
        }
        TileAttunementAltar altar = altarUnder(p);
        if (altar == null || !AstralCompat.isChaos(active(altar)) || p.isSneaking()) {
            PROGRESS.remove(p.getUniqueID());
            return;
        }
        PlayerProgress pr = ResearchManager.getProgress(p, Side.SERVER);
        if (pr == null || !pr.isValid()) {
            return;
        }
        if (linked(p) && missing(pr) == 0) {
            return;
        }
        String why = null;
        if (!pr.getResearchProgression().contains(ResearchProgression.ATTUNEMENT)) {
            why = "chaosshard.chaos_link.need_attunement";
        } else if (!pr.getKnownConstellations().contains(AstralCompat.CHAOS.getUnlocalizedName())) {
            why = "chaosshard.chaos_link.need_chaos";
        } else if (pr.getAttunedConstellation() == null && firstKnownMajor(pr) == null) {
            why = "chaosshard.chaos_link.need_major";
        }
        if (why != null) {
            Long last = WARNED.get(p.getUniqueID());
            if (last == null || now - last > 200) {
                WARNED.put(p.getUniqueID(), now);
                p.sendStatusMessage(Breach.grimoire(why), true);
            }
            return;
        }
        int t = PROGRESS.merge(p.getUniqueID(), STEP, Integer::sum);
        WorldServer ws = (WorldServer) p.world;
        ws.spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 10 + t / 5, 0.4, 0.8, 0.4, 0.6);
        ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, p.posX, p.posY + 2.2, p.posZ, 4, 0.3, 0.2, 0.3, 0.05);
        if (t == STEP) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.chaos_link.begin"), true);
            p.world.playSound(null, p.getPosition(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0F, 0.6F);
        }
        if (t >= NEED) {
            PROGRESS.remove(p.getUniqueID());
            link(p);
        }
    }

    /** Связать игрока с Хаосом. Возвращает, сколько корней добавлено (-1 — не вышло). */
    public static int link(EntityPlayerMP p) {
        PlayerProgress pr = ResearchManager.getProgress(p, Side.SERVER);
        if (pr == null || !pr.isValid()) {
            return -1;
        }
        if (pr.getAttunedConstellation() == null) {
            IMajorConstellation first = firstKnownMajor(pr);
            if (first == null || !ResearchManager.setAttunedConstellation(p, first)) {
                p.sendStatusMessage(Breach.grimoire("chaosshard.chaos_link.need_major"), true);
                return -1;
            }
        }
        persisted(p).setBoolean(TAG, true);
        int n = ensure(p);
        WorldServer ws = (WorldServer) p.world;
        ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, p.posX, p.posY + 1, p.posZ, 80, 0.6, 1.0, 0.6, 0.05);
        p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 1.2F, 0.5F);
        p.sendMessage(Breach.grimoire("chaosshard.chaos_link.done", n));
        Milestones.grant(p, Milestones.CHAOS_LINK);
        return n;
    }

    /** Добавить недостающие корни главных созвездий. Возвращает, сколько добавлено. */
    public static int ensure(EntityPlayerMP p) {
        PlayerProgress pr = ResearchManager.getProgress(p, Side.SERVER);
        if (pr == null || !pr.isValid() || pr.getAttunedConstellation() == null) {
            return 0;
        }
        int n = 0;
        for (IMajorConstellation m : ConstellationRegistry.getMajorConstellations()) {
            AbstractPerk root = PerkTree.PERK_TREE.getRootPerk(m);
            if (root == null || pr.hasPerkUnlocked(root)) {
                continue;
            }
            String token = TOKEN_PREFIX + m.getSimpleName();
            if (!pr.getFreePointTokens().contains(token)) {
                ResearchManager.grantFreePerkPoint(p, token);
            }
            if (ResearchManager.forceApplyPerk(p, root)) {
                n++;
            }
        }
        return n;
    }

    /** Сколько корней главных созвездий ещё не открыто. */
    public static int missing(PlayerProgress pr) {
        int n = 0;
        for (IMajorConstellation m : ConstellationRegistry.getMajorConstellations()) {
            AbstractPerk root = PerkTree.PERK_TREE.getRootPerk(m);
            if (root != null && !pr.hasPerkUnlocked(root)) {
                n++;
            }
        }
        return n;
    }

    public static boolean linked(EntityPlayer p) {
        return persisted(p).getBoolean(TAG);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static IMajorConstellation firstKnownMajor(PlayerProgress pr) {
        for (IMajorConstellation m : ConstellationRegistry.getMajorConstellations()) {
            if (pr.getKnownConstellations().contains(m.getUnlocalizedName())) {
                return m;
            }
        }
        return null;
    }

    /** Алтарь настройки под ногами (или в шаге от игрока). */
    private static TileAttunementAltar altarUnder(EntityPlayerMP p) {
        BlockPos at = p.getPosition();
        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    TileEntity te = p.world.getTileEntity(at.add(dx, dy, dz));
                    if (te instanceof TileAttunementAltar) {
                        return (TileAttunementAltar) te;
                    }
                }
            }
        }
        return null;
    }

    /** Созвездие, сложенное на алтаре (поле activeFound, наружу не отдаётся). */
    private static IConstellation active(TileAttunementAltar altar) {
        try {
            if (activeFound == null) {
                activeFound = TileAttunementAltar.class.getDeclaredField("activeFound");
                activeFound.setAccessible(true);
            }
            return (IConstellation) activeFound.get(altar);
        } catch (ReflectiveOperationException e) {
            ChaosShard.LOG.warn("Не читается созвездие алтаря настройки", e);
            return null;
        }
    }

    private ChaosLink() {
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        PROGRESS.clear();
        WARNED.clear();
    }
}
