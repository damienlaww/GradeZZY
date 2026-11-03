/*
 * File: SimilaritySheet.java
 *
 * Purpose: A large sheet that shows the matching code of two students
 * side by side, with the files the check left out (starter, tester and
 * provided files), so the instructor can judge a similar pair.
 *
 * Where it fits: Opened by "Compare" in the similar code card on Export,
 * only when the backend supports comparing a pair. Reads the pair
 * through DashboardBackend.getSimilarityDetail.
 */
package gradezzy.ui.flow;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.SimilarityDetail;

/**
 * Builds the sheet that compares a similar pair.
 */
public final class SimilaritySheet {

    // The size of the sheet
    private static final double WIDTH = 880.0;
    private static final double HEIGHT = 520.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // Percentages are shown as whole numbers
    private static final double PERCENT = 100.0;

    // The separator between parts of a line
    private static final String DOT = " · ";

    // Not used: every method is static.
    private SimilaritySheet() {
    }

    /**
     * Builds the sheet for one pair.
     *
     * @param window the window that shows it
     * @param run the run the pair belongs to, for the students' names
     * @param pair the pair
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, GradingRun run,
            SimilarityPair pair) {
        String first = nameOf(run, pair.getFirstUsername());
        String second = nameOf(run, pair.getSecondUsername());
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Label title = Parts.title(first + " and " + second + DOT
                + pair.getQuestionId());
        Node grow = Parts.grow();
        Label similar = Parts.pill(Math.round(pair.getSimilarity()
                * PERCENT) + "% similar", Tone.WARN);
        HBox top = Parts.row(Parts.GAP,
                new Node[] {title, grow, similar, close});

        Label help = Parts.muted("Similar is not proof of copying. Read "
                + "both before deciding; marks are not changed.");
        VBox body = new VBox(Parts.GAP, top, help);
        body.setPrefSize(WIDTH, HEIGHT);
        body.getStyleClass().add("similar-sheet");

        SimilarityDetail detail = window.getBackend()
                .getSimilarityDetail(pair);
        if (detail == null) {
            body.getChildren().add(Parts.muted("The check returned no "
                    + "files to compare for this pair."));
            return body;
        }
        HBox sides = new HBox(Parts.GAP,
                side(first, detail.getFirstFile(),
                detail.getFirstExcerpt()),
                side(second, detail.getSecondFile(),
                detail.getSecondExcerpt()));
        VBox.setVgrow(sides, Priority.ALWAYS);
        body.getChildren().addAll(sides, leftOut(detail));
        return body;
    }

    /**
     * Returns a student's name from a run, or the username when the
     * student is not in it.
     *
     * @param run the run, or null
     * @param username the username
     * @return the name
     */
    public static String nameOf(GradingRun run, String username) {
        if (run == null) {
            return username;
        }
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return Students.name(submission);
            }
        }
        return username;
    }

    // Builds one side: the student, the file and the matching code.
    private static VBox side(String name, String file, String excerpt) {
        TextArea code = new TextArea(excerpt);
        code.setEditable(false);
        code.getStyleClass().add("code-area");
        VBox.setVgrow(code, Priority.ALWAYS);
        Label fileLabel = Parts.muted(file);
        fileLabel.getStyleClass().add("small-text");
        VBox box = new VBox(Parts.GAP_SMALL / 2, Parts.strong(name),
                fileLabel, code);
        box.setMinWidth(0);
        box.setPrefWidth(0);
        HBox.setHgrow(box, Priority.ALWAYS);
        VBox.setMargin(code, new Insets(Parts.GAP_SMALL / 2, 0, 0, 0));
        return box;
    }

    // Lists the files the check left out.
    private static VBox leftOut(SimilarityDetail detail) {
        VBox box = new VBox(Parts.GAP_SMALL / 2);
        Label title = Parts.strong("Left out of the comparison");
        box.getChildren().add(title);
        if (detail.getExcludedFiles().isEmpty()) {
            box.getChildren().add(Parts.muted("The check listed no "
                    + "starter or tester files for this pair."));
            return box;
        }
        for (String file : detail.getExcludedFiles()) {
            Label line = Parts.muted(file);
            line.getStyleClass().add("small-text");
            box.getChildren().add(line);
        }
        return box;
    }
}
