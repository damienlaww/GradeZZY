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

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.backend.RunRecord;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;
import gradezzy.ui.flow.CodeSheet;
import gradezzy.ui.flow.FlowWindow;
import gradezzy.ui.flow.Page;
import gradezzy.ui.flow.Parts;
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

    // The space around the picture of a "?" explanation
    private static final double EXPLANATION_MARGIN = 16.0;

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
            shoot(scene, tag, "0-home");
            window.openAssessments();
            shoot(scene, tag, "0b-assessments");
            window.editAssessment(backend.getAssessment().getName());
            shoot(scene, tag, "0c-assessment-editor");
            window.editAssessment(null);
            shoot(scene, tag, "0d-assessment-new");
            window.closeSheet();
            window.show(Step.SET_UP);
            shoot(scene, tag, "1-set-up-empty");
            shootExplanation(scene, tag);

            for (Path path : backend.getSampleFiles(
                    InputKind.SUBMISSION)) {
                window.getState().getBatch().add(backend.inspect(
                        InputKind.SUBMISSION, path));
            }
            window.show(Step.SET_UP);
            shoot(scene, tag, "2-set-up-files");

            GradingRun run = backend.getRun(new Section(
                    DemoData.MODULE_CODE, DemoData.SAMPLE_SECTION));
            if (run == null) {
                run = firstGradedRun(backend);
            }
            if (run != null) {
                window.openRun(run);
                shoot(scene, tag, "4-review");
                ReviewScreen review = (ReviewScreen) window.getScreen(
                        Step.REVIEW);
                Submission open = firstNeedingReview(run, backend);
                if (open != null) {
                    review.selectStudent(open);
                    shoot(scene, tag, "5-review-student");
                    window.showSheet(CodeSheet.create(window, open));
                    shoot(scene, tag, "5c-code");
                    window.closeSheet();
                    javafx.scene.control.Button openCode = SceneFinder
                            .button(window.getRoot(), "Open code");
                    if (openCode != null) {
                        openCode.fire();
                        shoot(scene, tag, "5c2-code-issue");
                    }
                    window.closeSheet();
                }
                window.advanceTo(Step.EXPORT);
                shoot(scene, tag, "6-export");
            } else {
                window.getState().reach(Step.EXPORT);
                for (Step step : Step.values()) {
                    window.show(step);
                }
            }
            markingExtras(window, backend, scene, tag);
            window.showPage(Page.STATISTICS);
            shoot(scene, tag, "7-statistics");
            statisticsExtras(window, backend, scene, tag);
            window.showPage(Page.RUNS);
            shoot(scene, tag, "8-runs");
            runsExtras(window, backend, scene, tag);
            window.showPage(Page.SETTINGS);
            shoot(scene, tag, "9-settings");
            settingsExtras(window, backend, scene, tag);
            window.showPage(Page.MARKING);
            window.showHelp();
            shoot(scene, tag, "10-help");
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

    // Opens more states of the Grading steps, such as sheets and filters.
    private void markingExtras(FlowWindow window, DashboardBackend backend,
            Scene scene, String tag) throws IOException {
        // Pictures are named "5x-..." so they sort after Review
        // marking2: the student panel with the adjust mark sheet, and
        // the table showing only the students who need review
        GradingRun reviewRun = window.getState().getRun();
        if (reviewRun != null) {
            ReviewScreen review = (ReviewScreen) window.getScreen(
                    Step.REVIEW);
            window.show(Step.REVIEW);
            Submission student = firstNeedingReview(reviewRun, backend);
            if (student != null && backend.supports(
                    gradezzy.ui.backend.Capability.SCORE_ADJUSTMENT)) {
                review.adjustMark(student, backend.getAssessment()
                        .getQuestions().get(0).getId());
                shoot(scene, tag, "5r-adjust");
                window.closeSheet();
            }
            review.showNeedsReview();
            shoot(scene, tag, "5r-needs-review");
        }

        // marking1: the bottom of Set up, the typed test cases sheet,
        // and Export with the similar code card and its compare sheet
        gradezzy.ui.flow.SetUpScreen setUp = (gradezzy.ui.flow.SetUpScreen)
                window.getScreen(Step.SET_UP);
        javafx.scene.control.ScrollPane setUpPage =
                (javafx.scene.control.ScrollPane) setUp.getView();
        window.show(Step.SET_UP);
        setUpPage.setVvalue(1.0);
        shoot(scene, tag, "5s-set-up-bottom");
        setUpPage.setVvalue(0.0);
        if (!backend.isDemo()) {
            return;
        }
        ImportBatch batch = window.getState().getBatch();
        gradezzy.testcase.TestCase sample = new gradezzy.testcase.TestCase(
                "Q1a", "countVowels", "\"banana\"", "3");
        batch.getTypedTestCases().add(sample);
        batch.setUsingTypedTestCases(true);
        window.show(Step.SET_UP);
        setUp.openTestCases();
        shoot(scene, tag, "5s-test-cases");
        window.closeSheet();
        batch.getTypedTestCases().remove(sample);
        batch.setUsingTypedTestCases(false);

        String similarKey = gradezzy.config.ConfigKeys.PLAGIARISM_ENABLED;
        backend.saveSettings(java.util.Map.of(similarKey, "true"));
        window.show(Step.EXPORT);
        javafx.scene.control.ScrollPane exportPage =
                (javafx.scene.control.ScrollPane) window.getScreen(
                Step.EXPORT).getView();
        exportPage.setVvalue(1.0);
        shoot(scene, tag, "5e-export-similar");
        exportPage.setVvalue(0.0);
        gradezzy.ui.flow.ExportScreen export =
                (gradezzy.ui.flow.ExportScreen) window.getScreen(
                Step.EXPORT);
        if (export.openSimilarPair(0)) {
            shoot(scene, tag, "5e-similar-code");
            window.closeSheet();
        }
        backend.saveSettings(java.util.Map.of(similarKey, "false"));
        window.show(Step.EXPORT);
    }

    // Opens more states of the Statistics tab, such as Compare sections.
    private void statisticsExtras(FlowWindow window,
            DashboardBackend backend, Scene scene, String tag)
            throws IOException {
        // Pictures are named "7x-..."
        gradezzy.ui.flow.StatisticsScreen statistics =
                (gradezzy.ui.flow.StatisticsScreen) window.getPageScreen(
                Page.STATISTICS);
        statistics.scrollTo(true);
        shoot(scene, tag, "7c-statistics-bottom");
        statistics.scrollTo(false);
        statistics.setCompare(true);
        if (!backend.isDemo()) {
            shoot(scene, tag, "7b-statistics-compare");
            statistics.setCompare(false);
            return;
        }
        shoot(scene, tag, "7d-statistics-compare-one");
        statistics.setCompare(false);

        // A separate sample backend, so the other pictures do not
        // change: G2 and G3 are graded from some of the sample students
        // and G1 uses the large cohort, so Compare has curves to draw.
        DemoBackend sample = new DemoBackend(services);
        sample.setManualStepping(true);
        String[] sections = {"G2", "G3"};
        int[][] students = {{0, 1, 2, 4}, {1, 2, 3, 4}};
        java.util.List<Path> files = sample.getSampleFiles(
                InputKind.SUBMISSION);
        gradezzy.grading.ProgressListener quiet =
                new gradezzy.grading.ProgressListener() {
            @Override
            public void onRunStarted(int totalSubmissions) {
                // nothing to show
            }

            @Override
            public void onSubmissionStarted(Submission submission) {
                // nothing to show
            }

            @Override
            public void onStageStarted(Submission submission,
                    String stageName) {
                // nothing to show
            }

            @Override
            public void onSubmissionFinished(Submission submission) {
                // nothing to show
            }

            @Override
            public void onRunFinished(GradingRun gradingRun) {
                // nothing to show
            }
        };
        gradezzy.ui.backend.RunCallback done =
                new gradezzy.ui.backend.RunCallback() {
            @Override
            public void onFinished(GradingRun gradingRun) {
                // the backend keeps the run
            }

            @Override
            public void onFailed(String message) {
                System.out.println("Sample section not graded: " + message);
            }
        };
        for (int index = 0; index < sections.length; index++) {
            ImportBatch batch = new ImportBatch();
            for (int student : students[index]) {
                batch.add(sample.inspect(InputKind.SUBMISSION,
                        files.get(student)));
            }
            sample.startGrading(batch, new Section(DemoData.MODULE_CODE,
                    sections[index]), quiet, done);
            sample.finishGrading();
        }
        sample.useLargeSampleCohort(true);

        FlowWindow sampleWindow = new FlowWindow(sample, window.isDark());
        Scene sampleScene = sampleWindow.build(scene.getWidth(),
                scene.getHeight());
        sampleWindow.openStatistics(DemoData.MODULE_CODE,
                DemoData.SAMPLE_SECTION);
        shoot(sampleScene, tag, "7e-statistics-large");
        gradezzy.ui.flow.StatisticsScreen sampleStatistics =
                (gradezzy.ui.flow.StatisticsScreen)
                sampleWindow.getPageScreen(Page.STATISTICS);
        sampleStatistics.setCompare(true);
        shoot(sampleScene, tag, "7b-statistics-compare");
    }

    // Opens more states of the Runs tab, such as Backups and Activity.
    private void runsExtras(FlowWindow window, DashboardBackend backend,
            Scene scene, String tag) throws IOException {
        // Pictures are named "8x-..."
        gradezzy.ui.flow.RunsScreen runs = (gradezzy.ui.flow.RunsScreen)
                window.getPageScreen(Page.RUNS);
        runs.showBackups();
        shoot(scene, tag, "8b-runs-backups");
        runs.showActivity();
        shoot(scene, tag, "8c-runs-activity");
        runs.showRuns();
    }

    // Opens more states of the Settings tab, such as a changed setting.
    private void settingsExtras(FlowWindow window, DashboardBackend backend,
            Scene scene, String tag) throws IOException {
        // Pictures are named "9x-..."; nothing is saved
        gradezzy.ui.flow.SettingsScreen settings =
                (gradezzy.ui.flow.SettingsScreen) window.getPageScreen(
                Page.SETTINGS);
        settings.previewChange(gradezzy.config.ConfigKeys.OUTPUT_DIR,
                "workspace/exports");
        settings.previewChange(gradezzy.config.ConfigKeys.WORKING_DIR, "");
        shoot(scene, tag, "9b-settings-changed");
        settings.discardChanges();
        settings.scrollTo(true);
        shoot(scene, tag, "9c-settings-bottom");
        settings.showAssessmentGroup();
        shoot(scene, tag, "9d-settings-assessment");
        java.util.List<String> sections = window.getBackend()
                .getSectionCodes(window.getState().getModuleCode());
        if (!sections.isEmpty()) {
            gradezzy.ui.flow.CourseSheets.askDeleteSection(window,
                    window.getState().getModuleCode(), sections.get(0),
                    () -> { });
            shoot(scene, tag, "9e-delete-confirm");
            window.closeSheet();
        }
        settings.scrollTo(false);
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

    // Saves a picture of what a "?" opens, in its own small scene with
    // the window's stylesheet, as the popup has.
    private void shootExplanation(Scene scene, String tag)
            throws IOException {
        VBox box = Parts.explanation("Starter files", "The folder students "
                + "were given before the test, such as RenameToYourUsername."
                + " It is used to spot untouched answers.");
        if (scene.getRoot().getStyleClass().contains("dark")) {
            box.getStyleClass().add("dark");
        }
        StackPane holder = new StackPane(box);
        holder.setPadding(new Insets(EXPLANATION_MARGIN));
        Scene small = new Scene(holder);
        small.getStylesheets().addAll(scene.getStylesheets());
        shoot(small, tag, "1b-explanation");
    }

    // Returns the newest run that has students, from any section, or
    // null. Live mode may have no run for the sample section.
    private GradingRun firstGradedRun(DashboardBackend backend) {
        for (RunRecord record : backend.listRuns()) {
            if (!record.getRun().getSubmissions().isEmpty()) {
                return record.getRun();
            }
        }
        return null;
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
        stopCaching(scene.getRoot());
        Files.createDirectories(folder);
        WritableImage image = scene.getRoot().snapshot(null, null);
        PngWriter.write(image, folder.resolve(tag + "-" + name + ".png"));
    }

    // Turns off the picture a scroll pane keeps of its content. Off screen
    // that picture is not redrawn, so a snapshot could show content that
    // has since changed.
    private void stopCaching(Node node) {
        if (node.getStyleClass().contains("viewport")) {
            node.setCache(false);
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                stopCaching(child);
            }
        }
    }
}
