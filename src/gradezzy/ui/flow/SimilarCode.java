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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
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

    // How far Previous and Next move, in parts
    private static final int PART_STEP = 1;

    // Line numbers start at 1 while list indexes start at 0
    private static final int FIRST_LINE_NUMBER = 1;

    // The style class of a line that is the same in both files, and of
    // its key
    private static final String SAME_STYLE = "same";

    // The style class of a starter line, and of its key
    private static final String STARTER_STYLE = "starter";

    // No width. Both panes start with it and grow by the same amount, so
    // they share the width evenly.
    private static final double NO_WIDTH = 0.0;

    // A view order that draws a node over the others in its row
    private static final double IN_FRONT = -1.0;

    // The comparison of the two files
    private final LineComparison comparison;

    // The lines of the first file, and the pane it scrolls in
    private final VBox firstColumn;
    private final ScrollPane firstPane;

    // Both panes, side by side
    private final HBox view = new HBox();

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
        int rowCount = Math.max(comparison.getFirstLines().size(),
                comparison.getSecondLines().size());

        DoubleProperty firstOffset = new SimpleDoubleProperty();
        firstColumn = column(comparison.getFirstLines(),
                comparison.getFirstKinds(), parts, rowCount, firstOffset);
        firstPane = pane(firstColumn, firstOffset);

        DoubleProperty secondOffset = new SimpleDoubleProperty();
        VBox secondColumn = column(comparison.getSecondLines(),
                comparison.getSecondKinds(), new ArrayList<>(), rowCount,
                secondOffset);
        ScrollPane secondPane = pane(secondColumn, secondOffset);
        secondPane.getStyleClass().add("similar-column-right");
        // Only the second pane shows the up and down bar
        secondPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Scrolling one pane up or down scrolls the other with it
        secondPane.vvalueProperty().bindBidirectional(
                firstPane.vvalueProperty());

        view.getChildren().addAll(firstPane, secondPane);
        view.getStyleClass().add("similar-code");
        VBox.setVgrow(view, Priority.ALWAYS);

        previous = Parts.small("Previous", () -> showPreviousPart());
        next = Parts.small("Next same part", () -> showNextPart());
        updateButtons();
    }

    /**
     * Returns the files, scrolling together.
     *
     * @return the row of both panes
     */
    public HBox getView() {
        return view;
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
                key(SAME_STYLE, "Same line in both files"),
                key(STARTER_STYLE, "Starter code, not compared"),
                Parts.grow(), count, previous, next});
    }

    /**
     * Returns how many of a side's own lines are the same, such as
     * "9 of 11 own lines the same". Lines of brackets only are not
     * counted.
     *
     * @param firstSide true for the first student, false for the second
     * @return the text
     */
    public String describeSide(boolean firstSide) {
        List<String> lines = comparison.getSecondLines();
        List<LineKind> kinds = comparison.getSecondKinds();
        if (firstSide) {
            lines = comparison.getFirstLines();
            kinds = comparison.getFirstKinds();
        }
        int same = LineComparison.countCode(lines, kinds, LineKind.SAME);
        int own = same + LineComparison.countCode(lines, kinds,
                LineKind.OWN);
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
        if (!hasNextPart()) {
            return;
        }
        currentPart += PART_STEP;
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
        currentPart -= PART_STEP;
        if (currentPart == NO_PART) {
            firstPane.setVvalue(TOP);
        } else {
            scrollTo(partStarts.get(currentPart));
        }
        updateButtons();
    }

    // Builds one file's column, padded with blank rows to rowCount rows.
    // The lines at the indexes in parts are kept as the starts of the
    // parts that are the same.
    private VBox column(List<String> lines, List<LineKind> kinds,
            List<Integer> parts, int rowCount, DoubleProperty offset) {
        VBox box = new VBox();
        box.getStyleClass().add("similar-column");
        // Never narrower than its longest line, so no line is cut short
        box.setMinWidth(Region.USE_PREF_SIZE);
        for (int index = 0; index < lines.size(); index++) {
            int number = index + FIRST_LINE_NUMBER;
            HBox line = line(number, lines.get(index), kinds.get(index),
                    offset);
            if (parts.contains(index)) {
                partStarts.add(line);
            }
            box.getChildren().add(line);
        }

        // Both columns get the same number of rows, so they are the same
        // height and scroll together line for line
        for (int index = lines.size(); index < rowCount; index++) {
            box.getChildren().add(blankLine());
        }
        return box;
    }

    // Puts a column in its own pane, which scrolls sideways when a line
    // is too long, and keeps the column's line numbers at its left edge.
    private ScrollPane pane(VBox column, DoubleProperty offset) {
        ScrollPane pane = new ScrollPane(column);
        pane.getStyleClass().add("similar-pane");
        // The column fills the pane, or is wider when a line is longer
        pane.setFitToWidth(true);
        // Both panes always show the sideways bar, so both show the same
        // height of code and stay in line when they scroll
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setMinWidth(NO_WIDTH);
        pane.setPrefWidth(NO_WIDTH);
        HBox.setHgrow(pane, Priority.ALWAYS);
        // Worked out again when the bar moves or a width changes
        pane.hvalueProperty().addListener((seen, old, value) ->
                pinNumbers(pane, column, offset));
        pane.viewportBoundsProperty().addListener((seen, old, bounds) ->
                pinNumbers(pane, column, offset));
        column.widthProperty().addListener((seen, old, width) ->
                pinNumbers(pane, column, offset));
        return pane;
    }

    // Sets offset to how far the code has scrolled left: the bar's
    // position, from 0 to 1, times the width hidden off the pane. The
    // numbers move right by that much, so they stay in place.
    private void pinNumbers(ScrollPane pane, VBox column,
            DoubleProperty offset) {
        double hiddenWidth = column.getWidth()
                - pane.getViewportBounds().getWidth();
        offset.set(pane.getHvalue() * Math.max(NO_WIDTH, hiddenWidth));
    }

    // Builds one line: its number, its code and, for starter code, a tag.
    // The number moves sideways by offset, so it stays in view.
    private HBox line(int number, String code, LineKind kind,
            DoubleProperty offset) {
        Label numberLabel = new Label(String.valueOf(number));
        numberLabel.getStyleClass().add("line-number");
        numberLabel.translateXProperty().bind(offset);
        // Drawn over the code that scrolls under it
        numberLabel.setViewOrder(IN_FRONT);

        String text = code.replace(TAB, TAB_SPACES);
        if (text.isEmpty()) {
            text = EMPTY_LINE;
        }
        Label codeLabel = new Label(text);
        codeLabel.getStyleClass().add("line-code");
        // Shown in full on one line, never cut short with "..."
        codeLabel.setMinWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(numberLabel, codeLabel);
        row.getStyleClass().add("code-line");
        if (kind == LineKind.SAME) {
            row.getStyleClass().add(SAME_STYLE);
        } else if (kind == LineKind.STARTER) {
            row.getStyleClass().add(STARTER_STYLE);
            Label tag = new Label("starter");
            tag.getStyleClass().add("starter-tag");
            tag.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(tag);
        }
        return row;
    }

    // Builds an empty row with no number, to pad the shorter file.
    private HBox blankLine() {
        Label blank = new Label(EMPTY_LINE);
        blank.getStyleClass().add("line-code");
        HBox row = new HBox(blank);
        row.getStyleClass().add("code-line");
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
    // go. The second pane follows the first.
    private void scrollTo(Node line) {
        double contentHeight = firstColumn.getBoundsInLocal().getHeight();
        double viewHeight = firstPane.getViewportBounds().getHeight();
        if (contentHeight <= viewHeight) {
            return;
        }
        Bounds inScene = line.localToScene(line.getBoundsInLocal());
        Bounds inContent = firstColumn.sceneToLocal(inScene);
        double lineTop = inContent.getMinY() - Parts.GAP_SMALL;
        double scrollRange = contentHeight - viewHeight;
        double value = lineTop / scrollRange;
        double belowBottom = Math.min(BOTTOM, value);
        firstPane.setVvalue(Math.max(TOP, belowBottom));
    }

    // Says whether there is a part after the one last jumped to.
    private boolean hasNextPart() {
        int nextPart = currentPart + PART_STEP;
        return nextPart < partStarts.size();
    }

    // Turns off a button when there is nowhere for it to go.
    private void updateButtons() {
        previous.setDisable(currentPart == NO_PART);
        next.setDisable(!hasNextPart());
    }
}
