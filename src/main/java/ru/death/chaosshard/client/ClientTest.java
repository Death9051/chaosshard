package ru.death.chaosshard.client;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиентский прогон для CI (-Dchaosshard.clienttest=true): главное меню, плоский творческий мир, все блоки и существа мода
 * перед камерой, окна Гримуара, Сердца, Якоря и вкладка мода в творческом инвентаре — со снимками экрана.
 * Заодно проверяет модели предметов и блоков, отрисовщики существ и названия. Отчёт — chaosshard-clienttest.txt, затем выход.
 */
@SideOnly(Side.CLIENT)
public class ClientTest {

    public static void init() {
        if (Boolean.getBoolean("chaosshard.clienttest")) {
            MinecraftForge.EVENT_BUS.register(new ClientTest());
            ChaosShard.LOG.info("[clienttest] включён");
        }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<String> out = new ArrayList<>();
    private int pass, fail;
    private int step, wait, ticks;
    /** Снять экран после ближайшего кадра. */
    private String shot;
    private BlockPos origin, heart, anchor;
    private final List<Entity> shown = new ArrayList<>();

    private void line(String s) {
        out.add(s);
        ChaosShard.LOG.info("[clienttest] {}", s);
    }

    private void check(boolean ok, String what) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        line((ok ? "  [OK]   " : "  [FAIL] ") + what);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || shot == null) {
            return;
        }
        try {
            ScreenShotHelper.saveScreenshot(mc.gameDir, "ct_" + shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            line("  снимок: ct_" + shot + ".png");
        } catch (Throwable t) {
            line("  снимок не снят: " + t);
        }
        shot = null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (ticks > 20 * 60 * 8) {
            line("время вышло на шаге " + step);
            finish();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            next();
        } catch (Throwable t) {
            check(false, "шаг " + step + ": " + t);
            ChaosShard.LOG.error("[clienttest] шаг {}", step, t);
            step++;
            wait = 20;
        }
    }

