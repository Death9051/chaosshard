package ru.death.chaosshard.quest;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.heart.HeartPlacer;
import ru.death.chaosshard.heart.TileHeart;

/**
 * Вехи Осколка — скрытые достижения chaosshard:m/&lt;id&gt;. Квесты Better Questing проверяют их
 * задачей «advancement», поэтому отмечать квесты руками не нужно.
 * Большинство вех проверяется раз в две секунды по состоянию мира — так они засчитываются
 * и задним числом, если игрок сделал дело раньше, чем открыл квест.
 */
public class Milestones {

    public static final String SPELL = "spell_cast";
    public static final String HEART_POWERED = "heart_powered";
    public static final String DEFILED_POWER = "defiled_power";
    public static final String ANCHOR_HUM = "anchor_hum";
    public static final String WITHER_SLAIN = "wither_slain";
    public static final String ORDER_RAID = "order_raid";
    public static final String CITADEL_RECALL = "citadel_recall";
    public static final String CHAOS_LINK = "chaos_link";
    public static final String CROWN_OMEN = "crown_omen";
    public static final String BEACON_OUT = "beacon_out";
    public static final String CAPITAL_OPEN = "capital_open";
    public static final String KING_SLAIN = "king_slain";
    public static final String RIFT_ENTERED = "rift_entered";
    public static final String PYLON_BROKEN = "pylon_broken";
    public static final String ARCHON_SLAIN = "archon_slain";
    public static final String GAME_COMPLETE = "game_complete";
    public static final String DRAGON_SLAIN = "dragon_slain";
    public static final String ANCHOR_WAYSTONE = "anchor_waystone";
    public static final String SERVANT_FED = "servant_fed";
    public static final String POWER_MEKANISM = "power_mekanism";
    public static final String POWER_THERMAL = "power_thermal";
    public static final String POWER_EXU2 = "power_exu2";
    public static final String POWER_CYCLIC = "power_cyclic";
    public static final String SKY = "chaos_sky";
    public static final String AGE = "enter_age";
    public static final String TWO_AGES = "two_ages";
    public static final String BREACH = "breach_through";
    public static final String HOME = "breach_return";
    public static final String ANCHOR = "anchor_placed";
    public static final String CAPTURE_1 = "capture_1";
    public static final String CAPTURE_3 = "capture_3";
    public static final String TINKER_STAR = "tinker_starmetal";
    public static final String TINKER_DISTORTED = "tinker_distorted";
    public static final String TINKER_ASTRAL = "tinker_astral";
    public static final String DYNAMO = "starlight_dynamo";
    public static final String SHRINE_NEAR = "shrine_near";
    public static final String SHRINE_DESECRATED = "shrine_desecrated";
    public static final String VILLAGE_PATRON = "village_patron";
    public static final String VILLAGE_FALLEN = "village_fallen";
    public static final String GARROT_1 = "garrot_1";
    public static final String GARROT_3 = "garrot_3";
    public static final String RITUAL = "ritual_done";
    public static final String HIGH_RITUAL = "high_ritual";
    public static final String CHALLENGE_START = "challenge_start", CHALLENGE = "challenge_won", CHALLENGE_TOP = "challenge_top",
            CHALLENGE_ENDLESS = "challenge_endless";
    public static final String ANCHOR_MAX = "anchor_max";
    public static final String IMPLANT = "implant_installed";
    public static final String CHAOS_CORE = "chaos_core";
    public static final String MORPH_1 = "morph_first";
    public static final String MORPH_5 = "morph_five";
    public static final String SPAWNER = "spawner_broken";
    public static final String SPAWNER_DEEP = "spawner_deep";
    public static final String POCKET_DUNGEON = "pocket_dungeon";
    public static final String POCKET_PERSONAL = "pocket_personal";
    public static final String LIMBO = "limbo";
    public static final String CHAOS_PERK = "chaos_perk";
    public static final String PUMP = "star_pump";
    public static final String TRIAL = "trial_done";
    public static final String TRIAL_3 = "trial_three";
    public static final String TRIAL_HARD = "trial_hard";
    public static final String LAIR_1 = "lair_1", LAIR_2 = "lair_2", LAIR_3 = "lair_3";
    public static final String DRAGON = "dragon_scales";
    public static final String SERVANT = "servant_first", SERVANT_BONE = "servant_bone", SERVANT_EXEC = "servant_exec", SERVANT_3 = "servant_three";
    public static final String VILLAGE_EVOLVED = "village_evolved";

