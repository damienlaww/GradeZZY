/*
 * File: HelpSheet.java
 *
 * Purpose: A short sheet that explains the four steps, the keyboard
 * shortcuts and where files are saved.
 *
 * Where it fits: Opened from Help in the top bar, over any screen.
 * Replaces the old Help screen.
 */
package gradezzy.ui.flow;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Builds the Help sheet.
 */
public final class HelpSheet {

    // The width of the sheet
    private static final double WIDTH = 520.0;

    // The four steps and what each one does
    private static final String[][] STEPS = {
        {"1  Set up", "Choose the module and section, drop the zip files. "
            + "The score sheet and tester files come from Settings."},
        {"2  Grade", "Every submission is unzipped, checked, compiled and "
            + "tested. Cancel keeps what is done; Resume finishes it."},
        {"3  Review", "Approve or undo each fix, judge each flag, open a "
            + "student's code. Marks only change when you decide."},
        {"4  Export", "Save the score sheet in the provided format, plus "
            + "a JSON file with every question and test."},
    };

    // The keyboard shortcuts
    private static final String[][] KEYS = {
        {"Ctrl or Cmd + Enter", "Press the blue button"},
        {"Ctrl or Cmd + 1 to 4", "Go to a step you have reached"},
        {"Esc", "Close a sheet, History or Settings"},
    };

    // Not used: every method is static.
    private HelpSheet() {
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @return the sheet's content
     */
    public static Node create(FlowWindow window) {
        VBox body = new VBox(Parts.GAP, Parts.title("How GradeZZY works"),
                table(STEPS), Parts.title("Keyboard"), table(KEYS),
                Parts.muted("Original zip files are never changed. "
                + "Fixes happen in a copy under workspace/working, and "
                + "exports are saved in workspace/output."),
                Parts.row(0, Parts.grow(), Parts.secondary("Close",
                () -> window.closeSheet())));
        body.setPrefWidth(WIDTH);
        return body;
    }

    // Lays out pairs of words in two columns.
    private static GridPane table(String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(Parts.GAP + Parts.GAP);
        grid.setVgap(Parts.GAP_SMALL);
        for (int i = 0; i < rows.length; i++) {
            Label name = Parts.strong(rows[i][0]);
            name.setMinWidth(Label.USE_PREF_SIZE);
            Label text = Parts.muted(rows[i][1]);
            text.setWrapText(true);
            grid.add(name, 0, i);
            grid.add(text, 1, i);
        }
        return grid;
    }
}
