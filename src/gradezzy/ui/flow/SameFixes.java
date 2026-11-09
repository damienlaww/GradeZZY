/*
 * File: SameFixes.java
 *
 * Purpose: Keeps or rejects one fix together with the same kind of fix
 * of the other students in the run, such as every folder that had the
 * wrong name, so the instructor need not open each student in turn.
 *
 * Where it fits: StudentPanel asks it when Keep fix or Reject fix is
 * pressed. When other students have a fix of the same kind waiting, it
 * asks in a sheet whether to decide just this one or all of them;
 * otherwise the panel decides the one fix as before.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;

/**
 * Finds the fixes of the same kind as one fix and decides them together.
 *
 * <p>Tier: COULD.
 */
public class SameFixes {

    // The most fixes listed in the sheet before "and N more", so the
    // sheet fits a short window
    private static final int MAX_LISTED = 5;

    // The width of the sheet's text and table
    private static final double TEXT_WIDTH = 520.0;

    // The height of a table row: a name with the username under it
    private static final double ROW_HEIGHT = 48.0;

    // The width of the Student column
    private static final double STUDENT_WIDTH = 220.0;

    // The style of a value shown in code type, as on the test cases sheet
    private static final String CODE_STYLE = "code-text";

    // The window, for the sheet and the notices
    private final FlowWindow window;

    // Called after fixes are decided, so the panel and table redraw
    private final Runnable onChanged;

    /**
     * Creates the helper for one student panel.
     *
     * @param window the window
     * @param onChanged called after fixes are decided
     */
    public SameFixes(FlowWindow window, Runnable onChanged) {
        this.window = window;
        this.onChanged = onChanged;
    }

    /**
     * Returns the other fixes in the run that wait for a decision and
     * fix the same kind of problem as one fix, in the order of the run.
     * Their old values may differ, such as two wrong folder names.
     *
     * @param backend the backend that keeps the fixes
     * @param run the run shown, or null
     * @param repair the fix being decided
     * @return the other fixes, empty when there are none
     */
    public static List<RepairItem> find(DashboardBackend backend,
            GradingRun run, RepairItem repair) {
        List<RepairItem> same = new ArrayList<>();
        if (run == null) {
            return same;
        }
        Anomaly anomaly = repair.getAnomaly();
        for (RepairItem other : backend.listRepairs(run)) {
            Anomaly otherAnomaly = other.getAnomaly();
            if (!other.isBlocked()
                    && otherAnomaly.getType() == anomaly.getType()
                    && otherAnomaly.getFixStatus() == FixStatus.APPLIED
                    && !otherAnomaly.getId().equals(anomaly.getId())) {
                same.add(other);
            }
        }
        return same;
    }

    /**
     * Asks whether to keep just this fix or every fix of the same kind.
     *
     * @param repair the fix whose Keep fix was pressed
     * @param others the other fixes of the same kind
     * @param keepOne keeps just this fix, as Keep fix did before
     */
    public void askKeep(RepairItem repair, List<RepairItem> others,
            Runnable keepOne) {
        List<RepairItem> all = together(repair, others);
        VBox content = describe(repair, others, "Keeping them keeps their "
                + "fixed files, and their marks stay as they are now.");
        window.confirm(question("Keep", repair, others), content,
                "Just this one", keepOne, "Keep all " + all.size(),
                () -> keepAll(all));
    }

    /**
     * Asks whether to reject just this fix or every fix of the same kind.
     *
     * @param repair the fix whose Reject fix was pressed
     * @param others the other fixes of the same kind
     * @param rejectOne rejects just this fix, as Reject fix did before
     */
    public void askReject(RepairItem repair, List<RepairItem> others,
            Runnable rejectOne) {
        List<RepairItem> all = together(repair, others);
        String after = "Rejecting them puts back each student's own files "
                + "and grades them again, one after another.";
        if (!window.getBackend().isDemo()) {
            after = after + " This can take a few minutes; you can keep "
                    + "reviewing.";
        }
        VBox content = describe(repair, others, after);
        window.confirm(question("Reject", repair, others), content,
                "Just this one", rejectOne, "Reject all " + all.size(),
                () -> rejectAll(all));
    }

    // Returns the fix pressed first, then the others.
    private static List<RepairItem> together(RepairItem repair,
            List<RepairItem> others) {
        List<RepairItem> all = new ArrayList<>();
        all.add(repair);
        all.addAll(others);
        return all;
    }

    // Returns the sheet's question, such as "Keep this fix for 4 more
    // students?", or "Keep 2 more fixes like this one?" when a student
    // has more than one of them.
    private static String question(String verb, RepairItem repair,
            List<RepairItem> others) {
        if (isOnePerStudent(repair, others)) {
            return verb + " this fix for " + Parts.plural(others.size(),
                    "more student", "more students") + "?";
        }
        return verb + " " + Parts.plural(others.size(), "more fix",
                "more fixes") + " like this one?";
    }

    // Says whether every other fix belongs to a different student, none
    // of them the student whose fix was pressed.
    private static boolean isOnePerStudent(RepairItem repair,
            List<RepairItem> others) {
        List<String> usernames = new ArrayList<>();
        usernames.add(repair.getAnomaly().getUsername());
        for (RepairItem other : others) {
            String username = other.getAnomaly().getUsername();
            if (usernames.contains(username)) {
                return false;
            }
            usernames.add(username);
        }
        return true;
    }

