package com.azane.ixchel.sample.perf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * With {@code -Pbench}, the first title screen opens the dev world, creating a creative superflat
 * one when the run directory has none, so the benches run on a fresh checkout.
 */
public final class BenchWorld {
    private static final String NAME = "New World";
    private static boolean opened;

    private BenchWorld() {
    }

    public static void init() {
        if (System.getProperty("ixchelsample.bench") == null) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            if (opened || !(e.getScreen() instanceof TitleScreen title)) {
                return;
            }
            opened = true;
            Minecraft mc = Minecraft.getInstance();
            // Not from inside the title screen's init: the world flows replace the screen.
            mc.tell(() -> {
                if (mc.getLevelSource().levelExists(NAME)) {
                    mc.createWorldOpenFlows().loadLevel(title, NAME);
                } else {
                    mc.createWorldOpenFlows().createFreshLevel(NAME,
                            new LevelSettings(NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                    new GameRules(), WorldDataConfiguration.DEFAULT),
                            new WorldOptions(WorldOptions.randomSeed(), false, false),
                            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
            });
        });
    }
}
