package ru.death.chaosshard.cutscene.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.cutscene.Cutscene;

/**
 * Катсцена на клиенте: своя камера вместо глаз игрока, чёрные полосы, субтитры.
 * Ввод забирает прозрачный экран GuiCutscene (Пробел — пропустить).
 */
@SideOnly(Side.CLIENT)
public class CutsceneClient {

    static Cutscene scene;
    static Camera camera;
    static int ticks;
    /** Полосы: 0 — нет, 1 — во весь размер (плавно въезжают и уезжают). */
    static float bars;

    public static boolean active() {
        return scene != null;
    }

    public static void receive(Cutscene c) {
        Minecraft mc = Minecraft.getMinecraft();
        if (c == null) {
            finish();
            return;
        }
        if (mc.world == null || mc.player == null) {
            return;
        }
        scene = c;
        ticks = 0;
        camera = new Camera(mc.world);
        place(0);
        mc.setRenderViewEntity(camera);
        mc.displayGuiScreen(new GuiCutscene());
    }

    static void finish() {
        Minecraft mc = Minecraft.getMinecraft();
        scene = null;
        camera = null;
        if (mc.player != null) {
            mc.setRenderViewEntity(mc.player);
        }
        if (mc.currentScreen instanceof GuiCutscene) {
            mc.displayGuiScreen(null);
        }
    }

    static void place(float partial) {
        if (scene == null || camera == null) {
            return;
        }
        Vec3d[] pl = scene.at(ticks + partial);
        Vec3d p = pl[0], l = pl[1];
        double dx = l.x - p.x, dy = l.y - p.y, dz = l.z - p.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90F;
        float pitch = (float) -(MathHelper.atan2(dy, h) * 180 / Math.PI);
        camera.setPosition(p.x, p.y - camera.getEyeHeight(), p.z);
        camera.prevPosX = camera.lastTickPosX = camera.posX;
        camera.prevPosY = camera.lastTickPosY = camera.posY;
        camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
        camera.rotationYaw = camera.prevRotationYaw = yaw;
        camera.rotationPitch = camera.prevRotationPitch = pitch;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (scene == null) {
            bars = Math.max(0, bars - 0.1F);
            return;
        }
        if (mc.world == null || mc.player == null || camera == null || camera.world != mc.world) {
            finish();
            return;
        }
        ticks++;
        bars = Math.min(1, bars + 0.1F);
        if (mc.getRenderViewEntity() != camera) {
            mc.setRenderViewEntity(camera);
        }
        if (!(mc.currentScreen instanceof GuiCutscene) && (mc.currentScreen == null || !mc.currentScreen.doesGuiPauseGame())) {
            mc.displayGuiScreen(new GuiCutscene());
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && scene != null) {
            place(event.renderTickTime);
        }
    }

    /** Свой интерфейс игры в сцене не нужен. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        if (scene != null && event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onHand(RenderHandEvent event) {
        if (scene != null) {
            event.setCanceled(true);
        }
    }

    /** Камера: невидимая сущность, через которую смотрит игра. В мир не добавляется. */
    static final class Camera extends Entity {
        Camera(World world) {
            super(world);
            setSize(0.1F, 0.1F);
            noClip = true;
        }

        @Override
        public float getEyeHeight() {
            return 0.05F;
        }

        @Override
        protected void entityInit() {
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {
        }
    }
}
