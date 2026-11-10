/*
 * File: TesterStrengthSheet.java
 *
 * Purpose: A sheet that shows how strong each tester is: for each
 * question, how many broken copies of the model answer its tester still
 * gave full marks, and under it each such copy with the line and the
 * change, so the instructor can see what the tester never checks.
 *
 * Where it fits: Opened from the tester row on Set up once a
 * TesterStrengthCheck has ended.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.modelanswer.Mutant;
import gradezzy.modelanswer.QuestionStrength;

/**
 * Builds the sheet of tester strength results.
 *
 * <p>Tier: COULD.
 */
public final class TesterStrengthSheet {

    // The width of the sheet
    private static final double WIDTH = 680.0;

    // The tallest the list may get before it scrolls
    private static final double LIST_HEIGHT = 380.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The window that shows the sheet
    private final FlowWindow window;

    // The result of each question
    private final List<QuestionStrength> results;

    // Where the report was saved, or why not
    private final String reportMessage;

    // Not used: create builds the sheet.
    private TesterStrengthSheet(FlowWindow window,
            List<QuestionStrength> results, String reportMessage) {
        this.window = window;
        this.results = results;
        this.reportMessage = reportMessage;
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @param results the result of each question
     * @param reportMessage where the report was saved, or why not
     * @return the sheet's content
     */
    public static Node create(FlowWindow window,
            List<QuestionStrength> results, String reportMessage) {
        return new TesterStrengthSheet(window, results, reportMessage)
                .build();
    }

    // Builds the title, the list and the button.
    private Node build() {
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close");
        HBox top = Parts.row(Parts.GAP, new Node[] {
            Parts.title("Tester strength"), Parts.grow(), close});

        Label help = Parts.subtitle("GradeZZY broke the model answer in "
                + "small ways and ran each tester on every broken copy. A "
                + "copy that still gets full marks shows something the "
                + "tester never checks, so a student with that mistake "
                + "would not lose marks. Some changes do not change what "
                + "the code does, so a few full marks can be harmless.");
        help.setMaxWidth(WIDTH);

        VBox list = new VBox();
        list.getStyleClass().add("case-list");
        for (QuestionStrength result : results) {
            VBox row = row(result);
            row.getStyleClass().add("case-row");
            list.getChildren().add(row);
        }
        if (!list.getChildren().isEmpty()) {
            list.getChildren().get(0).getStyleClass().add("first");
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("page-scroll");

        Label saved = smallNote(reportMessage);
        Button done = Parts.primary("Done");
        done.setOnAction(event -> window.closeSheet());
        HBox bottom = Parts.row(Parts.GAP, new Node[] {saved, Parts.grow(),
            done});
        HBox.setHgrow(saved, Priority.ALWAYS);

        VBox body = new VBox(Parts.GAP, top, help, scroll, bottom);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("assessments-sheet");
        return body;
    }

    // Builds one question's row: its summary with a pill, then each
    // broken copy its tester still gave full marks.
    private VBox row(QuestionStrength result) {
        Label summary = Parts.strong(result.summarise());
        summary.setWrapText(true);
        summary.setMinWidth(0);
        HBox.setHgrow(summary, Priority.ALWAYS);
        HBox line = Parts.row(Parts.GAP, new Node[] {summary,
            pillFor(result)});
        VBox row = new VBox(Parts.GAP_TINY, line);
        for (Mutant survivor : result.getSurvivors()) {
            row.getChildren().add(smallNote(survivor.describe()));
        }
        return row;
    }

    // Returns the pill of a question: not checked, every copy caught, or
    // how many copies got full marks.
    private Label pillFor(QuestionStrength result) {
        if (result.countTried() == 0) {
            return Parts.pill("Not checked", Tone.NEUTRAL);
        }
        int survived = result.countSurvived();
        if (survived == 0) {
            return Parts.pill("All caught", Tone.GOOD);
        }
        return Parts.pill(Parts.plural(survived, "gap"), Tone.WARN);
    }

    // Returns a small grey note that wraps.
    private Label smallNote(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        label.setMinWidth(0);
        label.setWrapText(true);
        return label;
    }
}
