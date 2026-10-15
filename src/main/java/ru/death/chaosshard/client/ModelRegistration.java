package ru.death.chaosshard.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.init.ModBlocks;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class ModelRegistration {

    @SubscribeEvent
    public static void onModels(ModelRegistryEvent event) {
        for (Block block : ModBlocks.ALL) {
            Item item = Item.getItemFromBlock(block);
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
        }
        for (Item item : ru.death.chaosshard.init.ModItems.ALL) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
        for (ru.death.chaosshard.ritual.Ritual r : ru.death.chaosshard.ritual.Ritual.SPHERES) {
            ModelLoader.setCustomModelResourceLocation(ru.death.chaosshard.init.ModItems.RITUAL_SPHERE, r.sphereIndex(),
                    new ModelResourceLocation(Tags.MOD_ID + ":ritual_sphere_" + r.name().toLowerCase(), "inventory"));
        }
        for (ru.death.chaosshard.challenge.ChallengeFaction f : ru.death.chaosshard.challenge.ChallengeFaction.values()) {
            ModelLoader.setCustomModelResourceLocation(ru.death.chaosshard.init.ModItems.CHALLENGE_SPHERE, f.ordinal(),
                    new ModelResourceLocation(Tags.MOD_ID + ":challenge_sphere_" + f.name().toLowerCase(), "inventory"));
        }
        for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) {
            ModelLoader.setCustomModelResourceLocation(ru.death.chaosshard.init.ModItems.HERO_ECHO, h.ordinal(),
                    new ModelResourceLocation(Tags.MOD_ID + ":hero_echo_" + h.id(), "inventory"));
        }
        // объёмные модели трофеев на подставке (models/item/trophy/<герой>.json); в руке и в инвентаре — прежние
        for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) {
            net.minecraft.item.ItemStack s = ru.death.chaosshard.hero.Trophies.of(h);
            if (!s.isEmpty()) {
                ModelLoader.registerItemVariants(s.getItem(), s.getItem().getRegistryName(),
                        new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "trophy/" + h.id()));
            }
        }
        // BodyModels тянет класс импланта (он наследует интерфейс Cyberware) — трогать только при Cyberware
        if (ru.death.chaosshard.compat.body.Body.cyber()) {
            ru.death.chaosshard.compat.body.BodyModels.register();
        }
    }

    private ModelRegistration() {
    }
}
