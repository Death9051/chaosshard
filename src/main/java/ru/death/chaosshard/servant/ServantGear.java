package ru.death.chaosshard.servant;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.UUID;

/**
 * Вид и сила обычных слуг от уровня Нити хозяина: снаряжение по ступеням (без брони → кожа → кольчуга → железо →
 * зачарованное железо → алмаз), +15% здоровья и урона за уровень Нити, плюс улучшения ритуалом.
 * Добытчики носят шапку цвета своего ремесла и инструмент.
 */
public final class ServantGear {

    public static final String TAG_TIER = "chaosshard_tier";
    private static final UUID HP = UUID.fromString("6a1f3c0e-2b5d-4c39-9a51-7e0c2f9b1a01");
    private static final UUID DMG = UUID.fromString("6a1f3c0e-2b5d-4c39-9a51-7e0c2f9b1a02");
    private static final UUID ARMOR = UUID.fromString("6a1f3c0e-2b5d-4c39-9a51-7e0c2f9b1a03");
    private static final UUID SPEED = UUID.fromString("6a1f3c0e-2b5d-4c39-9a51-7e0c2f9b1a04");
    private static final UUID KNOCK = UUID.fromString("6a1f3c0e-2b5d-4c39-9a51-7e0c2f9b1a05");

    public static int tier(EntityLiving m) {
        return m.getEntityData().getInteger(TAG_TIER);
    }

    /** Обновить вид, силу и подпись. owner может быть null (не в сети) — тогда ступень прежняя. */
    public static void refresh(EntityLiving m, EntityPlayer owner) {
        Servants.Kind kind = Servants.kindOf(m);
        if (kind == null || kind.unique()) {
            return;
        }
        NBTTagCompound d = m.getEntityData();
        int t = owner != null ? Math.min(5, ThreadBonus.level(owner)) : d.getInteger(TAG_TIER);
        d.setInteger(TAG_TIER, t);
        if (owner != null) {
            d.setString("chaosshard_owner_name", owner.getName());
        }
        equip(m, kind, t);

        float hp = 0.15F * t, dmg = 0.15F * t, armor = 0, speed = 0, knock = 0;
        if (ServantUpgrade.has(m, ServantUpgrade.HUSK_FLESH)) hp += 0.5F;
        if (ServantUpgrade.has(m, ServantUpgrade.HUSK_SAND)) speed += 0.2F;
        if (ServantUpgrade.has(m, ServantUpgrade.BONE_FORT)) { armor += 4; knock = 1; }
        if (ServantUpgrade.has(m, ServantUpgrade.EXEC_HEAVY)) dmg += 0.4F;
        if (ServantUpgrade.has(m, ServantUpgrade.EXEC_UNSTOP)) { speed += 0.2F; knock = 1; }
        float ratio = m.getHealth() / Math.max(1, m.getMaxHealth());
        set(m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH), HP, "chaosshard servant hp", hp, 1);
        set(m.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE), DMG, "chaosshard servant dmg", dmg, 1);
        set(m.getEntityAttribute(SharedMonsterAttributes.ARMOR), ARMOR, "chaosshard servant armor", armor, 0);
        set(m.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED), SPEED, "chaosshard servant speed", speed, 1);
        set(m.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE), KNOCK, "chaosshard servant knock", knock, 0);
        m.setHealth(Math.max(1, m.getMaxHealth() * ratio));

        int stars = ServantUpgrade.count(m);
        String name = d.getString("chaosshard_owner_name");
        StringBuilder sb = new StringBuilder(t >= 4 ? "§d" : "§5").append(kind.name);
        if (stars > 0) {
            sb.append(" §e");
            for (int i = 0; i < stars; i++) {
                sb.append('★');
            }
        }
        sb.append(" §7(").append(name).append(")");
        m.setCustomNameTag(sb.toString());
    }

    private static void set(IAttributeInstance a, UUID id, String name, double value, int op) {
        if (a == null) {
            return;
        }
        if (a.getModifier(id) != null) {
            a.removeModifier(id);
        }
        if (value != 0) {
            a.applyModifier(new AttributeModifier(id, name, value, op));
        }
    }

    // ------------------------------------------------------------------ снаряжение

    private static void equip(EntityLiving m, Servants.Kind kind, int t) {
        Item[][] sets = {
                {null, null, null, null},
                {null, Items.LEATHER_CHESTPLATE, null, Items.LEATHER_BOOTS},
                {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
                {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
                {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
                {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}};
        EntityEquipmentSlot[] slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
        boolean glint = t >= 4;
        for (int i = 0; i < 4; i++) {
            Item it = sets[t][i];
            ItemStack s = it == null ? ItemStack.EMPTY : new ItemStack(it);
            if (glint && !s.isEmpty()) {
                s.addEnchantment(Enchantments.UNBREAKING, 1);
            }
            m.setItemStackToSlot(slots[i], s);
            m.setDropChance(slots[i], 0F);
        }
        // добытчик: шапка цвета ремесла
        if (kind.gatherer()) {
            ItemStack cap = new ItemStack(Items.LEATHER_HELMET);
            NBTTagCompound disp = new NBTTagCompound();
            disp.setInteger("color", kind == Servants.Kind.REAPER ? 0xE0C040 : kind == Servants.Kind.LUMBERJACK ? 0x3E8A3A : 0x606070);
            cap.setTagInfo("display", disp);
            if (glint) {
                cap.addEnchantment(Enchantments.UNBREAKING, 1);
            }
            m.setItemStackToSlot(EntityEquipmentSlot.HEAD, cap);
        }
        m.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, weapon(kind, t, glint));
        m.setDropChance(EntityEquipmentSlot.MAINHAND, 0F);
    }

    private static ItemStack weapon(Servants.Kind kind, int t, boolean glint) {
        Item it;
        switch (kind) {
            case HUSK:
                it = t == 0 ? null : t <= 2 ? Items.STONE_SWORD : t <= 4 ? Items.IRON_SWORD : Items.DIAMOND_SWORD;
                break;
            case BONE:
                it = t <= 1 ? Items.STONE_SWORD : t <= 3 ? Items.IRON_SWORD : Items.DIAMOND_SWORD;
                break;
            case EXECUTIONER:
                it = t <= 3 ? Items.IRON_AXE : Items.DIAMOND_AXE;
                break;
            case REAPER:
                it = t <= 1 ? Items.STONE_HOE : t <= 3 ? Items.IRON_HOE : Items.DIAMOND_HOE;
                break;
            case LUMBERJACK:
                it = t <= 1 ? Items.STONE_AXE : t <= 3 ? Items.IRON_AXE : Items.DIAMOND_AXE;
                break;
            default:
                it = t <= 1 ? Items.STONE_PICKAXE : t <= 3 ? Items.IRON_PICKAXE : Items.DIAMOND_PICKAXE;
                break;
        }
        ItemStack s = it == null ? ItemStack.EMPTY : new ItemStack(it);
        if (glint && !s.isEmpty()) {
            s.addEnchantment(Enchantments.UNBREAKING, 1);
        }
        return s;
    }

    private ServantGear() {
    }
}
