package ru.death.chaosshard.trial;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import ru.death.chaosshard.init.ModBlocks;

/** Облик обломка: чем был погибший мир. Свои стены, пол, свет и обитатели. */
public enum TrialTheme {
    RUINS, ICE, FORGE, OVERGROWN;

    public String key() {
        return "chaosshard.trial.theme." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public IBlockState wall() {
        switch (this) {
            case ICE: return Blocks.PACKED_ICE.getDefaultState();
            case FORGE: return Blocks.NETHER_BRICK.getDefaultState();
            case OVERGROWN: return Blocks.STONEBRICK.getStateFromMeta(1);
            default: return ModBlocks.DESECRATED_STONE.getDefaultState();
        }
    }

    public IBlockState wall2() {
        switch (this) {
            case ICE: return Blocks.SNOW.getDefaultState();
            case FORGE: return Blocks.RED_NETHER_BRICK.getDefaultState();
            case OVERGROWN: return Blocks.MOSSY_COBBLESTONE.getDefaultState();
            default: return ModBlocks.CHAOS_STONE.getDefaultState();
        }
    }

    public IBlockState floor(boolean alt) {
        switch (this) {
            case ICE: return alt ? Blocks.SNOW.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
            case FORGE: return alt ? Blocks.NETHER_BRICK.getDefaultState() : Blocks.RED_NETHER_BRICK.getDefaultState();
            case OVERGROWN: return alt ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.STONEBRICK.getStateFromMeta(1);
            default: return Blocks.STONEBRICK.getStateFromMeta(alt ? 2 : 0);
        }
    }

    /** Плитка пола, на которую уже наступили (Мир памяти). */
    public IBlockState trodden() {
        switch (this) {
            case ICE: return Blocks.ICE.getDefaultState();
            case FORGE: return Blocks.MAGMA.getDefaultState();
            case OVERGROWN: return Blocks.DIRT.getStateFromMeta(1);
            default: return Blocks.STONEBRICK.getStateFromMeta(2);
        }
    }

    public IBlockState light() {
        switch (this) {
            case FORGE: case OVERGROWN: return Blocks.GLOWSTONE.getDefaultState();
            default: return Blocks.SEA_LANTERN.getDefaultState();
        }
    }

    public IBlockState pit() {
        switch (this) {
            case ICE: return Blocks.SNOW.getDefaultState();
            case FORGE: return Blocks.MAGMA.getDefaultState();
            case OVERGROWN: return Blocks.DIRT.getStateFromMeta(2);
            default: return ModBlocks.CHAOS_CRUST.getDefaultState();
        }
    }

    private static final String[][][] MOBS = {
            {{"minecraft:zombie", "minecraft:skeleton", "minecraft:spider"},
                    {"minecraft:husk", "minecraft:stray", "minecraft:cave_spider", "minecraft:zombie"},
                    {"minecraft:wither_skeleton", "minecraft:vindication_illager", "minecraft:husk", "minecraft:stray"}},
            {{"minecraft:stray", "minecraft:zombie", "minecraft:spider"},
                    {"minecraft:stray", "minecraft:stray", "minecraft:husk", "minecraft:cave_spider"},
                    {"minecraft:stray", "minecraft:vindication_illager", "minecraft:wither_skeleton"}},
            {{"minecraft:magma_cube", "minecraft:zombie_pigman", "minecraft:skeleton"},
                    {"minecraft:magma_cube", "minecraft:wither_skeleton", "minecraft:blaze"},
                    {"minecraft:wither_skeleton", "minecraft:blaze", "minecraft:blaze", "minecraft:magma_cube"}},
            {{"minecraft:spider", "minecraft:zombie", "minecraft:cave_spider"},
                    {"minecraft:cave_spider", "minecraft:witch", "minecraft:zombie", "minecraft:spider"},
                    {"minecraft:witch", "minecraft:vindication_illager", "minecraft:cave_spider", "minecraft:spider"}}};

    public String[] mobs(int tier) {
        return MOBS[ordinal()][Math.max(0, Math.min(3, tier) - 1)];
    }

    public String guard(int tier) {
        switch (this) {
            case ICE: return "minecraft:stray";
            case FORGE: return "minecraft:wither_skeleton";
            case OVERGROWN: return "minecraft:vindication_illager";
            default: return tier >= 2 ? "minecraft:wither_skeleton" : "minecraft:vindication_illager";
        }
    }
}
