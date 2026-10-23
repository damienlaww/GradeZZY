/*
 * File: ImportRows.java
 *
 * Purpose: Draws the file rows and the ZIP summary on the Submissions screen.
 *
 * Where it fits: Used by UploadController. A later ZIP file for the same
 * student is shown as a duplicate.
 */
package gradezzy.ui.view;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * The rows of the Submissions screen.
 */
public final class ImportRows {

    // The space between the icon, the text and the state of a row
    private static final double ROW_GAP = 12.0;

    // The space between a file name and its explanation
    private static final double TEXT_GAP = 4.0;

    // Utility class, never instantiated.
    private ImportRows() {
        // nothing to set up
    }

    /**
     * Builds the row of one chosen file.
     *
     * @param item the file
     * @param state the state to show, which may be DUPLICATE
     * @return the row
     */
    public static Node describe(ImportItem item, ImportState state) {
        Label name = new Label(item.getName());
        name.getStyleClass().add("mono");
        if (item.getPath() != null) {
            name.setTooltip(new Tooltip(item.getPath().toString()));
        }
        String explanation = item.getMessage();
        if (state == ImportState.DUPLICATE) {
            explanation = "Another file for " + item.getUsername()
                    + " is already in the batch. Keep one per student.";
        }
        Label message = new Label(explanation);
        message.getStyleClass().add("secondary-text");
        message.setWrapText(true);
        VBox text = new VBox(TEXT_GAP, name, message);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(ROW_GAP, Icon.FILE.create(), text,
                StatusBadge.forImport(state));
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row, Priority.ALWAYS);
        return row;
    }

    /**
     * Shows the chosen file of a single-file group, or that none is set.
     *
     * @param row the row to fill
     * @param kind the roster, tester or starter group
     * @param batch the chosen files
     */
    public static void showChosen(HBox row, InputKind kind,
            ImportBatch batch) {
        row.getChildren().clear();
        List<ImportItem> chosen = batch.getItems(kind);
        if (!chosen.isEmpty()) {
            ImportItem item = chosen.get(0);
            row.getChildren().add(describe(item, item.getState()));
        } else if (kind.isRequired()) {
            row.getChildren().add(new StatusBadge("Not added yet. "
                    + "Required before grading.", Tone.WARNING));
        } else {
            row.getChildren().add(new StatusBadge("Not added yet.",
                    Tone.NEUTRAL));
        }
    }

    /**
     * Shows that typed test cases replace the tester folder.
     *
     * @param row the tester row to fill
     * @param batch the chosen files and typed test cases
     */
    public static void showTypedTesters(HBox row, ImportBatch batch) {
        row.getChildren().clear();
        int count = batch.getTypedTestCases().size();
        row.getChildren().add(new StatusBadge("Using " + count
                + " typed test case" + Labels.plural(count)
                + " from the Assessments screen.", Tone.INFO));
    }

    /**
     * Describes the chosen ZIP files in one sentence.
     *
     * @param batch the chosen files
     * @return the summary
     */
    public static String summarise(ImportBatch batch) {
        int total = batch.getItems(InputKind.SUBMISSION).size();
        if (total == 0) {
            return "No ZIP files added yet.";
        }
        int accepted = batch.count(InputKind.SUBMISSION, ImportState.ACCEPTED)
                + batch.count(InputKind.SUBMISSION, ImportState.WARNING);
        int invalid = batch.count(InputKind.SUBMISSION, ImportState.INVALID);
        int duplicates = batch.count(InputKind.SUBMISSION,
                ImportState.DUPLICATE);
        return total + " added: " + accepted + " ready, " + invalid
                + " cannot be used, " + duplicates + " duplicate"
                + Labels.plural(duplicates) + ".";
    }

    /**
     * A row of the ZIP file list: name, explanation and state.
     */
    public static final class ImportCell extends ListCell<ImportItem> {

        // The batch that decides duplicates
        private final ImportBatch batch;

        /**
         * Creates a cell.
         *
         * @param batch the batch that decides duplicates
         */
        public ImportCell(ImportBatch batch) {
            this.batch = batch;
            // Zero preferred width wraps long text, keeping the badge in view.
            setPrefWidth(0.0);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        /**
         * Shows one chosen file.
         *
         * @param item the file
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(ImportItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            ImportState state = batch.getEffectiveState(item);
            setGraphic(new HBox(describe(item, state)));
            setAccessibleText(item.getName() + ", " + state.getLabel());
        }
    }
}
