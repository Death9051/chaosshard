package ru.death.chaosshard.compat.body;

import flaxbeard.cyberware.api.CyberwareAPI;
import flaxbeard.cyberware.api.CyberwareSurgeryEvent;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.UUID;

/**
 * Тело Осколка поверх Robotic Parts: импланты Хаоса и стабильность конструкта (толерантность), растущая с Нитью.
 * Класс трогается только если мод cyberware загружен (см. Body).
 */
public final class BodyCompat {

    private static final UUID TOLERANCE_ID = UUID.fromString("6c1f2f3e-4d2a-4c6b-9d1e-0a7c5b3e9f11");
    public static ItemChaosImplant IMPLANT;

    static void registerItems() {
        IMPLANT = new ItemChaosImplant();
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register(new BodyCompat.Events());
    }

    /** Установленный экземпляр импланта (с его настройками) или пустой стак. */
    static ItemStack installed(EntityPlayer player, int meta) {
        if (IMPLANT == null || player == null || !CyberwareAPI.hasCapability(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack s = CyberwareAPI.getCyberware(player, new ItemStack(IMPLANT, 1, meta));
        return s == null ? ItemStack.EMPTY : s;
    }

    static boolean has(EntityPlayer player, int meta) {
        if (IMPLANT == null || player == null || !CyberwareAPI.hasCapability(player)) {
            return false;
        }
        return CyberwareAPI.isCyberwareInstalled(player, new ItemStack(IMPLANT, 1, meta));
    }

    /** Стабильность конструкта: + tolerancePerLevel за каждый уровень Нити. */
    static void updateTolerance(EntityPlayerMP player) {
        IAttributeInstance inst = player.getEntityAttribute(CyberwareAPI.TOLERANCE_ATTR);
        if (inst == null) {
            return;
        }
        double want = ChaosConfig.body.tolerancePerLevel * ThreadBonus.level(player);
        AttributeModifier cur = inst.getModifier(TOLERANCE_ID);
        if (cur != null && cur.getAmount() == want) {
            return;
        }
        if (cur != null) {
            inst.removeModifier(TOLERANCE_ID);
        }
        if (want > 0) {
            inst.applyModifier(new AttributeModifier(TOLERANCE_ID, "chaosshard thread stability", want, 0).setSaved(false));
        }
    }

    private static final UUID SCALES_ARMOR = UUID.fromString("0b6f2a1c-7e3d-4a55-8c21-5d4e3f2a1b09");
    private static final UUID SCALES_TOUGH = UUID.fromString("0b6f2a1c-7e3d-4a55-8c21-5d4e3f2a1b0a");
    private static final UUID BONES_HP = UUID.fromString("0b6f2a1c-7e3d-4a55-8c21-5d4e3f2a1b07");
    private static final UUID BONES_KB = UUID.fromString("0b6f2a1c-7e3d-4a55-8c21-5d4e3f2a1b08");

    /** Раз в секунду: постоянные эффекты имплантов Хаоса. */
    static void tickImplants(EntityPlayerMP player) {
        // Панцирь Скверны: на осквернённой земле — регенерация и сопротивление
        if (has(player, ItemChaosImplant.CARAPACE)
                && ru.death.chaosshard.anchor.TileAnchor.energyMultiplier(player.world, player.getPosition()) > 1.0) {
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 0, true, false));
            player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 0, true, false));
        }
        // Око Хаоса: включается и выключается в меню имплантов Cyberware. Открыто — ночное зрение и подсветка тварей.
        ItemStack eye = installed(player, ItemChaosImplant.EYE);
        boolean eyeOpen = !eye.isEmpty() && flaxbeard.cyberware.api.item.EnableDisableHelper.isEnabled(eye);
        PotionEffect nv = player.getActivePotionEffect(MobEffects.NIGHT_VISION);
        if (eyeOpen) {
            player.getEntityData().setBoolean("chaosshard_eye_nv", true);
            player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 400, 0, true, false));
            for (net.minecraft.entity.EntityLivingBase e : player.world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class,
                    player.getEntityBoundingBox().grow(16), e -> e instanceof net.minecraft.entity.monster.IMob)) {
                e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 40, 0, true, false));
            }
        } else if (player.getEntityData().getBoolean("chaosshard_eye_nv")) {
            // Око закрыли: снимаем своё ночное зрение (до 20 с), зелья и Звёздный взор не трогаем
            player.getEntityData().removeTag("chaosshard_eye_nv");
            if (nv != null && nv.getIsAmbient() && nv.getDuration() <= 400) {
                player.removePotionEffect(MobEffects.NIGHT_VISION);
            }
        }
        // Дыхание Пустоты: дышать под водой, яд и иссушение не держатся
        if (has(player, ItemChaosImplant.LUNGS)) {
            player.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING, 60, 0, true, false));
            player.removePotionEffect(MobEffects.POISON);
            player.removePotionEffect(MobEffects.WITHER);
        }
        // Поступь Бездны: прыжок выше
        if (has(player, ItemChaosImplant.STRIDE)) {
            player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 60, 1, true, false));
        }
        // Иссушающая звезда: иссушение не берёт, при малом здоровье — регенерация
        if (has(player, ItemChaosImplant.WITHER)) {
            player.removePotionEffect(MobEffects.WITHER);
            if (player.getHealth() < player.getMaxHealth() / 2) {
                player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 0, true, false));
            }
        }
        // Драконья чешуя: огонь не жжёт, +4 брони и +2 прочности брони
        boolean scales = has(player, ItemChaosImplant.SCALES);
        if (scales) {
            player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
        }
        modifier(player, net.minecraft.entity.SharedMonsterAttributes.ARMOR, SCALES_ARMOR, scales ? 4.0 : 0);
        modifier(player, net.minecraft.entity.SharedMonsterAttributes.ARMOR_TOUGHNESS, SCALES_TOUGH, scales ? 2.0 : 0);
        // Остов Хаоса: +2 сердца и устойчивость к отбрасыванию
        boolean bones = has(player, ItemChaosImplant.BONES);
        modifier(player, net.minecraft.entity.SharedMonsterAttributes.MAX_HEALTH, BONES_HP, bones ? 4.0 : 0);
        modifier(player, net.minecraft.entity.SharedMonsterAttributes.KNOCKBACK_RESISTANCE, BONES_KB, bones ? 0.5 : 0);
    }

    private static void modifier(EntityPlayerMP p, net.minecraft.entity.ai.attributes.IAttribute attr, UUID id, double amount) {
        IAttributeInstance inst = p.getEntityAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier cur = inst.getModifier(id);
        if (cur != null && cur.getAmount() == amount) {
            return;
        }
        if (cur != null) {
            inst.removeModifier(id);
        }
        if (amount != 0) {
            inst.applyModifier(new AttributeModifier(id, "chaosshard implant", amount, 0).setSaved(false));
        }
        if (attr == net.minecraft.entity.SharedMonsterAttributes.MAX_HEALTH && p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    public static final class Events {
        /** Когти Скверны: удар рукой иссушает цель. */
        @SubscribeEvent
        public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if (event.getSource().getImmediateSource() instanceof EntityPlayerMP
                    && "player".equals(event.getSource().getDamageType())) {
                EntityPlayerMP p = (EntityPlayerMP) event.getSource().getImmediateSource();
                if (has(p, ItemChaosImplant.CLAWS)) {
                    event.getEntityLiving().addPotionEffect(new PotionEffect(MobEffects.WITHER, 60, 0));
                }
            }
        }

        /** Иссушающая звезда: кто ударит Осколка вблизи, получит иссушение. */
        @SubscribeEvent
        public void onHurtOwner(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if (event.getEntityLiving() instanceof EntityPlayerMP && event.getSource().getImmediateSource() instanceof net.minecraft.entity.EntityLivingBase
                    && has((EntityPlayerMP) event.getEntityLiving(), ItemChaosImplant.WITHER)) {
                ((net.minecraft.entity.EntityLivingBase) event.getSource().getImmediateSource())
                        .addPotionEffect(new PotionEffect(MobEffects.WITHER, 80, 1));
            }
        }

        /** Перепонки дракона: присесть в воздухе — планировать. Работает и на клиенте, чтобы полёт был плавным. */
        @SubscribeEvent
        public void onLiving(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
            if (!(event.getEntityLiving() instanceof EntityPlayer)) {
                return;
            }
            EntityPlayer p = (EntityPlayer) event.getEntityLiving();
            if (p.onGround || !p.isSneaking() || p.motionY >= 0 || p.capabilities.isFlying || p.isInWater()
                    || !has(p, ItemChaosImplant.WINGS)) {
                return;
            }
            p.motionY = Math.max(p.motionY, -0.12);
            net.minecraft.util.math.Vec3d look = p.getLookVec();
            p.motionX += look.x * 0.02;
            p.motionZ += look.z * 0.02;
            p.fallDistance = 0;
        }

        /** Поступь Бездны: падение не ранит. */
        @SubscribeEvent
        public void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
            if (event.getEntityLiving() instanceof EntityPlayerMP && has((EntityPlayerMP) event.getEntityLiving(), ItemChaosImplant.STRIDE)) {
                event.setDistance(0);
            }
        }

        @SubscribeEvent
        public void onSurgery(CyberwareSurgeryEvent.Post event) {
            if (!(event.getEntity() instanceof EntityPlayerMP)) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) event.getEntity();
            Milestones.grant(p, Milestones.IMPLANT);
            if (has(p, ItemChaosImplant.CORE)) {
                Milestones.grant(p, Milestones.CHAOS_CORE);
            }
        }
    }

    private BodyCompat() {
    }
}
