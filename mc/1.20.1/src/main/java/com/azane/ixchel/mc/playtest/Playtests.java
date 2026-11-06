package com.azane.ixchel.mc.playtest;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.playtest.Pump;
import com.azane.ixchel.core.playtest.PlaytestRun;
import com.mojang.logging.LogUtils;
import com.mojang.text2speech.Narrator;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
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
 * failure); and quits, with exit status 1 when an assertion failed. The run starts in English
 * ({@code en_us}), in memory, whatever language the game directory's options keep: playtests assert
 * English text unless they switch the language themselves.
 * Assertions are logged as {@code [ixchel-playtest] <playtest> <what>: PASS|FAIL}.
 *
 * <p>With {@code -Dixchel.record.<modid>=<name>[,<name>…]|all} instead, the same playtests run as a
 * recording run: each {@linkplain Playtest#take take} becomes {@code playtests/<modid>/recordings/<take>.mp4},
 * paced as a person acts and on game time stepped one video frame per drawn frame, so a recording
 * does not depend on how fast the machine draws; failed assertions fail it the same way. The window
 * is set to {@code ixchel.recording.window} ({@code 1280x720}) at GUI scale
 * {@code ixchel.recording.guiScale} (2); {@code ixchel.recording.fps} (60), {@code .scale} (1, the
 * video's size against the window's) and {@code .ffmpeg} (the one on PATH) set the video. The two
 * properties exclude each other.
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
@HostApi
public final class Playtests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD = "New World";
    /** How long a recording run lets the loaded world settle before its first playtest, in ticks. */
    private static final int SETTLE_TICKS = 40;

    private final String modid;
    private final Path dir;
    private final PlaytestRun<Playtest> run;
    /** The recorder of a recording run, else {@code null}. */
    private final Recorder recorder;
    private boolean resetPlayer;
    private boolean worldOpened;
    private boolean done;
    /** Ticks a recording run has waited in the loaded world before its first playtest. */
    private int settling;

    private Playtests(String modid, String selection, Recorder.Settings recording) {
        this.modid = modid;
        this.dir = Minecraft.getInstance().gameDirectory.toPath().resolve("playtests").resolve(modid);
        this.recorder = recording == null ? null : new Recorder(recording, dir.resolve("recordings"));
        this.run = new PlaytestRun<>(selection, name -> new Playtest(name, dir, recorder), this::prepare);
    }

    /**
     * Runs the playtests {@code playtests} registers when {@code ixchel.playtest.<modid>} or
     * {@code ixchel.record.<modid>} selects any; otherwise does nothing. Call once, from the client
     * side of the host mod's constructor.
     *
     * @throws IllegalStateException when both are set
     */
    public static void install(String modid, Pump pump, Consumer<Playtests> playtests) {
        String tested = System.getProperty("ixchel.playtest." + modid);
        String recorded = System.getProperty("ixchel.record." + modid);
        if (tested == null && recorded == null) {
            return;
        }
        if (tested != null && recorded != null) {
            throw new IllegalStateException("ixchel.playtest." + modid + " and ixchel.record." + modid + " exclude each other");
        }
        String selection = tested != null ? tested : recorded;
        Playtests created = new Playtests(modid, selection.isEmpty() ? "all" : selection,
                recorded != null ? Recorder.Settings.fromSystemProperties() : null);
        playtests.accept(created);
        Minecraft mc = Minecraft.getInstance();
        // In a fresh game directory the first screen would ask about accessibility, not the title screen.
        mc.options.onboardAccessibility = false;
        // Not saved; off before the game makes its narrator, see silenceNarrator.
        mc.options.narrator().set(NarratorStatus.OFF);
        // Not saved: the developer's own language stays in options.txt.
        mc.options.languageCode = "en_us";
        if (mc.getLanguageManager() != null) {
            mc.getLanguageManager().setSelected("en_us");
        }
        pump.start(created::tick);
        if (created.recorder != null) {
            ScreenInput.INSTANCE.recorder = created.recorder;
            pump.frames(created::drawn);
        }
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

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (done) {
            return;
        }
        if (mc.player == null) {
            if (!worldOpened && mc.screen instanceof TitleScreen title) {
                worldOpened = true;
                silenceNarrator(mc);
                if (recorder != null) {
                    recorder.prepareWindow();
                }
                openWorld(mc, title);
            }
            return;
        }
        if (recorder != null && settling < SETTLE_TICKS) {
            // Long enough for the client to hear the still sky, which the server sends once a second.
            if (settling++ == 0) {
                recorder.stillSky(mc.getSingleplayerServer());
            }
            return;
        }
        if (run.tick()) {
            done = true;
            finish(mc);
        }
    }

    /**
     * Replaces the narrator with the speech library's silent one: an unattended run never speaks, and
     * the system speech library cannot stall it, nor its shutdown. Its status was turned off in memory
     * before the game made it, so the game did not ask, in a blocking dialog, whether to go on without
     * a speech library that failed to start.
     */
    private static void silenceNarrator(Minecraft mc) {
        try {
            GameClock.field(GameNarrator.class, "narrator", "f_93313_").set(mc.getNarrator(), Narrator.EMPTY);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** In a recording run, after each drawn frame: the frame into the open take, the frame steps, the server in step. */
    private void drawn() {
        recorder.frame();
        if (!done) {
            run.frame();
        }
        recorder.settle();
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
        if (recorder != null) {
            recorder.close();
        }
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