    private static final String TAG_AGES = "chaosshard_ages";
    private static final String TAG_CAPTURED = "chaosshard_captured";

    public static void grant(EntityPlayerMP player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement adv = server.getAdvancementManager().getAdvancement(new ResourceLocation(Tags.MOD_ID, "m/" + id));
        if (adv == null) {
            ChaosShard.LOG.warn("Нет вехи chaosshard:m/{}", id);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getProgress(adv);
        if (!progress.isDone()) {
            player.getAdvancements().grantCriterion(adv, "done");
        }
    }

    public static boolean has(EntityPlayerMP player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Advancement adv = server.getAdvancementManager().getAdvancement(new ResourceLocation(Tags.MOD_ID, "m/" + id));
        return adv != null && player.getAdvancements().getProgress(adv).isDone();
    }

    /** Захват края Якорем: счётчик у владельца. */
    public static void onCapture(EntityPlayerMP owner) {
        NBTTagCompound data = persisted(owner);
        data.setInteger(TAG_CAPTURED, data.getInteger(TAG_CAPTURED) + 1);
    }

    /** Эндер-дракон пал: каждый в Энде получает чешую дракона. */
    @SubscribeEvent
    public void onDragon(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof net.minecraft.entity.boss.EntityDragon) || event.getEntityLiving().world.isRemote) {
            return;
        }
        World w = event.getEntityLiving().world;
        for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities) {
            if (pl instanceof EntityPlayerMP && pl.getDistanceSq(event.getEntityLiving()) < 256 * 256) {
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(pl,
                        new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.DRAGON_SCALE, 6 + w.rand.nextInt(5)));
                grant((EntityPlayerMP) pl, DRAGON);
            }
        }
    }

    /** Сломанный рассадник тварей (подземелья Roguelike и любые другие). */
    @SubscribeEvent
    public void onBreak(net.minecraftforge.event.world.BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote || !(event.getPlayer() instanceof EntityPlayerMP)
                || event.getState().getBlock() != net.minecraft.init.Blocks.MOB_SPAWNER) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.getPlayer();
        grant(p, SPAWNER);
        if (event.getPos().getY() <= 20) {
            grant(p, SPAWNER_DEEP);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.ticksExisted % 40 != 7) {
            return;
        }
        try {
            check(player);
            if (Loader.isModLoaded("betterquesting")) {
                BqSync.detect(player);
            }
        } catch (Throwable t) {
            ChaosShard.LOG.error("Проверка вех упала", t);
        }
    }

    private void check(EntityPlayerMP player) {
        World world = player.world;
        boolean inAntechamber = AntechamberDimension.is(world);

        // Небо Преддверия: посмотреть вверх
        if (inAntechamber && player.rotationPitch < -55.0F) {
            grant(player, SKY);
        }

        // Сердце Острова: ступени
        WorldServer ante = DimensionManager.getWorld(AntechamberDimension.id());
        if (ante != null && ante.isBlockLoaded(HeartPlacer.heartPos())) {
            TileEntity te = ante.getTileEntity(HeartPlacer.heartPos());
            if (te instanceof TileHeart) {
                int tier = ((TileHeart) te).getTier();
                for (int t = 2; t <= tier; t++) {
                    grant(player, "heart_tier_" + t);
                }
            }
        }

        // Dimensional Doors: карманные подземелья, личный карман, Лимб
        String provider = world.provider.getClass().getSimpleName();
        if (provider.equals("WorldProviderDungeonPocket")) {
            grant(player, POCKET_DUNGEON);
        } else if (provider.equals("WorldProviderPersonalPocket")) {
            grant(player, POCKET_PERSONAL);
        } else if (provider.equals("WorldProviderLimbo")) {
            grant(player, LIMBO);
        }

        // Mystcraft: миры-Кладовки
        if (world.provider.getClass().getName().contains("mystcraft")) {
            grant(player, AGE);
            NBTTagCompound data = persisted(player);
            int[] ages = data.getIntArray(TAG_AGES);
            int dim = world.provider.getDimension();
            boolean known = false;
            for (int a : ages) {
                known |= a == dim;
            }
            if (!known) {
                int[] next = java.util.Arrays.copyOf(ages, ages.length + 1);
                next[ages.length] = dim;
                data.setIntArray(TAG_AGES, next);
                ages = next;
            }
            if (ages.length >= 2) {
                grant(player, TWO_AGES);
            }
        }

        // Прорыв
        if (Breach.hasBreached(player)) {
            if (world.provider.getDimension() == Breach.luchezariyaDim()) {
                grant(player, BREACH);
            }
            if (inAntechamber && Breach.hasReturnPoint(player)) {
                grant(player, HOME);
            }
        }

        // Якоря
        if (!has(player, ANCHOR)) {
            WorldServer luch = DimensionManager.getWorld(Breach.luchezariyaDim());
            if (luch != null && !AnchorData.get(luch).anchors.isEmpty()) {
                grant(player, ANCHOR);
            }
        }
        int captured = persisted(player).getInteger(TAG_CAPTURED);
        if (captured >= 1) {
            grant(player, CAPTURE_1);
        }
        if (captured >= 3) {
            grant(player, CAPTURE_3);
        }

        // Инструменты Tinkers' Construct в инвентаре
        if (!has(player, TINKER_STAR) || !has(player, TINKER_DISTORTED) || !has(player, TINKER_ASTRAL)) {
            for (ItemStack stack : player.inventory.mainInventory) {
                tinkerTool(player, stack);
            }
            tinkerTool(player, player.getHeldItemOffhand());
        }

        // Компрессионный динамо Thermal на жидком звёздном свете
        if (!has(player, DYNAMO)) {
            for (TileEntity te : world.loadedTileEntityList) {
                if (te.getClass().getSimpleName().equals("TileDynamoCompression")
                        && te.getPos().distanceSq(player.getPosition()) < 32 * 32 && TileChecks.burnsStarlight(te)) {
                    grant(player, DYNAMO);
                    break;
                }
            }
        }

        if (ChaosShard.astralLoaded) {
            AstralMilestones.check(player);
        }
    }

    private static void tinkerTool(EntityPlayerMP player, ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagCompound data = tag.getCompoundTag("TinkerData");
        NBTTagList materials = data.getTagList("Materials", 8);
        for (int i = 0; i < materials.tagCount(); i++) {
            String m = materials.getStringTagAt(i).toLowerCase();
            if (m.contains("chaosshard_distorted")) {
                grant(player, TINKER_DISTORTED);
            } else if (m.contains("starmetal")) {
                grant(player, TINKER_STAR);
            }
        }
        NBTTagList modifiers = tag.getTagList("Modifiers", 10);
        for (int i = 0; i < modifiers.tagCount(); i++) {
            String id = modifiers.getCompoundTagAt(i).getString("identifier").toLowerCase();
            if (id.contains("astral")) {
                grant(player, TINKER_ASTRAL);
            }
        }
        NBTBase mods = data.getTag("Modifiers");
        if (mods instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) mods;
            for (int i = 0; i < list.tagCount(); i++) {
                if (list.getStringTagAt(i).toLowerCase().contains("astral")) {
                    grant(player, TINKER_ASTRAL);
                }
            }
        }
    }

    static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