    // Builds the sheet's text: the kind of problem, a table of the other
    // students with what each had before the fix, and what deciding them
    // all does. What the fix makes is left out, as it is the expected
    // value, such as the username or UTF-8.
    private VBox describe(RepairItem repair, List<RepairItem> others,
            String after) {
        VBox content = new VBox(Parts.GAP);
        content.setPrefWidth(TEXT_WIDTH);
        Label about = Parts.muted(Problems.fixedTitle(
                repair.getAnomaly().getType()) + ". The same kind of fix "
                + "waits for:");
        about.setWrapText(true);

        PlainTable table = new PlainTable(ROW_HEIGHT);
        table.getStyleClass().add("compact");
        table.addColumn("Student", STUDENT_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Before the fix", PlainTable.GROW, Pos.CENTER_LEFT);
        int listed = 0;
        for (RepairItem other : others) {
            if (listed == MAX_LISTED) {
                break;
            }
            table.addRow(new Node[] {studentCell(other),
                    beforeCell(other)});
            listed++;
        }
        content.getChildren().addAll(about, table);
        if (listed < others.size()) {
            content.getChildren().add(Parts.muted("and "
                    + (others.size() - listed) + " more"));
        }

        Label note = Parts.muted(after);
        note.setWrapText(true);
        content.getChildren().add(note);
        return content;
    }

    // Shows a fix's student by name, with the username under it, as the
    // Review table does.
    private Node studentCell(RepairItem repair) {
        String username = repair.getAnomaly().getUsername();
        String name = username;
        Submission submission = findSubmission(username);
        if (submission != null) {
            name = Students.name(submission);
        }
        Label nameLabel = Parts.strong(name);
        Label usernameLabel = Parts.muted(username);
        usernameLabel.getStyleClass().add("small-text");
        VBox box = new VBox(nameLabel, usernameLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        return box;
    }

    // Shows what the student had before the fix, in code type. A value
    // longer than one line, or one the backend does not give, points to
    // the student's card instead.
    private Node beforeCell(RepairItem repair) {
        String before = repair.getOriginalValue();
        if (!window.getBackend().supports(Capability.REPAIR_VALUES)
                || !isOneLine(before)) {
            return Parts.muted("Shown on their card");
        }
        Label value = new Label(before);
        value.getStyleClass().add(CODE_STYLE);
        value.setTooltip(new Tooltip(before));
        value.setMinWidth(0);
        return value;
    }

    // Returns the student with a username in the run shown, or null.
    private Submission findSubmission(String username) {
        GradingRun run = window.getState().getRun();
        if (run == null) {
            return null;
        }
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }

    // Says whether a value is set and fits on one line.
    private static boolean isOneLine(String value) {
        return value != null && !value.trim().isEmpty()
                && !value.contains("\n");
    }

    // Keeps every fix, and offers Undo when the backend can take a kept
    // fix back.
    private void keepAll(List<RepairItem> all) {
        DashboardBackend backend = window.getBackend();
        List<RepairItem> kept = new ArrayList<>();
        String firstProblem = "";
        for (RepairItem repair : all) {
            ActionResult result = backend.approveRepair(repair);
            if (result.isDone()) {
                kept.add(repair);
            } else if (firstProblem.isEmpty()) {
                firstProblem = " " + result.getMessage();
            }
        }
        String message = "Kept " + Parts.plural(kept.size(), "fix",
                "fixes") + "." + firstProblem;
        if (!kept.isEmpty()
                && backend.supports(Capability.REPAIR_DECISION_UNDO)) {
            window.notify(message, "Undo", () -> unkeepAll(kept));
        } else {
            window.notify(message);
        }
        onChanged.run();
    }

    // Takes back the fixes kept together, so they wait for review again.
    // A fix decided otherwise since keeps that decision.
    private void unkeepAll(List<RepairItem> kept) {
        DashboardBackend backend = window.getBackend();
        int putBack = 0;
        for (RepairItem repair : kept) {
            if (repair.getAnomaly().getFixStatus() == FixStatus.APPROVED
                    && backend.undoRepairDecision(repair).isDone()) {
                putBack++;
            }
        }
        window.notify("Put " + Parts.plural(putBack, "fix", "fixes")
                + " back to review.");
        onChanged.run();
    }

    // Rejects every fix. The demo does it at once; live mode grades each
    // student again on a background thread, and the cards say so.
    private void rejectAll(List<RepairItem> all) {
        DashboardBackend backend = window.getBackend();
        UndoFixJob job = new UndoFixJob(backend, all);
        if (backend.isDemo()) {
            rejected(job.runHere());
            return;
        }
        FlowState state = window.getState();
        if (!job.startOnce(state, state.getRun(),
                result -> rejected(result), () -> onChanged.run())) {
            window.notify("Wait until the other fix has finished.");
            return;
        }
        onChanged.run();
    }

    // Tells the outcome of rejecting the fixes and redraws.
    private void rejected(ActionResult result) {
        window.notify(result.getMessage());
        onChanged.run();
    }
}
