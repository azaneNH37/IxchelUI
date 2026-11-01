package com.azane.ixchel.sample.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Set;

/**
 * The sample's client settings ({@code ixchel_sample-client.toml}), edited in game by the config
 * panel through {@link ConfigEntries}. Four categories cover every value kind the panel edits:
 * toggles, bounded numbers, enums, a free string, an item id list and RGB colors.
 */
public final class SampleConfig {
    /** Color theme of the sample screens and HUDs. */
    enum Theme {
        DARK, LIGHT, VANILLA, HIGH_CONTRAST
    }

    /** Screen corner of the pickup toasts. */
    enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** Paths of the int values that hold an RGB color ({@code 0xRRGGBB}) rather than a number. */
    static final Set<List<String>> COLORS = Set.of(List.of("hud", "accentColor"), List.of("compass", "markerColor"));

    static final ForgeConfigSpec.ConfigValue<String> INDEX_TITLE;
    static final ForgeConfigSpec.EnumValue<Theme> THEME;

    static final ForgeConfigSpec.BooleanValue SHOW_ENV_HUD;
    static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    static final ForgeConfigSpec.IntValue ACCENT_COLOR;

    static final ForgeConfigSpec.BooleanValue SHOW_COMPASS;
    static final ForgeConfigSpec.IntValue COMPASS_RANGE;
    static final ForgeConfigSpec.IntValue MARKER_COLOR;
    static final ForgeConfigSpec.IntValue MAX_WAYPOINTS;

    static final ForgeConfigSpec.BooleanValue PICKUP_TOASTS;
    static final ForgeConfigSpec.DoubleValue TOAST_SECONDS;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_ITEMS;
    static final ForgeConfigSpec.EnumValue<Corner> TOAST_CORNER;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Sample index and shared look").push("general");
        INDEX_TITLE = b.comment("Title shown above the sample index (H), at most 48 characters.")
                .define("indexTitle", "Ixchel UI samples", o -> o instanceof String s && !s.isBlank() && s.length() <= 48);
        THEME = b.comment("Color theme of the sample screens and HUDs.")
                .defineEnum("theme", Theme.DARK);
        b.pop();

        b.comment("Environment HUD").push("hud");
        SHOW_ENV_HUD = b.comment("Show the environment HUD in the top-left corner.")
                .define("showEnvHud", true);
        HUD_SCALE = b.comment("Scale of the environment HUD relative to the GUI scale.")
                .defineInRange("hudScale", 1.0, 0.5, 3.0);
        ACCENT_COLOR = b.comment("Accent color of HUD bars and highlights, as 0xRRGGBB.")
                .defineInRange("accentColor", 0x55C1FF, 0, 0xFFFFFF);
        b.pop();

        b.comment("Waypoint compass on the environment HUD").push("compass");
        SHOW_COMPASS = b.comment("Show the waypoint compass under the environment HUD.")
                .define("showCompass", true);
        COMPASS_RANGE = b.comment("Distance in blocks within which waypoints appear on the compass.")
                .defineInRange("compassRange", 256, 16, 4096);
        MARKER_COLOR = b.comment("Color of waypoint markers on the compass, as 0xRRGGBB.")
                .defineInRange("markerColor", 0xFFD24A, 0, 0xFFFFFF);
        MAX_WAYPOINTS = b.comment("Maximum number of waypoints kept per world; read when joining a world.")
                .worldRestart()
                .defineInRange("maxWaypoints", 64, 1, 256);
        b.pop();

        b.comment("Loot pickup sample").push("loot");
        PICKUP_TOASTS = b.comment("Show a toast for every item picked up from a loot drop.")
                .define("pickupToasts", true);
        TOAST_SECONDS = b.comment("How long a pickup toast stays on screen, in seconds.")
                .defineInRange("toastSeconds", 3.0, 0.5, 10.0);
        BLOCKED_ITEMS = b.comment("Item ids never offered in loot drops, e.g. minecraft:rotten_flesh.")
                .defineListAllowEmpty("blockedItems", List.of("minecraft:rotten_flesh", "minecraft:poisonous_potato"),
                        o -> o instanceof String s && ResourceLocation.isValidResourceLocation(s));
        TOAST_CORNER = b.comment("Screen corner the pickup toasts stack in.")
                .defineEnum("toastCorner", Corner.TOP_RIGHT);
        b.pop();

        SPEC = b.build();
    }

    private SampleConfig() {
    }

    /** Registers {@link #SPEC} as the mod's client config; call from the mod constructor. */
    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