    private void next() throws Exception {
        switch (step) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                line("=== Осколок Хаоса — клиентский прогон ===");
                check(true, "главное меню открылось");
                assets();
                wait = 40;
                step++;
                return;
            case 1:
                shot = "01_menu";
                wait = 10;
                step++;
                return;
            case 2: {
                WorldSettings ws = new WorldSettings(4242L, GameType.CREATIVE, false, false, WorldType.FLAT);
                ws.enableCommands();
                mc.launchIntegratedServer("chaosshard_clienttest", "chaosshard_clienttest", ws);
                step++;
                return;
            }
            case 3:
                if (mc.player == null || mc.world == null || mc.getIntegratedServer() == null) {
                    return;
                }
                if (mc.currentScreen != null) {
                    mc.displayGuiScreen(null);
                    return;
                }
                check(true, "мир загружен");
                wait = 100;
                step++;
                return;
            case 4:
                onServer(this::buildShowcase);
                wait = 80;
                step++;
                return;
            case 5:
                shot = "02_blocks";
                wait = 10;
                step++;
                return;
            case 6:
                onServer(this::turnBack);
                wait = 40;
                step++;
                return;
            case 7:
                shot = "03_entities";
                wait = 10;
                step++;
                return;
            case 8:
                onServer(this::clearEntities);
                magic(0);
                step++;
                return;
            case 9:
                shot = "04_grimoire_spells";
                wait = 5;
                step++;
                return;
            case 10:
                magic(1);
                step++;
                return;
            case 11:
                shot = "05_grimoire_rituals";
                wait = 5;
                step++;
                return;
            case 12: {
                ReflectionHelper.findField(ru.death.chaosshard.client.magic.GuiGrimoireMagic.class, "ritScroll").setInt(null, 100);
                wait = 10;
                step++;
                return;
            }
            case 13:
                shot = "06_grimoire_rituals_end";
                wait = 5;
                step++;
                return;
            case 14:
                magic(2);
                step++;
                return;
            case 15:
                shot = "07_grimoire_servants";
                wait = 5;
                step++;
                return;
            case 16:
                mc.displayGuiScreen(new ru.death.chaosshard.client.magic.GuiSpellWheel(mc.player.getHeldItemMainhand()));
                wait = 15;
                step++;
                return;
            case 17:
                shot = "08_spell_wheel";
                wait = 5;
                step++;
                return;
            case 18:
                mc.displayGuiScreen(null);
                onServer(() -> open(ru.death.chaosshard.heart.HeartGuiHandler.HEART, heart));
                wait = 20;
                step++;
                return;
            case 19:
                check(mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().contains("Heart"), "окно Сердца открылось: " + name(mc.currentScreen));
                shot = "09_heart";
                wait = 5;
                step++;
                return;
            case 20:
                mc.displayGuiScreen(null);
                onServer(() -> open(ru.death.chaosshard.heart.HeartGuiHandler.ANCHOR, anchor));
                wait = 20;
                step++;
                return;
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28: {
                int t = (step - 21) / 2;
                if ((step - 21) % 2 == 0) {
                    if (t == 0) {
                        check(mc.currentScreen instanceof ru.death.chaosshard.anchor.client.GuiAnchor, "окно Якоря открылось: " + name(mc.currentScreen));
                    }
                    if (mc.currentScreen instanceof ru.death.chaosshard.anchor.client.GuiAnchor) {
                        ReflectionHelper.findField(ru.death.chaosshard.anchor.client.GuiAnchor.class, "tab").setInt(mc.currentScreen, t);
                        mc.currentScreen.initGui();
                    }
                    wait = 10;
                    step++;
                } else {
                    shot = "1" + t + "_anchor_tab" + t;
                    wait = 5;
                    step = step == 28 ? 40 : step + 1;
                }
                return;
            }
            case 40: {
                mc.displayGuiScreen(null);
                GuiContainerCreative gui = new GuiContainerCreative(mc.player);
                mc.displayGuiScreen(gui);
                java.lang.reflect.Method m = ReflectionHelper.findMethod(GuiContainerCreative.class, "setCurrentCreativeTab", "func_147050_b", CreativeTabs.class);
                m.invoke(gui, ru.death.chaosshard.ChaosTab.TAB);
                wait = 15;
                step++;
                return;
            }
            case 41:
                shot = "20_creative";
                wait = 5;
                step++;
                return;
            case 42: {
                if (mc.currentScreen instanceof GuiContainerCreative) {
                    ReflectionHelper.findField(GuiContainerCreative.class, "currentScroll", "field_147067_x").setFloat(mc.currentScreen, 1.0F);
                    ((GuiContainerCreative.ContainerCreative) ((GuiContainerCreative) mc.currentScreen).inventorySlots).scrollTo(1.0F);
                }
                wait = 10;
                step++;
                return;
            }
            case 43:
                shot = "21_creative_end";
                wait = 5;
                step++;
                return;
            case 44:
                mc.displayGuiScreen(null);
                finish();
                step++;
                return;
            default:
        }
    }

    private static String name(GuiScreen s) {
        return s == null ? "нет" : s.getClass().getSimpleName();
    }

    private void magic(int tab) throws Exception {
        ReflectionHelper.findField(ru.death.chaosshard.client.magic.GuiGrimoireMagic.class, "tab").setInt(null, tab);
        ReflectionHelper.findField(ru.death.chaosshard.client.magic.GuiGrimoireMagic.class, "ritScroll").setInt(null, 0);
        mc.displayGuiScreen(new ru.death.chaosshard.client.magic.GuiGrimoireMagic(new ItemStack(ModItems.GRIMOIRE)));
        wait = 15;
    }

    private interface Job {
        void run(IntegratedServer s, WorldServer w, EntityPlayerMP p) throws Exception;
    }

    private void onServer(Job job) {
        IntegratedServer s = mc.getIntegratedServer();
        java.util.UUID id = mc.player.getUniqueID();
        s.addScheduledTask(() -> {
            try {
                EntityPlayerMP p = s.getPlayerList().getPlayerByUUID(id);
                job.run(s, p.getServerWorld(), p);
            } catch (Throwable t) {
                ChaosShard.LOG.error("[clienttest] на сервере", t);
                mc.addScheduledTask(() -> check(false, "на сервере: " + t));
            }
        });
    }

    private void onServer(Runnable r) {
        onServer((s, w, p) -> r.run());
    }

    private void open(int id, BlockPos at) {
        IntegratedServer s = mc.getIntegratedServer();
        EntityPlayerMP p = s.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if (id == ru.death.chaosshard.heart.HeartGuiHandler.ANCHOR && p.world.getTileEntity(at) instanceof ru.death.chaosshard.anchor.TileAnchor) {
            ((ru.death.chaosshard.anchor.TileAnchor) p.world.getTileEntity(at)).syncRec();
        }
        p.openGui(ChaosShard.INSTANCE, id, p.world, at.getX(), at.getY(), at.getZ());
    }

    /** Все блоки мода рядами перед игроком, существа — позади. */
    private void buildShowcase(IntegratedServer s, WorldServer w, EntityPlayerMP p) {
        ru.death.chaosshard.thread.ThreadManager.setPoints(p, 100000);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.GRIMOIRE));
        w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        w.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        w.setWorldTime(6000);
        origin = new BlockPos(p.posX, w.getHeight(p.getPosition()).getY(), p.posZ);
        int i = 0, placed = 0;
        List<String> bad = new ArrayList<>();
        for (Block b : ForgeRegistries.BLOCKS) {
            if (b.getRegistryName() == null || !Tags.MOD_ID.equals(b.getRegistryName().getNamespace())) {
                continue;
            }
            BlockPos at = origin.add(-12 + (i % 13) * 2, i / 13 * 2, 6 + (i / 13) * 2);
            i++;
            try {
                w.setBlockState(at, b.getDefaultState(), 2);
                if (b.hasTileEntity(b.getDefaultState()) && w.getTileEntity(at) == null) {
                    bad.add(b.getRegistryName() + " (нет плитки)");
                }
                placed++;
                if (b == ModBlocks.ISLAND_HEART) {
                    heart = at;
                } else if (b == ModBlocks.CHAOS_ANCHOR) {
                    anchor = at;
                    if (w.getTileEntity(at) instanceof ru.death.chaosshard.anchor.TileAnchor) {
                        ((ru.death.chaosshard.anchor.TileAnchor) w.getTileEntity(at)).setOwner(p);
                    }
                }
            } catch (Throwable t) {
                bad.add(b.getRegistryName() + ": " + t);
            }
        }
        if (heart == null) {
            heart = origin.add(0, 0, -30);
            w.setBlockState(heart, ModBlocks.ISLAND_HEART.getDefaultState(), 2);
        }
        if (anchor == null) {
            anchor = origin.add(4, 0, -30);
            w.setBlockState(anchor, ModBlocks.CHAOS_ANCHOR.getDefaultState(), 2);
        }
        final int n = placed;
        final List<String> b2 = bad;
        mc.addScheduledTask(() -> check(b2.isEmpty(), "блоки мода поставлены: " + n + (b2.isEmpty() ? "" : ", сбои: " + b2)));
        p.connection.setPlayerLocation(origin.getX() + 0.5, origin.getY() + 3, origin.getZ() - 6.5, 0F, 15F);
        p.capabilities.isFlying = true;
        p.sendPlayerAbilities();
        // существа — с другой стороны
        int k = 0;
        List<String> ents = new ArrayList<>();
        for (EntityEntry ee : ForgeRegistries.ENTITIES) {
            ResourceLocation id = ee.getRegistryName();
            if (id == null || !Tags.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            try {
                Entity en = EntityList.createEntityByIDFromName(id, w);
                if (!(en instanceof EntityLiving)) {
                    continue;
                }
                EntityLiving l = (EntityLiving) en;
                l.setLocationAndAngles(origin.getX() - 14 + k * 3.5, origin.getY(), origin.getZ() - 16, 0F, 0F);
                l.rotationYawHead = l.renderYawOffset = 0F;
                l.setNoAI(true);
                l.enablePersistence();
                w.spawnEntity(l);
                shown.add(l);
                ents.add(id.getPath());
                k++;
            } catch (Throwable t) {
                final String msg = id + ": " + t;
                mc.addScheduledTask(() -> check(false, "существо " + msg));
            }
        }
        mc.addScheduledTask(() -> line("  существа на снимке: " + ents));
    }

    private void turnBack(IntegratedServer s, WorldServer w, EntityPlayerMP p) {
        p.connection.setPlayerLocation(origin.getX() + 0.5, origin.getY() + 2, origin.getZ() - 8.5, 180F, 10F);
    }

    private void clearEntities() {
        for (Entity e : shown) {
            e.setDead();
        }
        shown.clear();
    }

    /** Модели, отрисовщики, названия — без мира. */
    private void assets() {
        IBakedModel missing = mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel();
        List<String> noItemModel = new ArrayList<>(), noName = new ArrayList<>();
        int items = 0;
        for (Item it : ForgeRegistries.ITEMS) {
            if (it.getRegistryName() == null || !Tags.MOD_ID.equals(it.getRegistryName().getNamespace())) {
                continue;
            }
            NonNullList<ItemStack> subs = NonNullList.create();
            if (it.getCreativeTab() != null) {
                it.getSubItems(it.getCreativeTab(), subs);
            }
            if (subs.isEmpty()) {
                subs.add(new ItemStack(it));
            }
            for (ItemStack st : subs) {
                items++;
                IBakedModel m = mc.getRenderItem().getItemModelWithOverrides(st, null, null);
                if (m == missing) {
                    noItemModel.add(it.getRegistryName() + "@" + st.getMetadata());
                }
                String n = st.getDisplayName();
                if (n.startsWith("item.") || n.startsWith("tile.") || n.contains(Tags.MOD_ID + ".")) {
                    noName.add(it.getRegistryName() + "@" + st.getMetadata() + " = " + n);
                }
            }
        }
        check(noItemModel.isEmpty(), "модели предметов: " + items + (noItemModel.isEmpty() ? "" : ", без модели: " + noItemModel));
        check(noName.isEmpty(), "названия предметов" + (noName.isEmpty() ? "" : ", без перевода: " + noName));
        List<String> noBlockModel = new ArrayList<>();
        int states = 0;
        for (Block b : ForgeRegistries.BLOCKS) {
            if (b.getRegistryName() == null || !Tags.MOD_ID.equals(b.getRegistryName().getNamespace())) {
                continue;
            }
            for (IBlockState st : b.getBlockState().getValidStates()) {
                if (st.getRenderType() != EnumBlockRenderType.MODEL) {
                    continue;
                }
                states++;
                if (mc.getBlockRendererDispatcher().getModelForState(st) == missing) {
                    noBlockModel.add(st.toString());
                }
            }
        }
        check(noBlockModel.isEmpty(), "модели блоков: " + states + " состояний" + (noBlockModel.isEmpty() ? "" : ", без модели: "
                + (noBlockModel.size() > 30 ? noBlockModel.subList(0, 30) + " … всего " + noBlockModel.size() : noBlockModel)));
        List<String> noRender = new ArrayList<>(), noEntName = new ArrayList<>();
        for (EntityEntry ee : ForgeRegistries.ENTITIES) {
            if (ee.getRegistryName() == null || !Tags.MOD_ID.equals(ee.getRegistryName().getNamespace())) {
                continue;
            }
            if (mc.getRenderManager().getEntityClassRenderObject(ee.getEntityClass()) == null) {
                noRender.add(ee.getRegistryName().toString());
            }
            if (EntityLiving.class.isAssignableFrom(ee.getEntityClass()) && !I18n.hasKey("entity." + ee.getName() + ".name")) {
                noEntName.add(ee.getName());
            }
        }
        check(noRender.isEmpty(), "отрисовщики существ" + (noRender.isEmpty() ? "" : ", нет у: " + noRender));
        check(noEntName.isEmpty(), "названия существ" + (noEntName.isEmpty() ? "" : ", нет у: " + noEntName));
    }

    private void finish() {
        line("");
        line("Итого: пройдено " + pass + ", провалено " + fail);
        try (Writer wr = new OutputStreamWriter(new FileOutputStream(new File(mc.gameDir, "chaosshard-clienttest.txt")), StandardCharsets.UTF_8)) {
            for (String s : out) {
                wr.write(s);
                wr.write('\n');
            }
        } catch (Exception ex) {
            ChaosShard.LOG.error("[clienttest] отчёт", ex);
        }
        ChaosShard.LOG.info("CLIENTTEST DONE pass={} fail={}", pass, fail);
        if (mc.world != null) {
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }
        mc.shutdown();
    }
}
