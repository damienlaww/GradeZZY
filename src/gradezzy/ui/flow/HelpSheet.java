/*
 * File: HelpSheet.java
 *
 * Purpose: A short sheet that explains the five tabs (with the four
 * steps of Marking), the keyboard shortcuts and where files are saved,
 * and tells a first-time user where to start.
 *
 * Where it fits: Opened from Help in the top bar, over any screen.
 * Replaces the old Help screen. The folders are read from the settings.
 */
package gradezzy.ui.flow;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.ui.backend.DashboardBackend;

/**
 * Builds the Help sheet.
 *
 * <p>Tier: COULD.
 */
public final class HelpSheet {

    // The width of the name column of the tabs table
    private static final double TAB_NAME_WIDTH = 96.0;

    // The width of the text column of the tabs table
    private static final double TAB_TEXT_WIDTH = 270.0;

    // The width of the name column of the keys and files tables
    private static final double KEY_NAME_WIDTH = 150.0;

    // The width of the text column of the keys and files tables
    private static final double KEY_TEXT_WIDTH = 200.0;

    // The gap between the two halves of the sheet
    private static final double HALF_GAP = 32.0;

    // The gap between the two columns of a table
    private static final double COLUMN_GAP = 12.0;

    // The tabs and what each one is for; a leading space marks a step
    private static final String[][] TABS = {
        {"Home", "Every section at a glance, and what to do next in "
            + "each. Choose or add assessments here."},
        {"Marking", "The four steps for one section."},
        {" 1  Set up", "Choose the section and assessment, and add the "
            + "zip files."},
        {" 2  Grade", "Each submission is checked, compiled and "
            + "tested."},
        {" 3  Review", "Approve or undo fixes, check flags and change "
            + "marks."},
        {" 4  Export", "The score sheet as provided, and a JSON file."},
        {"Statistics", "Average, spread, pass rate and each question."},
        {"Runs", "Earlier runs, backups and the activity log."},
        {"Settings", "Everything in config.properties."},
    };

    // The keyboard shortcuts that FlowWindow adds
    private static final String[][] KEYS = {
        {"Cmd or Ctrl + Enter", "Press the blue button."},
        {"Cmd or Ctrl + 1 to 4", "Open a step you have reached."},
        {"Esc", "Close this sheet."},
    };

    // Not used: every method is static.
    private HelpSheet() {
    }

    /**
     * Builds the sheet.
     *
     * <p>Tier: COULD.
     *
     * @param window the window that shows it
     * @return the sheet's content
     */
    public static Node create(FlowWindow window) {
        DashboardBackend backend = window.getBackend();
        String[][] files = {
            {"Zip files", "Never changed."},
            {"Working copies", backend.getSetting(ConfigKeys.WORKING_DIR)},
            {"Exports", backend.getSetting(ConfigKeys.OUTPUT_DIR)},
            {"Backups", backend.getSetting(ConfigKeys.BACKUP_DIR)},
            {"Assessments", backend.getSetting(
                    ConfigKeys.ASSESSMENTS_DIR)},
        };
        Label start = Parts.muted("New here? Start on Home: it lists the "
                + "first three steps. Rest the pointer on any \"?\" to see "
                + "what a word means.");
        start.setWrapText(true);
        start.setMaxWidth(TAB_NAME_WIDTH + TAB_TEXT_WIDTH + COLUMN_GAP);
        VBox tabs = new VBox(Parts.GAP, Parts.title("How GradeZZY works"),
                start, table(TABS, TAB_NAME_WIDTH, TAB_TEXT_WIDTH));
        VBox other = new VBox(Parts.GAP, Parts.title("Keyboard"),
                table(KEYS, KEY_NAME_WIDTH, KEY_TEXT_WIDTH),
                Parts.title("Where files are saved"),
                table(files, KEY_NAME_WIDTH, KEY_TEXT_WIDTH));
        HBox halves = new HBox(HALF_GAP, tabs, other);
        Button close = Parts.secondary("Close", () -> window.closeSheet());
        return new VBox(Parts.GAP, halves,
                Parts.row(0, new Node[] {Parts.grow(), close}));
    }

    // Lays out pairs of words in two columns of fixed width.
    private static GridPane table(String[][] rows, double nameWidth,
            double textWidth) {
        GridPane grid = new GridPane();
        grid.setHgap(COLUMN_GAP);
        grid.setVgap(Parts.GAP_SMALL);
        grid.getColumnConstraints().addAll(column(nameWidth),
                column(textWidth));
        for (int i = 0; i < rows.length; i++) {
            Label name = Parts.strong(rows[i][0].trim());
            if (rows[i][0].startsWith(" ")) {
                name.getStyleClass().setAll("label", "help-step");
            }
            Label text = Parts.muted(rows[i][1]);
            text.setWrapText(true);
            text.setMinHeight(Region.USE_PREF_SIZE);
            grid.add(name, 0, i);
            grid.add(text, 1, i);
        }
        return grid;
    }

    // Returns a column of one fixed width.
    private static ColumnConstraints column(double width) {
        ColumnConstraints column = new ColumnConstraints(width);
        column.setMinWidth(width);
        column.setMaxWidth(width);
        return column;
    }
}
