/*
 * File: SimilarCode.java
 *
 * Purpose: Shows two students' files side by side with line numbers.
 * Lines that are the same in both files are red, starter code is grey,
 * and Previous and Next jump between the parts that are the same.
 *
 * Where it fits: Built by SimilaritySheet for one similar pair. The kind
 * of each line comes from plagiarism.LineComparison.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.plagiarism.LineComparison;
import gradezzy.plagiarism.LineKind;
import gradezzy.ui.backend.SimilarityDetail;

/**
 * The two files of a similar pair, scrolling together.
 */
public class SimilarCode {

    // A tab in the code is shown as this many spaces
    private static final String TAB_SPACES = "    ";

    // The tab character
    private static final String TAB = "\t";

    // What an empty line shows, so it keeps the height of a line
    private static final String EMPTY_LINE = " ";

    // The scroll position of the top and of the bottom
    private static final double TOP = 0.0;
    private static final double BOTTOM = 1.0;

    // No part has been jumped to yet
    private static final int NO_PART = -1;

    // The comparison of the two files
    private final LineComparison comparison;

    // Both files, side by side
    private final HBox content = new HBox();

    // The scroll pane both files share
    private final ScrollPane scroll = new ScrollPane(content);

    // The first line of each part of the first file that is the same
    private final List<Node> partStarts = new ArrayList<>();

    // The part last jumped to, or NO_PART
    private int currentPart = NO_PART;

    // The buttons that jump between parts
    private final Button previous;
    private final Button next;

    /**
     * Builds the view of a pair.
     *
     * @param detail the pair's files and starter code
     */
    public SimilarCode(SimilarityDetail detail) {
        comparison = new LineComparison(detail.getFirstExcerpt(),
                detail.getSecondExcerpt(), detail.getStarterCode());

        List<Integer> parts = LineComparison.findSameParts(
                comparison.getFirstKinds());
        VBox first = column(comparison.getFirstLines(),
                comparison.getFirstKinds(), parts);
        VBox second = column(comparison.getSecondLines(),
                comparison.getSecondKinds(), new ArrayList<>());
        second.getStyleClass().add("similar-column-right");
        content.getChildren().addAll(first, second);

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("similar-code");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        previous = Parts.small("Previous", () -> showPreviousPart());
        next = Parts.small("Next same part", () -> showNextPart());
        updateButtons();
    }

    /**
     * Returns the files, scrolling together.
     *
     * @return the scroll pane
     */
    public ScrollPane getView() {
        return scroll;
    }

    /**
     * Returns the key to the colours, with the count of parts that are
     * the same and the buttons that jump between them.
     *
     * @return the row
     */
    public HBox createLegend() {
        Label count = Parts.muted(Parts.plural(partStarts.size(),
                "same part"));
        count.getStyleClass().add("small-text");
        if (partStarts.isEmpty()) {
            count.setText("No lines are the same");
        }
        return Parts.row(Parts.GAP, new Node[] {
                key("same", "Same line in both files"),
                key("starter", "Starter code, not compared"),
                Parts.grow(), count, previous, next});
    }

    /**
     * Returns how many of a side's own lines are the same, such as
     * "9 of 11 own lines the same".
     *
     * @param firstSide true for the first student, false for the second
     * @return the text
     */
    public String describeSide(boolean firstSide) {
        List<LineKind> kinds = comparison.getSecondKinds();
        if (firstSide) {
            kinds = comparison.getFirstKinds();
        }
        int same = LineComparison.count(kinds, LineKind.SAME);
        int own = same + LineComparison.count(kinds, LineKind.OWN);
        if (own == 0) {
            return "No own lines";
        }
        return same + " of " + Parts.plural(own, "own line")
                + " the same";
    }

    /**
     * Scrolls to the next part of the first file that is the same.
     */
    public void showNextPart() {
        if (currentPart + 1 >= partStarts.size()) {
            return;
        }
        currentPart++;
        scrollTo(partStarts.get(currentPart));
        updateButtons();
    }

    /**
     * Scrolls to the part before, or to the top from the first part.
     */
    public void showPreviousPart() {
        if (currentPart == NO_PART) {
            return;
        }
        currentPart--;
        if (currentPart == NO_PART) {
            scroll.setVvalue(TOP);
        } else {
            scrollTo(partStarts.get(currentPart));
        }
        updateButtons();
    }

    // Builds one file's column. The lines at the indexes in parts are
    // kept as the starts of the parts that are the same.
    private VBox column(List<String> lines, List<LineKind> kinds,
            List<Integer> parts) {
        VBox box = new VBox();
        box.getStyleClass().add("similar-column");
        box.setMinWidth(0);
        box.setPrefWidth(0);
        HBox.setHgrow(box, Priority.ALWAYS);
        for (int index = 0; index < lines.size(); index++) {
            HBox line = line(index + 1, lines.get(index), kinds.get(index));
            if (parts.contains(index)) {
                partStarts.add(line);
            }
            box.getChildren().add(line);
        }
        return box;
    }

    // Builds one line: its number, its code and, for starter code, a tag.
    private HBox line(int number, String code, LineKind kind) {
        Label numberLabel = new Label(String.valueOf(number));
        numberLabel.getStyleClass().add("line-number");

        String text = code.replace(TAB, TAB_SPACES);
        if (text.isEmpty()) {
            text = EMPTY_LINE;
        }
        Label codeLabel = new Label(text);
        codeLabel.getStyleClass().add("line-code");
        codeLabel.setWrapText(true);
        codeLabel.setMinHeight(Region.USE_PREF_SIZE);
        codeLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(codeLabel, Priority.ALWAYS);

        HBox row = new HBox(numberLabel, codeLabel);
        row.getStyleClass().add("code-line");
        if (kind == LineKind.SAME) {
            row.getStyleClass().add("same");
        }
        if (kind == LineKind.STARTER) {
            row.getStyleClass().add("starter");
            Label tag = new Label("starter");
            tag.getStyleClass().add("starter-tag");
            tag.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(tag);
        }
        return row;
    }

    // Builds one entry of the key: a small square and what it means.
    private HBox key(String style, String text) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch", style);
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        return Parts.row(Parts.GAP_SMALL, new Node[] {swatch, label});
    }

    // Scrolls so a line is at the top of the view, or as near as it can
    // go.
    private void scrollTo(Node line) {
        double contentHeight = content.getBoundsInLocal().getHeight();
        double viewHeight = scroll.getViewportBounds().getHeight();
        if (contentHeight <= viewHeight) {
            return;
        }
        Bounds bounds = content.sceneToLocal(line.localToScene(
                line.getBoundsInLocal()));
        double value = (bounds.getMinY() - Parts.GAP_SMALL)
                / (contentHeight - viewHeight);
        scroll.setVvalue(Math.max(TOP, Math.min(BOTTOM, value)));
    }

    // Turns off a button when there is nowhere for it to go.
    private void updateButtons() {
        previous.setDisable(currentPart == NO_PART);
        next.setDisable(currentPart + 1 >= partStarts.size());
    }
}
