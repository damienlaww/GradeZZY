/*
 * File: OverviewController.java
 *
 * Purpose: Controller of the Overview screen.
 *
 * Where it fits: The first screen. It shows the current run, what waits for
 * review, the grading journey, the sections and recent activity.
 */
package gradezzy.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.MetricTile;
import gradezzy.ui.component.Responsive;

/**
 * Controller of the Overview screen.
 */
public class OverviewController extends BaseController {

    // The share of the width given to the sections panel
    private static final double SECTIONS_PERCENT = 55.0;

    // The width below which the two panels stack
    private static final double STACK_WIDTH = 680.0;

    // The number of audit entries shown
    private static final int RECENT_COUNT = 4;

    // The space between the parts of a section or activity row
    private static final double ROW_GAP = 8.0;

    // The space between an activity's action and its detail
    private static final double TEXT_GAP = 4.0;

    // The space between an activity and its time
    private static final double TIME_GAP = 12.0;

    @FXML
    private Label statusLabel;

    @FXML
    private HBox modeBanner;

    @FXML
    private Label modeLabel;

    @FXML
    private MetricTile moduleTile;

    @FXML
    private MetricTile runTile;

    @FXML
    private MetricTile gradedTile;

    @FXML
    private MetricTile reviewTile;

    @FXML
    private FlowPane journey;

    @FXML
    private GridPane columns;

    @FXML
    private VBox sectionsPanel;

    @FXML
    private VBox activityPanel;

    @FXML
    private VBox sectionRows;

    @FXML
    private VBox activityRows;

