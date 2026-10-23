/*
 * File: EvidenceRecordsTab.java
 *
 * Purpose: Fills the Repairs, History and Anomalies tabs of Student
 * Evidence.
 *
 * Where it fits: StudentReportController creates one and calls fill each
 * time the screen is shown. Each list explains itself when it is empty.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ScoreAdjustment;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;

/**
 * The Repairs, History and Anomalies tabs of the Student Evidence screen.
 */
public final class EvidenceRecordsTab {

    // The gap between the parts of a row built in code
    private static final double ROW_GAP = 8.0;

    // The gap between the lines of a card built in code
    private static final double LINE_GAP = 8.0;

    // The narrowest a time column may get
    private static final double TIME_WIDTH = 140.0;

    // The narrowest the question column may get
    private static final double QUESTION_WIDTH = 76.0;

    // The narrowest a short number column may get
    private static final double NUMBER_WIDTH = 56.0;

    // The narrowest a column of sentences may get
    private static final double TEXT_WIDTH = 200.0;

    // The backend, for repairs, changes, audit entries and decisions
    private final DashboardBackend backend;

    // Holds one card per repair
    private final VBox repairRows;

    // Holds the two History tables
    private final VBox historyContent;

    // The manual mark changes
    private final TableView<ScoreAdjustment> adjustmentTable;

    // The title above the audit entries
    private final Label auditTitle;

    // The audit entries about the student
    private final TableView<AuditEntry> auditTable;

    // Replaces the History tables when both are empty
    private final VBox historyEmptyHolder;

    // The sentence above the anomaly cards
    private final Label anomalySummary;

    // Holds one card per anomaly
    private final VBox anomalyRows;

    /**
     * Creates the tabs over the nodes they fill.
     *
     * @param backend the backend, for the records shown
     * @param repairRows holds one card per repair
     * @param historyContent holds the two History tables
     * @param adjustmentTable the manual mark changes
     * @param auditTitle the title above the audit entries
     * @param auditTable the audit entries about the student
     * @param historyEmptyHolder shown when both History tables are empty
     * @param anomalySummary the sentence above the anomaly cards
     * @param anomalyRows holds one card per anomaly
     */
    public EvidenceRecordsTab(DashboardBackend backend, VBox repairRows,
            VBox historyContent, TableView<ScoreAdjustment> adjustmentTable,
            Label auditTitle, TableView<AuditEntry> auditTable,
            VBox historyEmptyHolder, Label anomalySummary,
            VBox anomalyRows) {
        this.backend = backend;
        this.repairRows = repairRows;
        this.historyContent = historyContent;
        this.adjustmentTable = adjustmentTable;
        this.auditTitle = auditTitle;
        this.auditTable = auditTable;
        this.historyEmptyHolder = historyEmptyHolder;
        this.anomalySummary = anomalySummary;
        this.anomalyRows = anomalyRows;
        buildAdjustmentColumns();
        buildAuditColumns();
    }

    /**
     * Shows the repairs, history and anomalies of one student.
     *
     * @param run the run the student is in
     * @param shown the student
     */
    public void fill(GradingRun run, Submission shown) {
        showRepairs(run, shown);
        showHistory(shown);
        showAnomalies(run, shown);
    }

    // Lists this student's repairs.
    private void showRepairs(GradingRun run, Submission shown) {
        repairRows.getChildren().clear();
        for (RepairItem repair : backend.listRepairs(run)) {
            if (repair.getAnomaly().getUsername().equals(
                    shown.getUsername())) {
                repairRows.getChildren().add(repairRow(repair));
            }
        }
        if (repairRows.getChildren().isEmpty()) {
            repairRows.getChildren().add(new EmptyState(Icon.VALIDATION,
                    "No repairs for this student", "Validation found "
                    + "nothing to repair in the structure, file names or "
                    + "headers of this submission."));
        }
    }

    // Builds the card of one repair.
    private VBox repairRow(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        HBox top = cardTop(anomaly, StatusBadge.forFix(
                anomaly.getFixStatus()));

        GridPane values = new GridPane();
        values.getStyleClass().add("detail-grid");
        ColumnConstraints names = new ColumnConstraints();
        ColumnConstraints texts = new ColumnConstraints();
        texts.setHgrow(Priority.ALWAYS);
        values.getColumnConstraints().setAll(names, texts);
        addDetail(values, 0, "Submitted", repair.getOriginalValue());
        addDetail(values, 1, "Working copy", repair.getProposedValue());

        Label message = new Label(anomaly.getMessage());
        message.getStyleClass().add("secondary-text");
        message.setWrapText(true);
        VBox row = new VBox(LINE_GAP, top, values, message);
        row.getStyleClass().add("panel");
        return row;
    }

