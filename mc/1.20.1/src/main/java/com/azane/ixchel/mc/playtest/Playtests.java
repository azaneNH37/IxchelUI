package com.azane.ixchel.mc.playtest;

import com.azane.ixchel.core.playtest.Pump;
import com.azane.ixchel.core.playtest.PlaytestRun;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A host mod's in-game playtests, run unattended in a development client when
 * {@code -Dixchel.playtest.<modid>=<name>[,<name>…]|all} is set ({@code all}: every playtest, in
 * registration order). The run turns the tutorial off and enters the dev world from the title
 * screen, creating a creative superflat {@value #WORLD} when the game directory has none; runs the
 * selected playtests one after another, each a {@link Playtest}; writes
 * {@code playtests/<modid>/report.txt} under the game directory (and a {@linkplain Playtest dump} per
 * failure); and quits, with exit status 1 when an assertion failed.
 * Assertions are logged as {@code [ixchel-playtest] <playtest> <what>: PASS|FAIL}.
 *
 * <p>The loader layer installs it: {@code IxchelForge.installPlaytests(modid, playtests -> ...)}.
 *
 * <pre>{@code
 * IxchelForge.installPlaytests("examplemod", playtests -> {
 *     playtests.resetPlayer();
 *     playtests.register("panel", s -> s.run(Panel::open).waitUntil("opens", Playtest::isOpen, Playtest.WAIT));
 * });
 * }</pre>
 */
public final class Playtests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD = "New World";

    private final String modid;
    private final Path dir;
    private final PlaytestRun<Playtest> run;
    private boolean resetPlayer;
    private boolean worldOpened;
    private boolean done;

    private Playtests(String modid, String selection) {
        this.modid = modid;
        this.dir = Minecraft.getInstance().gameDirectory.toPath().resolve("playtests").resolve(modid);
        this.run = new PlaytestRun<>(selection, name -> new Playtest(name, dir), this::prepare);
    }

    /**
     * Runs the playtests {@code playtests} registers when {@code ixchel.playtest.<modid>} selects any;
     * otherwise does nothing. Call once, from the client side of the host mod's constructor.
     */
    public static void install(String modid, Pump pump, Consumer<Playtests> playtests) {
        String selection = System.getProperty("ixchel.playtest." + modid);
        if (selection == null) {
            return;
        }
        Playtests created = new Playtests(modid, selection.isEmpty() ? "all" : selection);
        playtests.accept(created);
        pump.start(created::frame);
    }

    /** Adds a playtest; {@code body} queues its steps on the {@link Playtest} when it starts. */
    public Playtests register(String name, Consumer<Playtest> body) {
        run.register(name, body);
        return this;
    }

    /**
     * Before each playtest: no screen, first hotbar slot, and on the server an empty inventory, no
     * effects or experience, full health and food, creative mode, peaceful difficulty.
     */
    public Playtests resetPlayer() {
        resetPlayer = true;
        return this;
    }

    private void prepare(Playtest s) {
        if (!resetPlayer) {
            return;
        }
        s.run(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?>) {
                mc.player.closeContainer();
            } else {
                mc.setScreen(null);
            }
            mc.player.getInventory().selected = 0;
        });
        s.server(p -> {
            p.server.setDifficulty(Difficulty.PEACEFUL, true);
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().clearContent();
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.setExperienceLevels(0);
            p.experienceProgress = 0;
        });
    }

    private void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (done) {
            return;
        }
        if (mc.player == null) {
            if (!worldOpened && mc.screen instanceof TitleScreen title) {
                worldOpened = true;
                openWorld(mc, title);
            }
            return;
        }
        if (run.tick()) {
            done = true;
            finish(mc);
        }
    }

    private static void openWorld(Minecraft mc, TitleScreen title) {
        // The tutorial starts from this option once a level is loaded; its movement hint would sit
        // in every screenshot's top-right corner.
        mc.options.tutorialStep = TutorialSteps.NONE;
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().loadLevel(title, WORLD);
        } else {
            mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(WorldOptions.randomSeed(), false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
    }

    private void finish(Minecraft mc) {
        Path file = dir.resolve("report.txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, run.summary(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.error("[ixchel-playtest] cannot write {}", file, ex);
        }
        int failed = run.failed();
        LOG.info("[ixchel-playtest] {} done: {} playtests, {} passed, {} failed", modid, run.finished(), run.passed(),
                failed);
        if (failed > 0) {
            // The game exits with status 0 however it stops; a shutdown hook runs after it saved and
            // closed, and halting there sets the status the run task sees.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> Runtime.getRuntime().halt(1), "ixchel-playtest-status"));
        }
        mc.stop();
    }
}
