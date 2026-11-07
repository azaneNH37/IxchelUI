package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The sample's in-game playtests, a second mod in the {@code playtest} source set that Gradle loads
 * only with {@code -Pplaytest=<name>[,<name>…]|all}. It installs them with
 * {@link IxchelForge#installPlaytests} and drives the samples through their public entry classes and
 * the packets, never through playtest-only hooks. One playtest per sample feature, each in its own
 * {@code *Playtest} class.
 */
@Mod(IxchelSamplePlaytestMod.MOD_ID)
public final class IxchelSamplePlaytestMod {
    public static final String MOD_ID = "ixchel_sample_playtest";

    public IxchelSamplePlaytestMod() {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        IxchelForge.installPlaytests(IxchelSampleMod.MOD_ID, playtests -> {
            playtests.resetPlayer();
            // Registration order is the order of `-Pplaytest=all`.
            IndexPlaytest.register(playtests);
            EnvPlaytest.register(playtests);
            CharacterPlaytest.register(playtests);
            MachinePlaytest.register(playtests);
            LootPlaytest.register(playtests);
            WaypointsPlaytest.register(playtests);
            StoragePlaytest.register(playtests);
            ConfigPlaytest.register(playtests);
            ConfigPlaytest.registerSort(playtests);
            QuestsPlaytest.register(playtests);
            GuidePlaytest.register(playtests);
            TooltipsPlaytest.register(playtests);
            LanguagePlaytest.register(playtests);
            EffectsPlaytest.register(playtests);
            ThemesPlaytest.register(playtests);
            WorkbenchPlaytest.register(playtests);
            SpellPlaytest.register(playtests);
            BossPlaytest.register(playtests);
            ArcanePlaytest.register(playtests);
        });
    }
}
