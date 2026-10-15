/*
 * File: AuditLogController.java
 *
 * Purpose: Controller of the Audit Log screen.
 *
 * Where it fits: Lists every recorded instructor action and automatic
 * repair, newest first, with a search and an action filter. Other
 * screens add entries, so the list is read again each time the screen is
 * shown. Exporting the log is a proposed backend feature; until it is
 * connected, the export shows a preview and writes no file.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.AuditEntry;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;

/**
 * Controller of the audit log screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class AuditLogController extends BaseController {

    // The narrowest the time column may get
    private static final double TIME_WIDTH = 100.0;

    // The narrowest the action column may get
    private static final double ACTION_WIDTH = 150.0;

    // The narrowest the detail column may get
    private static final double DETAIL_WIDTH = 220.0;

    // The width of a cell taken by its left and right padding
    private static final double CELL_PADDING = 24.0;

    // The space above and below the text of a row
    private static final double ROW_PADDING = 16.0;

    // The lowest a row may be
    private static final double MIN_ROW_HEIGHT = 44.0;

    // The gap between the time and the date in the time column
    private static final double LINE_GAP = 0.0;

    // The words shown for every action in the action filter
    private static final String ALL_ACTIONS = "All actions";

    // How the date is written under the time of an entry
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy");

    // How times are written in the CSV preview
    private static final DateTimeFormatter CSV_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // The first line of the CSV preview
    private static final String CSV_HEADER = "Time,Action,Detail";

    @FXML
    private Label statusLabel;

    @FXML
    private TextField searchField;

    @FXML
    private Label actionLabel;

    @FXML
    private ComboBox<String> actionFilter;

    @FXML
    private Button clearButton;

    @FXML
    private TableView<AuditEntry> table;

    // The entries shown, after search and filter
    private final List<AuditEntry> shown = new ArrayList<>();

    // The search text, kept while the instructor visits other screens
    private String query = "";

    // The chosen action, or ALL_ACTIONS
    private String actionChoice = ALL_ACTIONS;

    // Whether the filter controls are being filled in by code
    private boolean restoring = false;

    /**
     * Creates a new audit log controller.
     */
    public AuditLogController() {
        // nothing to set up yet
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        actionLabel.setLabelFor(actionFilter);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        buildColumns();

        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        query = newValue;
                        refresh();
                    }
                });
        actionFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring && newValue != null) {
                        actionChoice = newValue;
                        refresh();
                    }
                });
    }

    /**
     * Reads the audit log again each time the screen is shown, because
     * other screens add entries.
     */
    @Override
    public void onShow() {
        restoring = true;
        searchField.setText(query);
        restoring = false;
        refresh();
    }

    /**
     * Moves keyboard focus to the search field.
     *
     * @return true, because this screen has a search field
     */
    @Override
    public boolean focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
        return true;
    }

    /**
     * Exports the entries shown. Until an audit export exists in the
     * backend, this shows a CSV preview and writes no file.
     */
    @FXML
    public void handleExport() {
        if (!getBackend().supports(Capability.AUDIT_EXPORT)) {
            getShell().showNotConnected(Capability.AUDIT_EXPORT);
            return;
        }
        if (shown.isEmpty()) {
            showError("There are no entries to export. Clear the filters, "
                    + "or wait for actions to be recorded.");
            return;
        }

        int count = shown.size();
        String note = "Previewed " + count + " " + entryWord(count) + ".";
        if (getBackend().isDemo()) {
            note += " Demo mode writes no file.";
        } else {
            note += " No file was written; copy the text to keep it.";
        }
        getShell().showPreview("Audit log preview", note, buildCsv());
        getBackend().recordAudit("Audit log export previewed", "Previewed "
                + count + " " + entryWord(count) + " as CSV.");
        refresh();
    }

    /**
     * Clears the search and the action filter.
     */
    @FXML
    public void handleClearFilters() {
        query = "";
        actionChoice = ALL_ACTIONS;
        onShow();
    }

    // Reloads the entries and applies the search and the action filter.
    private void refresh() {
        List<AuditEntry> all = getBackend().listAuditEntries();
        fillActions(all);

        shown.clear();
        for (AuditEntry entry : all) {
            if (matches(entry)) {
                shown.add(entry);
            }
        }
        table.setItems(FXCollections.observableArrayList(shown));
        table.setPlaceholder(emptyStateFor(all.isEmpty()));
        clearButton.setDisable(!isFiltered());
        statusLabel.setText("Every instructor action and automatic repair, "
                + "newest first. Showing " + shown.size() + " of "
                + all.size() + " " + entryWord(all.size()) + ".");
    }

    // Lists every action in the log as a filter choice, keeping the
    // current choice when it is still there.
    private void fillActions(List<AuditEntry> entries) {
        TreeSet<String> actions = new TreeSet<>();
        for (AuditEntry entry : entries) {
            actions.add(entry.getAction());
        }
        List<String> choices = new ArrayList<>();
        choices.add(ALL_ACTIONS);
        choices.addAll(actions);
        if (!choices.contains(actionChoice)) {
            actionChoice = ALL_ACTIONS;
        }

        restoring = true;
        actionFilter.setItems(FXCollections.observableArrayList(choices));
        actionFilter.setValue(actionChoice);
        restoring = false;
    }

    // Says whether an entry passes the search and the action filter.
    private boolean matches(AuditEntry entry) {
        if (!ALL_ACTIONS.equals(actionChoice)
                && !actionChoice.equals(entry.getAction())) {
            return false;
        }
        String wanted = query.trim().toLowerCase();
        if (wanted.isEmpty()) {
            return true;
        }
        return entry.getAction().toLowerCase().contains(wanted)
                || entry.getDetail().toLowerCase().contains(wanted);
    }

    // Says whether a search or an action filter is in use.
    private boolean isFiltered() {
        return !query.trim().isEmpty() || !ALL_ACTIONS.equals(actionChoice);
    }

    // Builds the empty state that fits why no entry is shown.
    private EmptyState emptyStateFor(boolean logEmpty) {
        if (logEmpty) {
            String message = "Approvals, repairs, retries, mark changes and "
                    + "exports are listed here as they happen.";
            Capability capability = Capability.AUDIT_LOG;
            if (capability.getStatus() != CapabilityStatus.WORKS_NOW) {
                message += " The saved audit log waits for "
                        + capability.getServiceMethod() + " ("
                        + capability.getOwner() + ").";
            }
            EmptyState empty = new EmptyState(Icon.AUDIT,
                    "No actions recorded yet", message);
            empty.setAction("Open Backend Connections",
                    () -> getShell().navigate(Screen.HELP));
            return empty;
        }
        EmptyState empty = new EmptyState(Icon.SEARCH, "No entries match",
                "Change the search or the action filter to see more "
                + "entries.");
        empty.setAction("Clear Filters", () -> handleClearFilters());
        return empty;
    }

    // Writes the entries shown as CSV text.
    private String buildCsv() {
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        csv.append('\n');
        for (AuditEntry entry : shown) {
            csv.append(csvValue(formatCsvTime(entry.getTimestamp())));
            csv.append(',');
            csv.append(csvValue(entry.getAction()));
            csv.append(',');
            csv.append(csvValue(entry.getDetail()));
            csv.append('\n');
        }
        return csv.toString();
    }

    // Quotes a CSV value that holds a comma, a quote or a line break.
    private String csvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // Writes a time for the CSV preview.
    private String formatCsvTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(CSV_TIME);
    }

    // Returns "entry" or "entries" for a count.
    private String entryWord(int count) {
        if (count == 1) {
            return "entry";
        }
        return "entries";
    }

    // Adds the Time, Action and Detail columns.
    private void buildColumns() {
        TableColumn<AuditEntry, AuditEntry> time = new TableColumn<>(
                "Time");
        time.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue()));
        time.setCellFactory(column -> new TimeCell());
        time.setSortable(false);
        time.setMinWidth(TIME_WIDTH);

        TableColumn<AuditEntry, String> action = new TableColumn<>(
                "Action");
        action.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getAction()));
        action.setCellFactory(column -> new WrapCell(column, true));
        action.setMinWidth(ACTION_WIDTH);

        TableColumn<AuditEntry, String> detail = new TableColumn<>(
                "Detail");
        detail.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getDetail()));
        detail.setCellFactory(column -> new WrapCell(column, false));
        detail.setSortable(false);
        detail.setMinWidth(DETAIL_WIDTH);

        table.getColumns().add(time);
        table.getColumns().add(action);
        table.getColumns().add(detail);
    }

    /**
     * Shows the time of an entry with its date under it.
     */
    private static class TimeCell extends TableCell<AuditEntry, AuditEntry> {

        /**
         * Shows one time.
         *
         * @param entry the row's entry
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(AuditEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            setText(null);
            if (empty || entry == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            Label time = new Label(Labels.formatTime(entry.getTimestamp()));
            Label date = new Label("");
            if (entry.getTimestamp() != null) {
                date.setText(entry.getTimestamp().format(DATE));
            }
            date.getStyleClass().add("secondary-text");
            VBox box = new VBox(LINE_GAP, time, date);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
            setAccessibleText(Labels.formatDateTime(entry.getTimestamp()));
        }
    }

    /**
     * Shows text that wraps within its column, so long details stay
     * readable at any window width.
     */
    private static class WrapCell extends TableCell<AuditEntry, String> {

        // The wrapped text
        private final Label label = new Label();

        // The column this cell belongs to
        private final TableColumn<AuditEntry, String> column;

        // Creates a cell; bold text is used for the action name.
        WrapCell(TableColumn<AuditEntry, String> column, boolean bold) {
            this.column = column;
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            if (bold) {
                label.getStyleClass().add("context-value");
            }
        }

        /**
         * Shows one value.
         *
         * @param text the value
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            setText(null);
            if (empty || text == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            label.setText(text);
            setGraphic(label);
            setAccessibleText(text);
        }

        /**
         * Returns the height the wrapped text needs at the column's
         * width.
         *
         * @param width ignored; the column's width is used
         * @return the preferred height
         */
        @Override
        protected double computePrefHeight(double width) {
            if (isEmpty() || getGraphic() == null) {
                return MIN_ROW_HEIGHT;
            }
            double textWidth = column.getWidth() - CELL_PADDING;
            double height = label.prefHeight(textWidth) + ROW_PADDING;
            return Math.max(MIN_ROW_HEIGHT, height);
        }
    }
}
