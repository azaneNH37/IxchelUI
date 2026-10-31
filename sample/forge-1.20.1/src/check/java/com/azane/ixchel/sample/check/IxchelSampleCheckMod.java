package com.azane.ixchel.sample.check;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.check.scenario.CharacterCheck;
import com.azane.ixchel.sample.check.scenario.ConfigCheck;
import com.azane.ixchel.sample.check.scenario.EnvCheck;
import com.azane.ixchel.sample.check.scenario.GuideCheck;
import com.azane.ixchel.sample.check.scenario.IndexCheck;
import com.azane.ixchel.sample.check.scenario.LootCheck;
import com.azane.ixchel.sample.check.scenario.MachineCheck;
import com.azane.ixchel.sample.check.scenario.QuestsCheck;
import com.azane.ixchel.sample.check.scenario.StorageCheck;
import com.azane.ixchel.sample.check.scenario.TooltipsCheck;
import com.azane.ixchel.sample.check.scenario.WaypointsCheck;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The sample's unattended checks, a second mod in the {@code check} source set that Gradle loads
 * only with {@code -Pcheck=<name>[,<name>…]|all}. It installs them with
 * {@link IxchelForge#installChecks} and drives the samples through their public entry classes and
 * the packets, never through check-only hooks. The checks themselves, one per sample feature, are in
 * {@code scenario}.
 */
@Mod(IxchelSampleCheckMod.MOD_ID)
public final class IxchelSampleCheckMod {
    public static final String MOD_ID = "ixchelsamplecheck";

    public IxchelSampleCheckMod() {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        IxchelForge.installChecks(IxchelSampleMod.MOD_ID, checks -> {
            checks.resetPlayer();
            // Registration order is the order of `-Pcheck=all`.
            IndexCheck.register(checks);
            EnvCheck.register(checks);
            CharacterCheck.register(checks);
            MachineCheck.register(checks);
            LootCheck.register(checks);
            WaypointsCheck.register(checks);
            StorageCheck.register(checks);
            ConfigCheck.register(checks);
            QuestsCheck.register(checks);
            GuideCheck.register(checks);
            TooltipsCheck.register(checks);
        });
    }
}
