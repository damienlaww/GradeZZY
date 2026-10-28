/*
 * File: HistoryScreen.java
 *
 * Purpose: Earlier grading runs (open, resume or restore one), backups,
 * and the activity log of what instructors did.
 *
 * Where it fits: Opened from the top bar over the current step. Replaces
 * the old History and Audit Log screens. Done returns to the step.
 */
package gradezzy.ui.flow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.RunRecord;

/**
 * The History page.
 */
public class HistoryScreen extends FlowScreen {

    // How dates are shown
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The size of the folder icon of a run
    private static final double RUN_ICON_SIZE = 20.0;

    // The page
    private final VBox view = new VBox(Parts.GAP);

    // The runs
    private final VBox runs = new VBox();

    // The activity entries
    private final VBox activity = new VBox();

    // Filters the activity
    private final TextField activitySearch = new TextField();

    // The backup card's content
    private final VBox backups = new VBox(Parts.GAP);

    /**
     * Creates the History page.
     *
     * @param window the window
     */
    public HistoryScreen(FlowWindow window) {
        super(window);
        activitySearch.setPromptText("Search the activity");
        activitySearch.textProperty().addListener((observable, before,
                after) -> refreshActivity());

        TabPane tabs = new TabPane(
                new Tab("Runs", scroll(Parts.card(null, runs))),
                new Tab("Backups", scroll(Parts.card(null, backups))),
                new Tab("Activity", scroll(Parts.card(null, activitySearch,
                activity))));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("page-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        view.getChildren().addAll(Parts.header("History", "Every grading "
                + "run, backups of saved results, and what was done."),
                tabs);
        view.getStyleClass().add("wide-column");
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        refreshRuns();
        refreshBackups();
        refreshActivity();
        getActions().setPrimary("Done", () -> getWindow().closePage(),
                true);
    }

    // Wraps a card in a scroll pane.
    private ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("page-scroll");
        return pane;
    }

    // Lists every run.
    private void refreshRuns() {
        runs.getChildren().clear();
        List<RunRecord> records = new ArrayList<>();
        if (getBackend().supports(Capability.HISTORY)) {
            records = getBackend().listRuns();
        }
        if (records.isEmpty()) {
            runs.getChildren().add(Parts.muted("No run yet. Runs appear "
                    + "here after grading."));
            return;
        }
        for (RunRecord record : records) {
            runs.getChildren().add(createRunRow(record));
        }
    }

    // Builds one run row.
    private HBox createRunRow(RunRecord record) {
        GradingRun run = record.getRun();
        String title = run.getSection().getModuleCode() + "  ·  "
                + run.getSection().getSectionCode() + "  ·  "
                + run.getAssessment().getName();
        String detail = Parts.plural(run.getSubmissions().size(),
                "student") + "  ·  " + run.getStartedAt().format(WHEN)
                + "  ·  " + record.getStateText();
        VBox text = new VBox(Parts.strong(title), Parts.muted(detail));
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = Parts.row(Parts.GAP, Parts.icon(Parts.FOLDER,
                RUN_ICON_SIZE), text);
        row.getStyleClass().add("list-row");
        if (hasUnfinished(run)
                && getBackend().supports(Capability.RESUME_RUN)
                && record.isCurrent()) {
            row.getChildren().add(Parts.small("Resume", () -> {
                getWindow().resumeRun(run);
            }));
        }
        if (record.isCurrent()) {
            row.getChildren().add(Parts.small("Open",
                    () -> getWindow().openRun(run)));
        } else if (record.isRestorable()
                && getBackend().supports(Capability.RESTORE)) {
            row.getChildren().add(Parts.small("Restore",
                    () -> restore(record)));
        }
        return row;
    }

    // Restores an older run and opens it.
    private void restore(RunRecord record) {
        getWindow().confirm("Restore this run?", "It replaces the run now "
                + "kept for " + record.getRun().getSection().getLabel()
                + ". A backup is made first when backups are on.",
                "Restore", () -> {
                    ActionResult result = getBackend().restore(record);
                    getWindow().notify(result.getMessage());
                    if (result.isDone()) {
                        getWindow().openRun(record.getRun());
                    } else {
                        refreshRuns();
                    }
                });
    }

    // Says whether a run has students still to grade.
    private boolean hasUnfinished(GradingRun run) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getStatus() == SubmissionStatus.CANCELLED
                    || submission.getStatus() == SubmissionStatus.PENDING) {
                return true;
            }
        }
        return false;
    }

    // Shows the backup button.
    private void refreshBackups() {
        backups.getChildren().clear();
        if (!getBackend().supports(Capability.BACKUP)) {
            backups.getChildren().add(Parts.muted("Backups are not "
                    + "available while saving is switched off in "
                    + "Settings."));
            return;
        }
        Button now = Parts.secondary("Back up now", () -> {
            ActionResult result = getBackend().backupNow();
            getWindow().notify(result.getMessage());
        });
        backups.getChildren().addAll(Parts.subtitle("A backup is a copy "
                + "of every saved run. Restore an older run from the Runs "
                + "tab."), now);
    }

    // Lists the activity, newest first, matching the search.
    private void refreshActivity() {
        activity.getChildren().clear();
        if (!getBackend().supports(Capability.AUDIT_LOG)) {
            activity.getChildren().add(Parts.muted("The activity log is "
                    + "switched off in Settings."));
            return;
        }
        String text = activitySearch.getText().trim().toLowerCase();
        List<AuditEntry> entries = getBackend().listAuditEntries();
        int shown = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            AuditEntry entry = entries.get(i);
            String all = (entry.getAction() + " " + entry.getDetail())
                    .toLowerCase();
            if (text.isEmpty() || all.contains(text)) {
                VBox words = new VBox(Parts.strong(entry.getAction()),
                        Parts.muted(entry.getDetail()));
                HBox.setHgrow(words, Priority.ALWAYS);
                HBox row = Parts.row(Parts.GAP, words, Parts.muted(
                        entry.getTimestamp().format(WHEN)));
                row.getStyleClass().add("list-row");
                activity.getChildren().add(row);
                shown++;
            }
        }
        if (shown == 0) {
            activity.getChildren().add(Parts.muted("Nothing to show."));
        }
    }
}
