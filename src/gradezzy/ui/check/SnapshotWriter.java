/*
 * File: SnapshotWriter.java
 *
 * Purpose: Saves a PNG picture of every screen, with demo data, in both
 * themes and at four window sizes, so the layout can be reviewed without
 * clicking through the application.
 *
 * Where it fits: Run by "sh run.sh --snapshot [folder]". The sizes are a
 * 1200 by 800 window at 100, 125, 150 and 200 percent display scaling,
 * in logical pixels. Pictures go to workspace/ui-snapshots unless
 * another folder is given; git ignores workspace/.
 *
 * With --fullpage, each screen that scrolls is also saved as tall as
 * its whole page ("-full" pictures), so content below the fold is
 * checked too. PngWriter writes the files.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;

import gradezzy.model.GradingRun;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.MainController;
import gradezzy.ui.RunFollower;
import gradezzy.ui.Screen;
import gradezzy.ui.ShellBuilder;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;

/**
 * Saves pictures of every screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class SnapshotWriter {

    // The window sizes, in logical pixels: width, then height
    private static final double[][] SIZES = {{1200, 800}, {960, 640},
        {800, 533}, {600, 400}};

    // The stages of a demo run stepped before the mid-run picture
    private static final int MID_RUN_STEPS = 24;

    // Every service of the application
    private final ServiceRegistry services;

    // The folder pictures are saved to
    private final Path folder;

    // The screens to picture; empty means every screen
    private final List<Screen> only = new ArrayList<>();

    // The number of screens that could not be pictured
    private int failures = 0;

    // The number of pictures saved
    private int written = 0;

    // Whether a whole-page picture is saved for screens that scroll
    private boolean fullPage = false;

    /**
     * Creates a snapshot writer.
     *
     * @param services every service of the application
     * @param folder the folder pictures are saved to
     */
    public SnapshotWriter(ServiceRegistry services, Path folder) {
        this.services = services;
        this.folder = folder;
    }

    /**
     * Pictures only some screens, to save time while working on them.
     *
     * @param screens the screens to picture; empty means every screen
     */
    public void setScreens(List<Screen> screens) {
        only.clear();
        only.addAll(screens);
    }

    /**
     * Also saves each scrolling screen as tall as its whole page.
     *
     * @param enabled true for whole-page pictures as well
     */
    public void setFullPage(boolean enabled) {
        fullPage = enabled;
    }

    /**
     * Saves a picture of every screen.
     *
     * @return 0 when every picture was saved, 1 otherwise
     */
    public int run() {
        try {
            Files.createDirectories(folder);
            DemoBackend backend = new DemoBackend(services);
            backend.setManualStepping(true);
            UiState state = ShellBuilder.createState(services.getConfig());
            state.animationsEnabledProperty().set(false);
            ShellBuilder builder = new ShellBuilder(services, backend,
                    state, new ThemeManager());
            builder.build(SIZES[0][0], SIZES[0][1]);
            MainController main = builder.getController();
            main.setAutoConfirm(true);
            state.setSelectedUsername(DemoData.TIMEOUT_USERNAME);
            state.getSelectedResults().add(DemoData.TIMEOUT_USERNAME);

            for (Screen screen : screens()) {
                captureSafely(builder, main, screen);
            }
            if (only.isEmpty() || only.contains(Screen.RESULTS)) {
                captureSheets(builder, main, backend, state);
            }
            if (only.isEmpty() || only.contains(Screen.PROGRESS)) {
                captureMidRun(builder, main, backend, state);
            }
        } catch (IOException exception) {
            System.out.println("FAILED: " + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            System.out.println("FAILED: " + exception);
            exception.printStackTrace(System.out);
            return 1;
        }

        System.out.println("Saved " + written + " pictures to "
                + folder.toAbsolutePath() + ".");
        if (failures > 0) {
            System.out.println(failures + " screen(s) failed. See above.");
            return 1;
        }
        return 0;
    }

    // Pictures one screen, reporting a failure without stopping.
    private void captureSafely(ShellBuilder builder, MainController main,
            Screen screen) throws IOException {
        try {
            captureBothThemes(builder, main, screen, "");
        } catch (RuntimeException exception) {
            failures++;
            System.out.println("FAILED: " + screen + ": " + exception);
            exception.printStackTrace(System.out);
        }
    }

    // Returns every screen except the main window itself.
    private List<Screen> screens() {
        List<Screen> screens = new ArrayList<>();
        for (Screen screen : Screen.values()) {
            boolean wanted = only.isEmpty() || only.contains(screen);
            if (screen != Screen.MAIN && wanted) {
                screens.add(screen);
            }
        }
        return screens;
    }

    // Pictures the three kinds of sheet over the Results screen: a
    // confirmation, a Not connected yet panel and an export preview.
    private void captureSheets(ShellBuilder builder, MainController main,
            DemoBackend backend, UiState state) throws IOException {
        main.navigate(Screen.RESULTS);
        main.setAutoConfirm(false);
        main.confirm("Cancel grading?", "Grading stops after the current "
                + "submission. The 3 completed results are kept, and you "
                + "can resume the rest later.", "Cancel Grading",
                "Keep Grading", true, () -> main.closeSheet());
        captureBothThemes(builder, main, Screen.RESULTS, "-sheet-confirm");
        main.closeSheet();

        main.showNotConnected(Capability.SCORE_ADJUSTMENT);
        captureBothThemes(builder, main, Screen.RESULTS,
                "-sheet-not-connected");
        main.closeSheet();

        GradingRun run = backend.getRun(state.getSection());
        ActionResult preview = backend.export(run, run.getSubmissions(),
                "csv");
        main.showPreview("Score sheet preview", preview.getMessage(),
                preview.getPreview());
        captureBothThemes(builder, main, Screen.RESULTS, "-sheet-preview");
        main.closeSheet();
        main.setAutoConfirm(true);
    }

    // Starts a demo run and pictures the Progress screen half way.
    private void captureMidRun(ShellBuilder builder, MainController main,
            DemoBackend backend, UiState state) throws IOException {
        ImportBatch batch = state.getImportBatch();
        for (InputKind kind : InputKind.values()) {
            for (Path path : backend.getSampleFiles(kind)) {
                batch.add(backend.inspect(kind, path));
            }
        }
        for (ImportItem item
                : batch.getItems(InputKind.SUBMISSION)) {
            if (!batch.getEffectiveState(item).isUsable()) {
                batch.replace(item, backend.inspect(InputKind.SUBMISSION,
                        backend.getSampleReplacement(item)));
            }
        }
        state.getMonitor().begin("Grading 6 submissions for CS102 G1.");
        backend.startGrading(batch, state.getSection(), state.getMonitor(),
                new RunFollower(backend, state, main, "Grading"));
        backend.stepGrading(MID_RUN_STEPS);
        captureBothThemes(builder, main, Screen.PROGRESS, "-running");
        backend.finishGrading();
    }

    // Pictures one screen at every size in the light and dark theme.
    private void captureBothThemes(ShellBuilder builder, MainController main,
            Screen screen, String suffix) throws IOException {
        boolean[] themes = {false, true};
        for (boolean dark : themes) {
            main.setDark(dark);
            for (double[] size : SIZES) {
                Scene scene = builder.resize(size[0], size[1]);
                main.navigate(screen);
                String name = screen.name().toLowerCase(Locale.ROOT)
                        .replace('_', '-') + suffix + "-"
                        + ThemeManager.themeName(dark) + "-"
                        + (int) size[0] + "x" + (int) size[1];
                save(scene, folder.resolve(name + ".png"));
                if (fullPage) {
                    saveFullPage(builder, screen, size, name);
                }
            }
        }
    }

    // Saves the screen again in a window tall enough for its whole page,
    // when the page scrolls, then restores the window.
    private void saveFullPage(ShellBuilder builder, Screen screen,
            double[] size, String name) throws IOException {
        Parent view = builder.getNavigator().getView(screen);
        if (!(view instanceof ScrollPane)) {
            return;
        }
        ScrollPane page = (ScrollPane) view;
        Node content = page.getContent();
        double hidden = content.getLayoutBounds().getHeight()
                - page.getViewportBounds().getHeight();
        if (hidden <= 0.0) {
            return;
        }
        Scene tall = builder.resize(size[0], size[1] + hidden);
        save(tall, folder.resolve(name + "-full.png"));
        builder.resize(size[0], size[1]);
    }

    // Lays the scene out and writes it as a PNG file.
    private void save(Scene scene, Path file) throws IOException {
        Parent root = scene.getRoot();
        root.applyCss();
        root.layout();
        root.applyCss();
        root.layout();
        PngWriter.write(scene.snapshot(null), file);
        written++;
    }
}
