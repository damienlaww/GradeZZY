/*
 * File: PlagiarismController.java
 *
 * Purpose: Controller of the Similarity Review screen.
 *
 * Where it fits: Lists the pairs of submissions whose code is at or
 * above the similarity threshold, and shows the two compared files side
 * by side with the starter and tester files that were left out, so the
 * instructor can read both before deciding anything. Similarity is a
 * review aid: marking a pair reviewed records a decision on its Similar
 * code flags and never changes a mark or labels a student. The pair list
 * and the comparison are parts in gradezzy.ui.view; this controller
 * wires them together.
 *
 * Note on untaught Java: Check Again compares every submission's files,
 * so it runs in a javafx.concurrent.Task on a background thread, as
 * GradingTask does for grading. The list is only redrawn when the task
 * has finished, back on the JavaFX thread.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.List;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.SimilarityDetail;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.view.ReviewDecisions;
import gradezzy.ui.view.ReviewOutcome;
import gradezzy.ui.view.SimilarityComparison;
import gradezzy.ui.view.SimilarityPairList;

/**
 * Controller of the Similarity Review screen.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public class PlagiarismController extends BaseController {

    // The share of the width given to the list of pairs
    private static final double PAIRS_PERCENT = 30.0;

    // The width below which the pair details move under the list
    private static final double STACK_WIDTH = 760.0;

    // The share of the width given to the first student's excerpt
    private static final double CODE_PERCENT = 50.0;

    // The width below which the second excerpt moves under the first
    private static final double CODE_STACK_WIDTH = 580.0;

    @FXML
    private Label statusLabel;

    @FXML
    private Button checkButton;

    @FXML
    private Button reviewButton;

    @FXML
    private HBox disabledBanner;

    @FXML
    private Label disabledLabel;

    @FXML
    private VBox emptyHolder;

    @FXML
    private GridPane columns;

    @FXML
    private VBox pairsPanel;

    @FXML
    private VBox detailPanel;

    @FXML
    private ListView<SimilarityPair> pairList;

    @FXML
    private Label pairTitle;

    @FXML
    private Label pairSummary;

    @FXML
    private HBox decisionHolder;

    @FXML
    private Button undoButton;

    @FXML
    private VBox compareBox;

    @FXML
    private GridPane codeColumns;

    @FXML
    private VBox firstBox;

    @FXML
    private VBox secondBox;

    @FXML
    private Label firstName;

    @FXML
    private Label firstFile;

    @FXML
    private TextArea firstCode;

    @FXML
    private Label secondName;

    @FXML
    private Label secondFile;

    @FXML
    private TextArea secondCode;

    @FXML
    private VBox excludedList;

    @FXML
    private VBox missingHolder;

    // The two compared files of the chosen pair
    private SimilarityComparison comparison = null;

    // The pairs at or above the threshold and the chosen one
    private SimilarityPairList pairs = null;

    // Records decisions with Undo and reports them once
    private ReviewDecisions decisions = null;

    // The student selected elsewhere when this screen was last shown
    private String lastHint = "";

    // The run being checked in the background, or null when no check
    // runs. The pair list must not be read or redrawn meanwhile.
    private GradingRun checkingRun = null;

    /**
     * Creates a new plagiarism controller.
     */
    public PlagiarismController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        Responsive.twoColumns(columns, pairsPanel, detailPanel,
                PAIRS_PERCENT, STACK_WIDTH);
        Responsive.twoColumns(codeColumns, firstBox, secondBox,
                CODE_PERCENT, CODE_STACK_WIDTH);
        comparison = new SimilarityComparison(firstName, firstFile,
                firstCode, secondName, secondFile, secondCode,
                excludedList);
    }

    /**
     * Builds the parts that depend on the backend and the state.
     */
    @Override
    public void onAttach() {
        pairs = new SimilarityPairList(pairList, getBackend(),
                this::refreshDetail);
        decisions = new ReviewDecisions(getBackend(), getState(),
                getShell());
    }

    /**
     * Checks the run of the section the first time it is shown, keeps
     * the chosen pair, and refreshes the decisions each time the screen
     * is shown.
     */
    @Override
    public void onShow() {
        if (checkingRun != null) {
            showCheckingStatus();
            return;
        }
        GradingRun run = currentRun();
        if (!pairs.isChecked(run)) {
            pairs.load(run);
        }
        String hint = getState().getSelectedUsername();
        if (hint == null) {
            hint = "";
        }
        if (!hint.equals(lastHint)) {
            pairs.prefer(hint);
        }
        lastHint = hint;
        refresh();
    }

    /**
     * Runs the similarity check on the section's run again, in the
     * background, and reloads the pairs when it has finished. Check
     * Again is unavailable meanwhile.
     */
    @FXML
    public void handleRunCheck() {
        if (checkingRun != null) {
            return;
        }
        GradingRun run = currentRun();
        if (run == null) {
            showError("There is no graded run for "
                    + getState().getSection().getLabel() + " to check. "
                    + "Grade a batch on the Submissions screen first.");
            return;
        }
        Task<Void> task = new Task<>() {

            /**
             * Asks the backend for the pairs of the run, on a background
             * thread. The pair list only keeps them; they are drawn
             * when the task has finished.
             *
             * @return nothing
             */
            @Override
            protected Void call() {
                pairs.load(run);
                return null;
            }
        };
        task.setOnSucceeded(event -> finishCheck(run));
        task.setOnFailed(event -> checkStopped(task.getException()));
        checkingRun = run;
        showCheckingStatus();
        Thread worker = new Thread(task, "gradezzy-similarity");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Records that the instructor reviewed the chosen pair, on the
     * Similar code flags of both students. No mark changes.
     */
    @FXML
    public void handleMarkReviewed() {
        if (checkingRun != null) {
            return;
        }
        SimilarityPair pair = pairs.getSelected();
        if (pair == null) {
            showError("Select a pair first.");
            return;
        }
        List<Anomaly> flags = pairs.flagsOf(pair);
        if (flags.isEmpty()) {
            showError("No Similar code flag records this pair yet, so "
                    + "there is no decision to store. The similarity check "
                    + "adds one to each student when it lists a pair.");
            return;
        }
        if (!decisions.checkConnected()) {
            return;
        }
        ReviewOutcome outcome = decisions.decideAll(flags,
                ReviewDecision.REVIEWED);
        if (outcome.isEmpty()) {
            showInfo("This pair was already marked reviewed.");
            return;
        }
        decisions.report(outcome, "Similar pair reviewed", "Marked the pair "
                + SimilarityPairList.nameOf(pair) + " as reviewed. No mark "
                + "changed.", "");
        refresh();
    }

    /**
     * Restores the decision the chosen pair's flags had before they were
     * last changed in this session.
     */
    @FXML
    public void handleUndoDecision() {
        SimilarityPair pair = pairs.getSelected();
        if (pair == null || checkingRun != null) {
            return;
        }
        List<Anomaly> flags = decisions.undoable(pairs.flagsOf(pair));
        if (flags.isEmpty()) {
            showError("This pair has no decision to undo in this session.");
            return;
        }
        if (!decisions.checkConnected()) {
            return;
        }
        ReviewOutcome outcome = decisions.restoreAll(flags);
        decisions.report(outcome, "Similar pair decision undone",
                "Restored the earlier decision on the pair "
                + SimilarityPairList.nameOf(pair) + ". No mark changed.",
                "");
        refresh();
    }

    /**
     * Opens the Settings screen, where the similarity check is switched
     * on.
     */
    @FXML
    public void handleOpenSettings() {
        getShell().navigate(Screen.SETTINGS);
    }

    /**
     * Opens the evidence of the first student of the chosen pair.
     */
    @FXML
    public void handleOpenFirst() {
        SimilarityPair pair = pairs.getSelected();
        if (pair != null) {
            openEvidence(pair.getFirstUsername());
        }
    }

    /**
     * Opens the evidence of the second student of the chosen pair.
     */
    @FXML
    public void handleOpenSecond() {
        SimilarityPair pair = pairs.getSelected();
        if (pair != null) {
            openEvidence(pair.getSecondUsername());
        }
    }

    // Returns the run of the section in the context bar.
    private GradingRun currentRun() {
        return getBackend().getRun(getState().getSection());
    }

    // Draws the pairs of a finished check and says how many were found.
    private void finishCheck(GradingRun run) {
        checkingRun = null;
        if (run != currentRun()) {
            // The section changed during the check, so its run is shown
            onShow();
            return;
        }
        refresh();
        showInfo("Checked run " + run.getId() + " again: " + pairs.size()
                + " pair" + Labels.plural(pairs.size()) + " at or above "
                + pairs.thresholdText() + ".");
    }

    // Reports a check that ended with an exception.
    private void checkStopped(Throwable problem) {
        checkingRun = null;
        String detail = "no details were given";
        if (problem != null && problem.getMessage() != null) {
            detail = problem.getMessage();
        }
        refresh();
        showError("The similarity check stopped unexpectedly (" + detail
                + "). The list may be incomplete; press Check Again.");
    }

    // Says that a check runs and keeps the actions unavailable meanwhile.
    private void showCheckingStatus() {
        checkButton.setDisable(true);
        reviewButton.setDisable(true);
        undoButton.setDisable(true);
        statusLabel.setText("Checking run " + checkingRun.getId()
                + " for similar code. The list updates when the check is "
                + "done.");
    }

    // Redraws the banner, the list, the chosen pair and the status.
    private void refresh() {
        if (checkingRun != null) {
            showCheckingStatus();
            return;
        }
        GradingRun run = currentRun();
        boolean any = run != null && pairs.size() > 0;
        show(disabledBanner, !getBackend().isSimilarityEnabled());
        String off = "The similarity check is off in Settings "
                + "(plagiarism.enabled is false).";
        if (getBackend().isDemo() && any) {
            off += " The sample pair is shown so you can practise the "
                    + "review.";
        }
        disabledLabel.setText(off);

        emptyHolder.getChildren().clear();
        if (!any) {
            emptyHolder.getChildren().add(emptyStateFor(run));
        }
        show(emptyHolder, !any);
        show(columns, any);
        checkButton.setDisable(run == null);

        pairs.show(run);
        refreshStatus(run);
        refreshDetail();
    }

    // Builds the empty state that fits why no pair is shown.
    private EmptyState emptyStateFor(GradingRun run) {
        String section = getState().getSection().getLabel();
        if (run == null) {
            EmptyState empty = new EmptyState(Icon.SIMILARITY,
                    "No grading run for " + section + " yet",
                    "Similarity is checked on a graded run. Grade a batch "
                    + "for this section on the Submissions screen, or "
                    + "choose another section above.");
            empty.setAction("Go to Submissions",
                    () -> getShell().navigate(Screen.UPLOAD));
            return empty;
        }
        EmptyState empty = new EmptyState(Icon.SIMILARITY,
                "No pairs at or above the threshold", "No two submissions "
                + "in this run reached " + pairs.thresholdText()
                + ". Nothing here needs a review.");
        empty.setAction("Check Again", () -> handleRunCheck());
        return empty;
    }

    // Says how many pairs are listed and what similarity means.
    private void refreshStatus(GradingRun run) {
        String aid = " Similarity is a review aid, not proof of copying.";
        if (run == null) {
            statusLabel.setText("No grading run for "
                    + getState().getSection().getLabel()
                    + " yet, so there are no pairs to compare." + aid);
            return;
        }
        statusLabel.setText(pairs.size() + " pair"
                + Labels.plural(pairs.size()) + " at or above "
                + pairs.thresholdText() + " in run " + run.getId() + "."
                + aid);
    }

    // Shows the chosen pair: its decision, files, excerpts and left-out
    // files, or why there is nothing to compare.
    private void refreshDetail() {
        SimilarityPair pair = pairs.getSelected();
        decisionHolder.getChildren().clear();
        missingHolder.getChildren().clear();
        comparison.clear();
        if (pair == null) {
            pairTitle.setText("No pair selected");
            pairSummary.setText("Select a pair to read both files.");
            show(compareBox, false);
            undoButton.setDisable(true);
            reviewButton.setDisable(true);
            return;
        }

        ReviewDecision decision = pairs.decisionOf(pair);
        pairTitle.setText(SimilarityPairList.nameOf(pair));
        pairSummary.setText(pair.getQuestionId() + ", "
                + Labels.formatPercent(pair.getSimilarity()) + " similar. "
                + "Listed because it is at or above " + pairs.thresholdText()
                + ".");
        decisionHolder.getChildren().add(StatusBadge.forDecision(decision));
        boolean checking = checkingRun != null;
        undoButton.setDisable(checking || decisions.undoable(
                pairs.flagsOf(pair)).isEmpty());
        reviewButton.setDisable(checking
                || decision == ReviewDecision.REVIEWED);

        SimilarityDetail detail = getBackend().getSimilarityDetail(pair);
        show(compareBox, detail != null);
        if (detail == null) {
            missingHolder.getChildren().add(
                    SimilarityComparison.missingPanel(getBackend()));
            return;
        }
        comparison.show(pair, detail);
    }

    // Opens one student's evidence.
    private void openEvidence(String username) {
        getState().setSelectedUsername(username);
        getState().setEvidenceTab("");
        getState().setEvidenceQuestionId("");
        lastHint = username;
        getShell().navigate(Screen.STUDENT_REPORT);
    }

    // Shows or hides a node and frees its space.
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
