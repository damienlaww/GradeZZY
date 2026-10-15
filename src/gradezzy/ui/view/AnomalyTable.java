/*
 * File: AnomalyTable.java
 *
 * Purpose: Builds the columns of the Review Anomalies table, describes
 * what it shows in the status line, and explains why it is empty when no
 * flag is shown.
 *
 * Where it fits: The Review Anomalies screen creates one in onAttach. Each
 * row is one flag: the student, the flag with its message, the severity
 * and the review decision, each status as an icon and words.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.List;
import java.util.function.Function;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.ui.AppShell;
import gradezzy.ui.Screen;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;

/**
 * The columns, status line and empty states of the flag table.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class AnomalyTable {

    // The narrowest the student column may get
    private static final double STUDENT_WIDTH = 140.0;

    // The width the student column starts from
    private static final double STUDENT_PREF = 160.0;

    // The narrowest the flag column may get
    private static final double FLAG_WIDTH = 200.0;

    // The width the flag column starts from
    private static final double FLAG_PREF = 340.0;

    // The narrowest the severity column may get
    private static final double SEVERITY_WIDTH = 96.0;

    // The narrowest the decision column may get
    private static final double DECISION_WIDTH = 150.0;

    // The backend that knows which flags wait for a decision
    private final DashboardBackend backend;

    // The window, for the buttons of the empty states
    private final AppShell shell;

    /**
     * Adds the Student, Flag, Severity and Decision columns to a table.
     *
     * @param table the table
     * @param students the students of the run
     * @param backend the backend that knows each flag's decision
     * @param shell the window, for the buttons of the empty states
     */
    public AnomalyTable(TableView<Anomaly> table, ReviewStudents students,
            DashboardBackend backend, AppShell shell) {
        this.backend = backend;
        this.shell = shell;
        Function<Anomaly, Anomaly> itself = Function.identity();

        TableColumn<Anomaly, Anomaly> student = ReviewColumns.add(table,
                "Student", STUDENT_WIDTH, STUDENT_PREF);
        student.setCellFactory(column -> ReviewColumns.studentCell(
                students, itself));
        TableColumn<Anomaly, Anomaly> flag = ReviewColumns.add(table, "Flag",
                FLAG_WIDTH, FLAG_PREF);
        flag.setCellFactory(column -> ReviewColumns.findingCell(itself));
        TableColumn<Anomaly, Anomaly> severity = ReviewColumns.add(table,
                "Severity", SEVERITY_WIDTH, SEVERITY_WIDTH);
        severity.setCellFactory(column -> ReviewColumns.badgeCell(
                anomaly -> StatusBadge.forSeverity(anomaly.getSeverity())));
        TableColumn<Anomaly, Anomaly> decision = ReviewColumns.add(table,
                "Decision", DECISION_WIDTH, DECISION_WIDTH);
        decision.setCellFactory(column -> ReviewColumns.badgeCell(
                anomaly -> StatusBadge.forDecision(
                backend.getDecision(anomaly))));
    }

    /**
     * Describes how many flags wait for a decision, are shown and are
     * selected.
     *
     * @param run the run of the section, or null
     * @param anomalies every flag of the run
     * @param shownCount how many flags pass the filters
     * @param selectedCount how many flags are selected
     * @param section the section's name, such as "CS102 G1"
     * @return the status line
     */
    public String statusText(GradingRun run, List<Anomaly> anomalies,
            int shownCount, int selectedCount, String section) {
        if (run == null) {
            return "No grading run for " + section
                    + " yet, so there are no flags to review.";
        }
        int waiting = 0;
        for (Anomaly anomaly : anomalies) {
            if (backend.needsReview(anomaly)) {
                waiting++;
            }
        }
        String verb = " wait";
        if (waiting == 1) {
            verb = " waits";
        }
        String text = waiting + " flag" + Labels.plural(waiting) + verb
                + " for a decision, " + anomalies.size() + " in total. "
                + "Flags never change marks. " + shownCount + " shown, "
                + selectedCount + " selected.";
        if (selectedCount == 0 && shownCount > 0) {
            text += " Select flags to mark them reviewed or reject them.";
        }
        return text;
    }

    /**
     * Builds the explanation that fits why no flag is shown.
     *
     * @param run the run of the section, or null
     * @param anyFlags whether the run has flags at all
     * @param section the section's name, such as "CS102 G1"
     * @param clearFilters clears the search and the filters
     * @return the empty state
     */
    public EmptyState emptyState(GradingRun run, boolean anyFlags,
            String section, Runnable clearFilters) {
        if (run == null) {
            EmptyState empty = new EmptyState(Icon.ANOMALIES,
                    "No grading run for " + section + " yet",
                    "Flags appear once a batch for this section is graded. "
                    + "Add the submissions first, or choose another "
                    + "section above.");
            empty.setAction("Go to Submissions",
                    () -> shell.navigate(Screen.UPLOAD));
            return empty;
        }
        if (!anyFlags) {
            EmptyState empty = new EmptyState(Icon.CHECK,
                    "No flags in this run", "Grading recorded nothing that "
                    + "needs your review. The marks are on the Results "
                    + "screen.");
            empty.setAction("View Results",
                    () -> shell.navigate(Screen.RESULTS));
            return empty;
        }
        EmptyState empty = new EmptyState(Icon.SEARCH, "No flags match",
                "Change the search or the filters to see more flags.");
        empty.setAction("Clear Filters", clearFilters);
        return empty;
    }
}
