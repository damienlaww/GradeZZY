/*
 * File: SimilaritySheet.java
 *
 * Purpose: A large sheet that shows the code of two students side by
 * side, with the lines that are the same in red, and the files the check
 * left out (starter, tester and provided files), so the instructor can
 * judge a similar pair.
 *
 * Where it fits: Opened by "Compare" in the similar code card on Export,
 * only when the backend supports comparing a pair. Reads the pair
 * through DashboardBackend.getSimilarityDetail.
 */
package gradezzy.ui.flow;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.SimilarityDetail;
import gradezzy.util.TextUtils;

/**
 * Builds the sheet that compares a similar pair.
 *
 * <p>Tier: COULD.
 */
public final class SimilaritySheet {

    // The size of the sheet
    private static final double WIDTH = 880.0;
    private static final double HEIGHT = 600.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // Percentages are shown as whole numbers
    private static final double PERCENT = 100.0;

    // A pair shown as more than this percent similar has a red pill;
    // at or below it, amber
    private static final long RED_ABOVE_PERCENT = 75;

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
        Label similar = similarityPill(pair);
        HBox top = Parts.row(Parts.GAP,
                new Node[] {title, grow, similar, close});

        Label help = Parts.muted("Similar is not proof of copying. Read "
                + "both before deciding; marks are not changed.");
        VBox body = new VBox(Parts.GAP, top, help);
        body.setPrefSize(WIDTH, HEIGHT);
        body.getStyleClass().add("similar-sheet");

        SimilarityDetail detail = window.getBackend()
                .getSimilarityDetail(run, pair);
        if (detail == null) {
            body.getChildren().add(Parts.muted("The check returned no "
                    + "files to compare for this pair."));
            return body;
        }
        SimilarCode code = new SimilarCode(detail);
        VBox firstSide = side(first, detail.getFirstFile(),
                code.describeSide(true));
        VBox secondSide = side(second, detail.getSecondFile(),
                code.describeSide(false));
        HBox names = new HBox(Parts.GAP, firstSide, secondSide);
        body.getChildren().addAll(code.createLegend(), names,
                code.getView(), leftOut(detail));
        return body;
    }

    /**
     * Returns the pill that shows how similar a pair is, such as
     * "84% similar": red above 75%, amber at 75% or below.
     *
     * @param pair the pair
     * @return the pill
     */
    public static Label similarityPill(SimilarityPair pair) {
        // Two students picked from a cluster that were not a flagged pair
        if (Double.isNaN(pair.getSimilarity())) {
            Label pill = Parts.pill("Not flagged as a pair", Tone.NEUTRAL);
            Parts.tip(pill, "The check linked these two through other "
                    + "students in the cluster, not to each other.");
            return pill;
        }
        long percent = Math.round(pair.getSimilarity() * PERCENT);
        Tone tone = Tone.WARN;
        if (percent > RED_ABOVE_PERCENT) {
            tone = Tone.BAD;
        }
        return Parts.pill(percent + Parts.PERCENT_SIGN + " similar", tone);
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

    // Builds the heading of one side: the student, how many of their
    // lines are the same, and the file.
    private static VBox side(String name, String file, String count) {
        Label countLabel = Parts.muted(count);
        countLabel.getStyleClass().add("small-text");
        HBox top = Parts.row(Parts.GAP, new Node[] {Parts.strong(name),
                Parts.grow(), countLabel});
        Label fileLabel = Parts.muted(file);
        fileLabel.getStyleClass().add("small-text");
        VBox box = new VBox(Parts.GAP_TINY, top, fileLabel);
        box.setMinWidth(0);
        box.setPrefWidth(0);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // Lists the files the check left out, on one line that wraps, so
    // the code keeps as much room as it can.
    private static VBox leftOut(SimilarityDetail detail) {
        Label title = Parts.strong("Left out of the comparison");
        String files = "The check listed no starter or tester files for "
                + "this pair.";
        if (!detail.getExcludedFiles().isEmpty()) {
            files = TextUtils.join(DOT, detail.getExcludedFiles());
        }
        Label list = Parts.muted(files);
        list.getStyleClass().add("small-text");
        list.setWrapText(true);
        list.setMinHeight(Region.USE_PREF_SIZE);
        return new VBox(Parts.GAP_TINY, title, list);
    }
}