    /**
     * Creates a new overview controller.
     */
    public OverviewController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        Responsive.twoColumns(columns, sectionsPanel, activityPanel,
                SECTIONS_PERCENT, STACK_WIDTH);
        addJourneyStep("1  Check the Assessment", Screen.TEST_CASES);
        addJourneyStep("2  Add Submissions", Screen.UPLOAD);
        addJourneyStep("3  Grade and Follow Progress", Screen.PROGRESS);
        addJourneyStep("4  Review Anomalies", Screen.ANOMALY_LOG);
        addJourneyStep("5  Check Results and Export", Screen.RESULTS);
        addJourneyStep("6  Read the Statistics", Screen.STATISTICS);
    }

    /**
     * Refreshes the screen each time it is shown.
     */
    @Override
    public void onShow() {
        Section section = getState().getSection();
        GradingRun run = getBackend().getRun(section);
        int open = countWaiting(run);

        showMode();
        showMetrics(section, run, open);
        showSections();
        showActivity();

        if (run == null) {
            statusLabel.setText(section.getLabel() + " has no grading run "
                    + "yet. Import submissions to start one.");
        } else {
            statusLabel.setText(section.getLabel() + ", "
                    + run.getAssessment().getName() + ". "
                    + countGraded(run) + " of " + run.getSubmissions().size()
                    + " graded, " + open + " waiting for review.");
        }
    }

    /**
     * Opens the Submissions screen.
     */
    @FXML
    public void handleImport() {
        getShell().navigate(Screen.UPLOAD);
    }

    /**
     * Opens the Help screen, which lists the backend connections.
     */
    @FXML
    public void handleConnections() {
        getShell().navigate(Screen.HELP);
    }

    /**
     * Opens the Modules screen.
     */
    @FXML
    public void handleModules() {
        getShell().navigate(Screen.MODULES);
    }

    /**
     * Opens the Audit Log screen.
     */
    @FXML
    public void handleAuditLog() {
        getShell().navigate(Screen.AUDIT_LOG);
    }

    // Adds one step of the main journey as a button.
    private void addJourneyStep(String text, Screen screen) {
        Button step = new Button(text);
        step.setOnAction(event -> getShell().navigate(screen));
        journey.getChildren().add(step);
    }

    // Says whether demo data is shown or how ready the live backend is.
    private void showMode() {
        if (getBackend().isDemo()) {
            modeLabel.setText("Demo data. Six synthetic students in "
                    + "CS102 G1. Nothing here reads, grades or saves real "
                    + "files.");
            return;
        }

        int works = 0;
        int waiting = 0;
        int proposed = 0;
        for (Capability capability : Capability.values()) {
            if (capability.getStatus() == CapabilityStatus.WORKS_NOW) {
                works++;
            } else if (capability.getStatus() == CapabilityStatus.WAITING) {
                waiting++;
            } else {
                proposed++;
            }
        }
        modeLabel.setText("Live mode. " + works + " backend features work "
                + "now, " + waiting + " wait for a teammate and " + proposed
                + " are proposed. Screens say when a feature is not "
                + "connected yet.");
    }

    // Fills in the four key numbers.
    private void showMetrics(Section section, GradingRun run, int open) {
        String moduleCode = section.getModuleCode();
        moduleTile.setValue(moduleCode);
        String moduleName = getBackend().getModuleName(moduleCode);
        if (moduleName.isEmpty()) {
            moduleName = "Configured module";
        }
        moduleTile.setCaption(moduleName + ", section "
                + section.getSectionCode());

        if (run == null) {
            runTile.setValue("None");
            runTile.setCaption("Grade a batch to start one");
            gradedTile.setValue(Labels.NO_MARK);
            gradedTile.setCaption("No submissions yet");
        } else {
            int graded = countGraded(run);
            int waiting = run.getSubmissions().size() - graded;
            runTile.setValue(run.getId());
            runTile.setCaption("Started " + Labels.formatDateTime(
                    run.getStartedAt()));
            gradedTile.setValue(graded + " of " + run.getSubmissions()
                    .size());
            gradedTile.setCaption(waiting + " not graded");
        }
        reviewTile.setValue(String.valueOf(open));
        reviewTile.setCaption("Flags never change marks");
    }

    // Lists each module with its sections, grouped.
    private void showSections() {
        sectionRows.getChildren().clear();
        for (String moduleCode : getBackend().getModuleCodes()) {
            Label module = new Label(moduleTitle(moduleCode));
            module.getStyleClass().add("context-value");
            sectionRows.getChildren().add(module);

            List<String> empty = new ArrayList<>();
            for (String sectionCode
                    : getBackend().getSectionCodes(moduleCode)) {
                GradingRun run = getBackend().getRun(new Section(moduleCode,
                        sectionCode));
                if (run == null) {
                    empty.add(sectionCode);
                } else {
                    sectionRows.getChildren().add(sectionRow(moduleCode,
                            sectionCode, run));
                }
            }
            if (!empty.isEmpty()) {
                sectionRows.getChildren().add(emptySectionsRow(empty));
            }
        }
    }

    // Builds the row of one section that has a run.
    private HBox sectionRow(String moduleCode, String sectionCode,
            GradingRun run) {
        Label text = new Label(sectionCode + ": run " + run.getId() + ", "
                + countGraded(run) + " of " + run.getSubmissions().size()
                + " graded");
        text.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button open = new Button("Open Results");
        open.getStyleClass().add("link-button");
        open.setOnAction(event -> openSection(moduleCode, sectionCode));
        HBox row = new HBox(ROW_GAP, Icon.FOLDER.create(), text, spacer,
                open);
        row.setAlignment(Pos.CENTER_LEFT);
        Responsive.keepButtonsWhole(row);
        return row;
    }

    // Builds the row that lists the sections with no run yet.
    private HBox emptySectionsRow(List<String> sections) {
        String names = String.join(", ", sections);
        Label text = new Label(names + ": no grading runs yet");
        text.getStyleClass().add("secondary-text");
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button choose = new Button("Choose Section");
        choose.getStyleClass().add("link-button");
        choose.setOnAction(event -> getShell().navigate(Screen.MODULES));
        HBox row = new HBox(ROW_GAP, Icon.FOLDER.create(), text, choose);
        row.setAlignment(Pos.CENTER_LEFT);
        Responsive.keepButtonsWhole(row);
        return row;
    }

    // Lists the latest audit entries.
    private void showActivity() {
        activityRows.getChildren().clear();
        List<AuditEntry> entries = getBackend().listAuditEntries();
        if (entries.isEmpty()) {
            EmptyState empty = new EmptyState(Icon.AUDIT, "No activity yet",
                    "Approvals, retries, mark changes and exports are "
                    + "listed here as they happen.");
            activityRows.getChildren().add(empty);
            return;
        }

        int count = Math.min(RECENT_COUNT, entries.size());
        for (int index = 0; index < count; index++) {
            AuditEntry entry = entries.get(index);
            Label action = new Label(entry.getAction());
            action.getStyleClass().add("context-value");
            Label detail = new Label(entry.getDetail());
            detail.getStyleClass().add("secondary-text");
            detail.setWrapText(true);
            Label time = new Label(Labels.formatTime(entry.getTimestamp()));
            time.getStyleClass().add("secondary-text");
            time.setMinWidth(Region.USE_PREF_SIZE);
            VBox text = new VBox(TEXT_GAP, action, detail);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(TIME_GAP, text, time);
            activityRows.getChildren().add(row);
        }
    }

    // Shows a section's results.
    private void openSection(String moduleCode, String sectionCode) {
        getState().moduleCodeProperty().set(moduleCode);
        getState().sectionCodeProperty().set(sectionCode);
        getShell().navigate(Screen.RESULTS);
    }

    // Returns the module code with its name when one is known.
    private String moduleTitle(String moduleCode) {
        String name = getBackend().getModuleName(moduleCode);
        if (name.isEmpty()) {
            return moduleCode;
        }
        return moduleCode + ", " + name;
    }

    // Counts the graded students of a run.
    private int countGraded(GradingRun run) {
        int graded = 0;
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded++;
            }
        }
        return graded;
    }

    // Counts the anomalies that wait for the instructor.
    private int countWaiting(GradingRun run) {
        int count = 0;
        for (Anomaly anomaly : getBackend().listAnomalies(run)) {
            if (getBackend().needsReview(anomaly)) {
                count++;
            }
        }
        return count;
    }
}
