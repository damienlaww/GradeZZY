/*
 * File: FlowCheck.java
 *
 * Purpose: Opens every step and page of the guided window off screen,
 * with the live and the demo backend, in both themes, and can save a
 * picture of each.
 *
 * Where it fits: ScreenCheck calls it for "sh run.sh --check"; MainApp
 * calls it with a folder for "sh run.sh --flowshots".
 */
package gradezzy.ui.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.image.WritableImage;

import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;
import gradezzy.ui.flow.FlowWindow;
import gradezzy.ui.flow.ReviewScreen;
import gradezzy.ui.flow.Step;

/**
 * Loads the guided window's screens and optionally saves pictures.
 */
public class FlowCheck {

    // The window sizes checked: the default and the smallest supported
    private static final double[][] SIZES = {{1200, 800}, {960, 640}};

    // Layout passes before a picture, so scroll panes settle
    private static final int LAYOUT_PASSES = 3;

    // Every service
    private final ServiceRegistry services;

    // Where pictures are saved, or null for none
    private final Path folder;

    /**
     * Creates the check.
     *
     * @param services every service
     * @param folder where to save pictures, or null to save none
     */
    public FlowCheck(ServiceRegistry services, Path folder) {
        this.services = services;
        this.folder = folder;
    }

    /**
     * Opens every screen in both modes, themes and sizes.
     *
     * @return the number of problems found
     */
    public int run() {
        int problems = 0;
        boolean[] themes = {false, true};
        for (boolean dark : themes) {
            for (double[] size : SIZES) {
                problems += check(new LiveBackend(services), "live", dark,
                        size);
                problems += check(new DemoBackend(services), "demo", dark,
                        size);
            }
        }
        if (problems == 0) {
            System.out.println("Guided window: every step and page opened "
                    + "in live and demo mode, both themes, two sizes.");
        }
        return problems;
    }

    // Walks through the screens with one backend, theme and size.
    private int check(DashboardBackend backend, String mode, boolean dark,
            double[] size) {
        String tag = mode + "-" + (dark ? "dark" : "light") + "-"
                + (int) size[0];
        try {
            FlowWindow window = new FlowWindow(backend, dark);
            Scene scene = window.build(size[0], size[1]);
            shoot(scene, tag, "1-set-up-empty");

            for (Path path : backend.getSampleFiles(
                    InputKind.SUBMISSION)) {
                window.getState().getBatch().add(backend.inspect(
                        InputKind.SUBMISSION, path));
            }
            window.show(Step.SET_UP);
            shoot(scene, tag, "2-set-up-files");

            GradingRun run = backend.getRun(new Section(
                    DemoData.MODULE_CODE, DemoData.SAMPLE_SECTION));
            if (run != null) {
                window.openRun(run);
                shoot(scene, tag, "4-review");
                ReviewScreen review = (ReviewScreen) window.getScreen(
                        Step.REVIEW);
                Submission open = firstNeedingReview(run, backend);
                if (open != null) {
                    review.selectStudent(open);
                    shoot(scene, tag, "5-review-student");
                }
                window.advanceTo(Step.EXPORT);
                shoot(scene, tag, "6-export");
            } else {
                window.getState().reach(Step.EXPORT);
                for (Step step : Step.values()) {
                    window.show(step);
                }
            }
            window.showHistory();
            shoot(scene, tag, "7-history");
            window.showSettings();
            shoot(scene, tag, "8-settings");
            window.closePage();
            window.showHelp();
            shoot(scene, tag, "9-help");
            window.closeSheet();
            shootGrading(window, backend, scene, tag);
        } catch (RuntimeException exception) {
            System.out.println("FAILED: guided window (" + tag + "): "
                    + exception);
            exception.printStackTrace(System.out);
            return 1;
        } catch (IOException exception) {
            System.out.println("FAILED: picture (" + tag + "): "
                    + exception.getMessage());
            return 1;
        }
        System.out.println("Loaded guided window (" + tag + ")");
        return 0;
    }

    // Starts grading the usable sample files and takes one picture.
    private void shootGrading(FlowWindow window, DashboardBackend backend,
            Scene scene, String tag) throws IOException {
        if (!backend.isDemo()) {
            return;
        }
        ImportBatch batch = window.getState().getBatch();
        for (ImportItem item : batch.getItems(InputKind.SUBMISSION)) {
            if (!batch.getEffectiveState(item).isUsable()) {
                batch.remove(item);
            }
        }
        window.show(Step.SET_UP);
        window.startGrading();
        shoot(scene, tag, "3-grade");
        backend.cancelGrading();
    }

    // Returns the first student with something to check, or null.
    private Submission firstNeedingReview(GradingRun run,
            DashboardBackend backend) {
        Submission found = null;
        for (Submission submission : run.getSubmissions()) {
            if (found == null && !submission.getAnomalies().isEmpty()
                    && backend.needsReview(submission.getAnomalies()
                    .get(0))) {
                found = submission;
            }
        }
        return found;
    }

    // Lays out the scene and saves a picture of it when asked to.
    private void shoot(Scene scene, String tag, String name)
            throws IOException {
        for (int pass = 0; pass < LAYOUT_PASSES; pass++) {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }
        if (folder == null) {
            return;
        }
        Files.createDirectories(folder);
        WritableImage image = scene.getRoot().snapshot(null, null);
        PngWriter.write(image, folder.resolve(tag + "-" + name + ".png"));
    }
}
