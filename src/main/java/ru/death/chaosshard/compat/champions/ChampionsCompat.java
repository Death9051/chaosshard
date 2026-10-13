package ru.death.chaosshard.compat.champions;

import c4.champions.common.affix.AffixRegistry;
import c4.champions.common.affix.IAffix;
import c4.champions.common.capability.CapabilityChampionship;
import c4.champions.common.capability.IChampionship;
import c4.champions.common.rank.Rank;
import c4.champions.common.rank.RankManager;
import c4.champions.common.util.ChampionHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.hero.EntityHero;
import ru.death.chaosshard.hero.HeroData;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.structure.EntityOrderKnight;
import ru.death.chaosshard.thread.ThreadManager;

/**
 * Champions: Светочи никогда не становятся чемпионами (у них своя механика), а рыцари Ордена —
 * тем чаще и тем выше рангом, чем больше Опор пало: Орден бросает в бой лучших.
 * За убитого чемпиона — Нить и Осколки мира по рангу.
 * Класс загружается только если Champions установлен.
 */
public final class ChampionsCompat {

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ChampionsCompat());
    }

    /** До обработчика Champions: решаем ранг сами, он увидит готовый и не тронет. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityLiving)) {
            return;
        }
        EntityLiving e = (EntityLiving) event.getEntity();
        if (!(e instanceof EntityHero) && !(e instanceof EntityOrderKnight)) {
            return;
        }
        IChampionship chp = CapabilityChampionship.getChampionship(e);
        if (chp == null || chp.getRank() != null) {
            return;
        }
        if (e instanceof EntityHero) {
            chp.setRank(RankManager.getEmptyRank());
            return;
        }
        int fallen = HeroData.get(event.getWorld()).fallenCount();
        if (e.world.rand.nextFloat() >= ChaosConfig.compat.championBase + ChaosConfig.compat.championPerPillar * fallen) {
            return;   // дальше решает сам Champions, как для любой твари
        }
        int maxTier = RankManager.getRanks().isEmpty() ? 0 : RankManager.getRanks().lastKey();
        int tier = Math.min(maxTier, 1 + e.world.rand.nextInt(1 + fallen / 2));
        Rank rank = tier > 0 ? RankManager.getRankForTier(tier) : null;
        if (rank == null) {
            return;
        }
        chp.setRank(rank);
        chp.setAffixes(ChampionHelper.generateAffixes(rank, e));
        chp.setName(ChampionHelper.generateRandomName());
        rank.applyGrowth(e);
        for (String id : chp.getAffixes()) {
            IAffix affix = AffixRegistry.getAffix(id);
            if (affix != null) {
                affix.onInitialSpawn(e, chp);
            }
        }
    }

    public static int tier(EntityLiving e) {
        IChampionship chp = CapabilityChampionship.getChampionship(e);
        return chp == null || chp.getRank() == null ? 0 : chp.getRank().getTier();
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityLiving) || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)
                || event.getEntityLiving().world.isRemote) {
            return;
        }
        int tier = tier((EntityLiving) event.getEntityLiving());
        if (tier > 0) {
            ThreadManager.addPoints((EntityPlayerMP) event.getSource().getTrueSource(), ChaosConfig.compat.championThread * tier);
            ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) event.getSource().getTrueSource(), "champion_kill");
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntityLiving() instanceof EntityLiving) || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)
                || !(event.getEntityLiving().world instanceof WorldServer)) {
            return;
        }
        EntityLiving e = (EntityLiving) event.getEntityLiving();
        int tier = tier(e);
        if (tier > 0) {
            event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, new ItemStack(ModItems.WORLD_SHARD, tier)));
        }
    }

    private ChampionsCompat() {
    }
}