    // Adds one labelled value to a grid of details.
    private void addDetail(GridPane grid, int row, String name,
            String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("detail-name");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("mono");
        valueLabel.setWrapText(true);
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    // Lists the mark changes and the audit entries that mention the student.
    private void showHistory(Submission shown) {
        String username = shown.getUsername();
        List<ScoreAdjustment> changes = backend.listAdjustments(shown);
        List<AuditEntry> entries = new ArrayList<>();
        for (AuditEntry entry : backend.listAuditEntries()) {
            String detail = entry.getDetail();
            if (detail != null && detail.contains(username)) {
                entries.add(entry);
            }
        }

        adjustmentTable.setItems(FXCollections.observableArrayList(changes));
        adjustmentTable.setPlaceholder(new Label("No mark was changed by "
                + "hand."));
        auditTable.setItems(FXCollections.observableArrayList(entries));
        auditTable.setPlaceholder(new Label("No audit entry mentions "
                + username + "."));
        auditTitle.setText("Audit entries about " + username);

        boolean empty = changes.isEmpty() && entries.isEmpty();
        show(historyContent, !empty);
        show(historyEmptyHolder, empty);
        historyEmptyHolder.getChildren().clear();
        if (empty) {
            historyEmptyHolder.getChildren().add(new EmptyState(Icon.HISTORY,
                    "No history for this student yet", "Retries, mark "
                    + "changes and exports for " + username + " are listed "
                    + "here as they happen."));
        }
    }

    // Lists this student's anomalies with their severity and decision.
    private void showAnomalies(GradingRun run, Submission shown) {
        anomalyRows.getChildren().clear();
        int count = 0;
        int waiting = 0;
        for (Anomaly anomaly : backend.listAnomalies(run)) {
            if (anomaly.getUsername().equals(shown.getUsername())) {
                anomalyRows.getChildren().add(anomalyRow(anomaly));
                count++;
                if (backend.needsReview(anomaly)) {
                    waiting++;
                }
            }
        }

        if (count == 0) {
            anomalySummary.setText("Nothing was flagged for this student.");
            anomalyRows.getChildren().add(new EmptyState(Icon.ANOMALIES,
                    "No anomalies for this student", "Nothing in this "
                    + "submission was flagged for review."));
            return;
        }
        anomalySummary.setText(count + " flag" + Labels.plural(count)
                + ", " + waiting + " waiting for review. Flags request a "
                + "review and never change a mark.");
    }

    // Builds the card of one anomaly.
    private VBox anomalyRow(Anomaly anomaly) {
        HBox top = cardTop(anomaly, StatusBadge.forSeverity(
                anomaly.getSeverity()));
        top.getChildren().add(StatusBadge.forDecision(
                backend.getDecision(anomaly)));

        Label message = new Label(anomaly.getMessage());
        message.setWrapText(true);
        Label action = new Label("Next step: "
                + anomaly.getSuggestedAction());
        action.getStyleClass().add("secondary-text");
        action.setWrapText(true);
        VBox row = new VBox(LINE_GAP, top, message, action);
        row.getStyleClass().add("panel");
        return row;
    }

    // Builds the first line of a card: the anomaly's title and a badge.
    private HBox cardTop(Anomaly anomaly, Node badge) {
        Label title = new Label(Labels.anomalyTitle(anomaly.getType()));
        title.getStyleClass().add("context-value");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox top = new HBox(ROW_GAP, title, badge);
        top.setAlignment(Pos.CENTER_LEFT);
        return top;
    }

    // Adds the Time, Question, From, To and Reason columns.
    private void buildAdjustmentColumns() {
        TableColumn<ScoreAdjustment, String> time = new TableColumn<>(
                "Time");
        time.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                Labels.formatDateTime(data.getValue().getSavedAt())));
        time.setMinWidth(TIME_WIDTH);
        TableColumn<ScoreAdjustment, String> question = new TableColumn<>(
                "Question");
        question.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getQuestionId()));
        question.setMinWidth(QUESTION_WIDTH);
        TableColumn<ScoreAdjustment, String> from = new TableColumn<>(
                "From");
        from.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                Labels.formatScore(data.getValue().getPreviousScore())));
        from.setMinWidth(NUMBER_WIDTH);
        TableColumn<ScoreAdjustment, String> to = new TableColumn<>("To");
        to.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                Labels.formatScore(data.getValue().getNewScore())));
        to.setMinWidth(NUMBER_WIDTH);
        TableColumn<ScoreAdjustment, String> reason = new TableColumn<>(
                "Reason");
        reason.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getReason()));
        reason.setMinWidth(TEXT_WIDTH);
        adjustmentTable.getColumns().add(time);
        adjustmentTable.getColumns().add(question);
        adjustmentTable.getColumns().add(from);
        adjustmentTable.getColumns().add(to);
        adjustmentTable.getColumns().add(reason);
        adjustmentTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    // Adds the Time, Action and Detail columns.
    private void buildAuditColumns() {
        TableColumn<AuditEntry, String> when = new TableColumn<>("Time");
        when.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                Labels.formatDateTime(data.getValue().getTimestamp())));
        when.setMinWidth(TIME_WIDTH);
        TableColumn<AuditEntry, String> action = new TableColumn<>(
                "Action");
        action.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getAction()));
        action.setMinWidth(TIME_WIDTH);
        TableColumn<AuditEntry, String> detail = new TableColumn<>(
                "Detail");
        detail.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getDetail()));
        detail.setMinWidth(TEXT_WIDTH);
        auditTable.getColumns().add(when);
        auditTable.getColumns().add(action);
        auditTable.getColumns().add(detail);
        auditTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    // Shows or hides a node and frees its space.
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
