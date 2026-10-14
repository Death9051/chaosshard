package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.CommonProxy;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new ThreadHud());
        MinecraftForge.EVENT_BUS.register(new EnergyHud());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                ru.death.chaosshard.heart.TileHeart.class, new ru.death.chaosshard.heart.client.HeartRenderer());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.heart.client.HeartBolts());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                ru.death.chaosshard.ritual.TileRitualBowl.class, new ru.death.chaosshard.ritual.client.BowlRenderer());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                ru.death.chaosshard.anchor.TileAnchor.class, new ru.death.chaosshard.anchor.client.AnchorRenderer());
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.EntityChaosBeast.Hare.class,
                rm -> new ru.death.chaosshard.servant.client.RenderChaosBeast<>(rm, new ru.death.chaosshard.servant.client.ModelHare(), 0.3F, "hare"));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.EntityChaosBeast.Tiger.class,
                rm -> new ru.death.chaosshard.servant.client.RenderChaosBeast<>(rm, new ru.death.chaosshard.servant.client.ModelTiger(), 0.8F, "tiger"));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.EntityChaosBeast.Cat.class,
                rm -> new ru.death.chaosshard.servant.client.RenderChaosBeast<>(rm, new ru.death.chaosshard.servant.client.ModelCat(), 0.35F, "cat"));
        {
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.entity.ServantEntities.Husk.class,
                    rm -> new ru.death.chaosshard.servant.client.ServantRenders.Husk(rm, "husk"));
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.entity.ServantEntities.Reaper.class,
                    rm -> new ru.death.chaosshard.servant.client.ServantRenders.Husk(rm, "reaper"));
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.entity.ServantEntities.Lumberjack.class,
                    rm -> new ru.death.chaosshard.servant.client.ServantRenders.Husk(rm, "lumberjack"));
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.entity.ServantEntities.Miner.class,
                    rm -> new ru.death.chaosshard.servant.client.ServantRenders.Husk(rm, "miner"));
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.entity.ServantEntities.Bone.class,
                    ru.death.chaosshard.servant.client.ServantRenders.Bone::new);
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.servant.entity.ServantEntities.Executioner.class,
                    ru.death.chaosshard.servant.client.ServantRenders.Executioner::new);
        }
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                ru.death.chaosshard.structure.TileOrderBeacon.class, new ru.death.chaosshard.structure.client.OrderBeaconRenderer());
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.structure.EntityOrderKnight.class,
                ru.death.chaosshard.structure.client.RenderOrderKnight::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.hero.EntityHero.class,
                ru.death.chaosshard.hero.client.RenderHero::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.act5.EntityKing.class,
                rm -> new ru.death.chaosshard.act5.client.RenderBoss<>(rm, "king", 1.15F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.act5.EntityArchon.class,
                rm -> new ru.death.chaosshard.act5.client.RenderBoss<>(rm, "archon", 1.5F));
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ru.death.chaosshard.garrot.EntityGarrot.class,
                ru.death.chaosshard.garrot.client.RenderGarrot::new);
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                ru.death.chaosshard.ritual.TileRitualSphere.class, new ru.death.chaosshard.ritual.client.SphereRenderer());
        MagicKeys.register();
        MinecraftForge.EVENT_BUS.register(new MagicKeys());
        MinecraftForge.EVENT_BUS.register(new OreSight());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                ru.death.chaosshard.hero.TileTrophy.class, new ru.death.chaosshard.hero.client.TrophyRenderer());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.cutscene.client.CutsceneClient());
        if (ChaosShard.astralLoaded && ChaosConfig.stars.enabled) {
            MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.stars.client.AstralSkyClient());
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientTest.init();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        if (ChaosShard.astralLoaded && ChaosConfig.stars.enabled && ChaosConfig.stars.extraSkyPositions) {
            ru.death.chaosshard.stars.client.AstralSkyClient.addExtraSkyPositions();
        }
    }

    @Override
    public void openGrimoire() {
        ru.death.chaosshard.compat.BetterQuestingCompat.openQuestBook();
    }

    @Override
    public int clientThreadLevel() {
        return Math.max(0, ru.death.chaosshard.thread.ThreadManager.levelFor(ClientThreadCache.points()) - ClientThreadCache.penalty());
    }

    @Override
    public void handleThinned(int penalty) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientThreadCache.setPenalty(penalty));
    }

    @Override
    public void handleThreadSync(int points) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientThreadCache.update(points));
    }

    @Override
    public void showCredits() {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new ru.death.chaosshard.act5.client.GuiCredits()));
    }

    @Override
    public void handleCutscene(ru.death.chaosshard.network.PacketCutscene m) {
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> ru.death.chaosshard.cutscene.client.CutsceneClient.receive(m.scene));
    }

    @Override
    public void handleHeartBolt(ru.death.chaosshard.network.PacketHeartBolt bolt) {
        Minecraft.getMinecraft().addScheduledTask(() -> ru.death.chaosshard.heart.client.HeartBolts.add(bolt));
    }

    @Override
    public void handleBiomes(int x, int z, byte[] biomes) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            net.minecraft.client.multiplayer.WorldClient w = Minecraft.getMinecraft().world;
            if (w == null) {
                return;
            }
            net.minecraft.world.chunk.Chunk c = w.getChunkProvider().getLoadedChunk(x, z);
            if (c == null) {
                return;
            }
            byte[] arr = c.getBiomeArray();
            System.arraycopy(biomes, 0, arr, 0, Math.min(arr.length, biomes.length));
            w.markBlockRangeForRenderUpdate(x << 4, 0, z << 4, (x << 4) + 15, 255, (z << 4) + 15);
        });
    }

    @Override
    public String clientEntity(int id) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return "?";
        }
        net.minecraft.entity.Entity e = mc.world.getEntityByID(id);
        return e == null ? "§cнет§7" : (e.isDead ? "§cмёртв§7" : "да");
    }

    @Override
    public void handleBeasts(net.minecraft.nbt.NBTTagCompound tag) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientBeasts.set(tag));
    }

    @Override
    public void handleEnergy(int energy, int max) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientThreadCache.setEnergy(energy, max));
    }

    @Override
    public void handleSpellData(net.minecraft.nbt.NBTTagCompound data) {
        Minecraft.getMinecraft().addScheduledTask(() -> ru.death.chaosshard.spell.SpellData.client = data);
    }

    @Override
    public void handleOreSight(long[] pos, byte[] tier, int ticks) {
        Minecraft.getMinecraft().addScheduledTask(() -> OreSight.show(pos, tier, ticks));
    }

    @Override
    public void handleAnchorView(net.minecraft.nbt.NBTTagCompound data) {
        Minecraft.getMinecraft().addScheduledTask(() -> ru.death.chaosshard.anchor.client.GuiAnchor.view = data);
    }
}
