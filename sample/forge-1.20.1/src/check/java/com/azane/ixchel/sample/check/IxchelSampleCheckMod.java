package com.azane.ixchel.sample.check;

import com.azane.ixchel.sample.check.scenario.CharacterCheck;
import com.azane.ixchel.sample.check.scenario.EnvCheck;
import com.azane.ixchel.sample.check.scenario.IndexCheck;
import com.azane.ixchel.sample.check.scenario.LootCheck;
import com.azane.ixchel.sample.check.scenario.MachineCheck;
import com.azane.ixchel.sample.check.scenario.StorageCheck;
import com.azane.ixchel.sample.check.scenario.WaypointsCheck;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The sample's unattended checks, a second mod in the {@code check} source set that Gradle loads
 * only with {@code -Pcheck=<name>[,<name>…]|all}. It drives the samples through their public entry
 * classes and the packets, never through check-only hooks: {@link BenchWorld} enters the dev world,
 * {@link Checks} runs the selected checks one after another, each a {@link Script} of steps, and
 * {@link FrameTimer} holds documents to a frame budget. The checks themselves, one per sample
 * feature, are in {@code scenario}.
 */
@Mod(IxchelSampleCheckMod.MOD_ID)
public final class IxchelSampleCheckMod {
    public static final String MOD_ID = "ixchelsamplecheck";

    public IxchelSampleCheckMod() {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        BenchWorld.init();
        Checks.init();
        // Registration order is the order of `-Pcheck=all`.
        IndexCheck.register();
        EnvCheck.register();
        CharacterCheck.register();
        MachineCheck.register();
        LootCheck.register();
        WaypointsCheck.register();
        StorageCheck.register();
    }
}
