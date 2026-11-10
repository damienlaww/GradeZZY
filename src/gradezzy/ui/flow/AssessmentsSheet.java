/*
 * File: AssessmentsSheet.java
 *
 * Purpose: A sheet that lists the saved assessments, marks the one in
 * use, and lets the instructor use, edit, copy, delete or add one.
 *
 * Where it fits: Opened from Home, Set up and Settings with
 * FlowWindow.openAssessments. Editing opens AssessmentEditor.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.SavedAssessment;

/**
 * Builds the list of saved assessments.
 *
 * <p>Tier: SHOULD.
 */
public final class AssessmentsSheet {

    // The width of the sheet
    private static final double WIDTH = 640.0;

    // The tallest the list may get before it scrolls
    private static final double LIST_HEIGHT = 360.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The separator between facts on one line
    private static final String DOT = "  ·  ";

    // Text added to the name of a copy
    private static final String COPY_SUFFIX = " (copy)";

    // The window that shows the sheet
    private final FlowWindow window;

    // Not used: create builds the sheet.
    private AssessmentsSheet(FlowWindow window) {
        this.window = window;
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @return the sheet's content
     */
    public static Node create(FlowWindow window) {
        return new AssessmentsSheet(window).build();
    }

    // Builds the title, the list and the buttons.
    private Node build() {
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close");
        HBox top = Parts.row(Parts.GAP, new Node[] {
            Parts.title("Assessments"), Parts.grow(), close});

        Label help = Parts.subtitle("Each assessment is one lab test or "
                + "exam: its questions, marks, tester files and starter "
                + "files. Grading uses the one marked In use. Earlier "
                + "results always keep the questions they were graded "
                + "with.");
        help.setMaxWidth(WIDTH);

        VBox list = new VBox();
        list.getStyleClass().add("case-list");
        List<SavedAssessment> saved = window.getBackend().listAssessments();
        for (SavedAssessment assessment : saved) {
            HBox row = row(assessment);
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

        Button create = Parts.secondary("New assessment",
                () -> window.editAssessment(null));
        Parts.tip(create, "Add the next lab test or exam.");
        Button done = Parts.primary("Done");
        done.setOnAction(event -> window.closeSheet());
        HBox bottom = Parts.row(Parts.GAP, new Node[] {create, Parts.grow(),
            done});

        VBox body = new VBox(Parts.GAP, top, help, scroll, bottom);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("assessments-sheet");
        return body;
    }

    // Builds one row: the name, what it holds and its buttons.
    private HBox row(SavedAssessment assessment) {
        boolean inUse = assessment.getName().equals(activeName());
        HBox nameLine = Parts.row(Parts.GAP_SMALL, new Node[] {
            Parts.strong(assessment.getName())});
        if (inUse) {
            Label pill = Parts.pill("In use", Tone.GOOD);
            Parts.tip(pill, "Set up and grading use this assessment now.");
            nameLine.getChildren().add(pill);
        }
        int questionCount = assessment.getQuestions().size();
        Label facts = Parts.muted(Parts.plural(questionCount, "question")
                + DOT
                + Parts.mark(assessment.getMaxTotal()) + " marks" + DOT
                + "testers in " + assessment.getTestersDirectory());
        facts.getStyleClass().add("small-text");
        facts.setMinWidth(0);
        VBox words = new VBox(nameLine, facts);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);

        HBox buttons = Parts.row(Parts.GAP_SMALL);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (!inUse) {
            Button use = Parts.small("Use", () -> use(assessment));
            use.getStyleClass().add("accent");
            Parts.tip(use, "Grade with " + assessment.getName()
                    + " from now on.");
            buttons.getChildren().add(use);
        }
        Button edit = Parts.small("Edit",
                () -> window.editAssessment(assessment.getName()));
        Parts.tip(edit, "Change the name, questions, marks or folders.");
        Button copy = Parts.small("Duplicate", () -> duplicate(assessment));
        Parts.tip(copy, "Start a new assessment from this one, such as next "
                + "week's lab with the same questions.");
        buttons.getChildren().addAll(edit, copy);
        if (!inUse) {
            Button delete = Parts.small("Delete", () -> delete(assessment));
            Parts.tip(delete, "Remove it from this list. Results graded "
                    + "with it are kept.");
            buttons.getChildren().add(delete);
        }
        return Parts.row(Parts.GAP, new Node[] {words, buttons});
    }

    // Switches to an assessment and shows the list again.
    private void use(SavedAssessment assessment) {
        window.useAssessment(assessment.getName());
        window.openAssessments();
    }

    // Opens the editor on a copy with a new name.
    private void duplicate(SavedAssessment assessment) {
        SavedAssessment copy = assessment.copy();
        copy.setName(assessment.getName() + COPY_SUFFIX);
        window.showSheet(AssessmentEditor.create(window, copy, null));
    }

    // Asks, then deletes an assessment that is not in use.
    private void delete(SavedAssessment assessment) {
        window.confirm("Delete " + assessment.getName() + "?", "It is "
                + "removed from the list of assessments, with the files "
                + "chosen for it on Set up. Results already graded with it "
                + "are kept.", "Delete",
                () -> deleteNow(assessment));
    }

    // Deletes an assessment, with the files chosen for it on Set up, once
    // the instructor has confirmed. Undo puts it back and lists it again.
    private void deleteNow(SavedAssessment assessment) {
        window.deleteAssessment(assessment.getName(),
                () -> window.openAssessments());
        window.openAssessments();
    }

    // Returns the name of the assessment in use.
    private String activeName() {
        if (window.getBackend().getAssessment() == null) {
            return "";
        }
        return window.getBackend().getAssessment().getName();
    }
}
