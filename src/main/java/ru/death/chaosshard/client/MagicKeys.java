package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import ru.death.chaosshard.client.magic.GuiGrimoireMagic;
import ru.death.chaosshard.client.magic.GuiSpellWheel;
import ru.death.chaosshard.init.ModItems;

/** Клавиши магии: K — колесо заклинаний (Гримуар в руке), P — окно магии Гримуара (Гримуар в инвентаре). R, V и G заняты Cyberware, Metamorph, Lycanites и др. */
public class MagicKeys {

    public static final String CATEGORY = "key.categories.chaosshard";
    public static KeyBinding WHEEL;
    public static KeyBinding BOOK;

    public static void register() {
        WHEEL = new KeyBinding("key.chaosshard.spell_wheel", Keyboard.KEY_K, CATEGORY);
        BOOK = new KeyBinding("key.chaosshard.grimoire_magic", Keyboard.KEY_P, CATEGORY);
        ClientRegistry.registerKeyBinding(WHEEL);
        ClientRegistry.registerKeyBinding(BOOK);
    }

    public static ItemStack heldGrimoire(EntityPlayer player) {
        if (player.getHeldItemMainhand().getItem() == ModItems.GRIMOIRE) {
            return player.getHeldItemMainhand();
        }
        if (player.getHeldItemOffhand().getItem() == ModItems.GRIMOIRE) {
            return player.getHeldItemOffhand();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack anyGrimoire(EntityPlayer player) {
        ItemStack held = heldGrimoire(player);
        if (!held.isEmpty()) {
            return held;
        }
        for (ItemStack s : player.inventory.mainInventory) {
            if (s.getItem() == ModItems.GRIMOIRE) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        if (WHEEL.isPressed()) {
            ItemStack g = heldGrimoire(mc.player);
            if (!g.isEmpty()) {
                mc.displayGuiScreen(new GuiSpellWheel(g));
            } else {
                mc.player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("chaosshard.grimoire.need_hand"), true);
            }
        }
        if (BOOK.isPressed()) {
            ItemStack g = anyGrimoire(mc.player);
            if (!g.isEmpty()) {
                mc.displayGuiScreen(new GuiGrimoireMagic(g));
            }
        }
    }
}
