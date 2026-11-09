/*
 * File: SubmissionsSheet.java
 *
 * Purpose: The sheet that lists every chosen submission when Set up has
 * more than it shows on the page: a filter box, then one row per file
 * with its status and Remove link, in a list that scrolls.
 *
 * Where it fits: SetUpScreen opens it from "Show all N files" and gives
 * it the rows to show, again after each change while it is open, so a
 * file removed here leaves the list at once.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A sheet listing every chosen submission, with a filter.
 *
 * <p>Tier: SHOULD.
 */
public class SubmissionsSheet {

    // The width of the sheet, in pixels
    private static final double WIDTH = 760.0;

    // The tallest the list grows before it scrolls, in pixels
    private static final double LIST_HEIGHT = 460.0;

    // The size of the close icon, in pixels
    private static final double CLOSE_SIZE = 16.0;

    // Comes between the title and the file count
    private static final String DOT = "  ·  ";

    // The window the sheet is shown in
    private final FlowWindow window;

    // The title, such as "Submissions · 9 files"
    private final Label title = Parts.title("");

    // Narrows the rows to the files whose name or students match
    private final TextField filter = new TextField();

    // Holds the rows shown
    private final VBox list = new VBox();

    // Shown when the filter matches no file
    private final Label noMatch = Parts.muted("No file matches.");

    // The rows, and the text each one is filtered by
    private final List<Node> rows = new ArrayList<>();
    private final List<String> searchTexts = new ArrayList<>();

    // The sheet's content, once built
    private VBox body = null;

    /**
     * Creates the sheet for a window.
     *
     * @param window the window to show it in
     */
    public SubmissionsSheet(FlowWindow window) {
        this.window = window;
    }

    /**
     * Shows the sheet with the rows given, and puts the cursor in the
     * filter box.
     *
     * @param newRows one row per chosen file
     * @param newSearchTexts the text each row is filtered by, such as its
     *        file name and usernames
     */
    public void show(List<Node> newRows, List<String> newSearchTexts) {
        build();
        setRows(newRows, newSearchTexts);
        window.showSheet(body);
        Platform.runLater(() -> filter.requestFocus());
    }

    /**
     * Says whether the sheet is on screen.
     *
     * @return true while it is shown
     */
    public boolean isShowing() {
        return body != null && body.getScene() != null;
    }

    /**
     * Shows new rows, keeping the filter text, after a file was added or
     * removed while the sheet is open.
     *
     * @param newRows one row per chosen file
     * @param newSearchTexts the text each row is filtered by
     */
    public void setRows(List<Node> newRows, List<String> newSearchTexts) {
        rows.clear();
        rows.addAll(newRows);
        searchTexts.clear();
        searchTexts.addAll(newSearchTexts);

        title.setText("Submissions" + DOT
                + Parts.plural(rows.size(), "file"));
        list.getChildren().setAll(rows);
        list.getChildren().add(noMatch);
        applyFilter();
    }

    // Builds the sheet's parts once.
    private void build() {
        if (body != null) {
            return;
        }

        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close (Esc)");
        HBox top = Parts.row(Parts.GAP, new Node[] {title, Parts.grow(),
            close});

        filter.setPromptText("Filter by file name or username");
        filter.textProperty().addListener((seen, before, now) ->
                applyFilter());

        noMatch.getStyleClass().add("small-text");
        list.getStyleClass().add("file-list");
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(LIST_HEIGHT);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("page-scroll");

        Button done = Parts.primary("Done");
        done.setOnAction(event -> window.closeSheet());
        HBox buttons = Parts.row(Parts.GAP, new Node[] {Parts.grow(),
            done});

        body = new VBox(Parts.GAP, top, filter, scroll, buttons);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("confirm");
    }

    // Shows only the rows whose text holds the filter, ignoring case,
    // and gives the visible rows the hairline rules.
    private void applyFilter() {
        String wanted = filter.getText().trim().toLowerCase(Locale.ROOT);
        List<Node> shown = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Node row = rows.get(index);
            row.getStyleClass().removeAll("first", "last");
            boolean match = wanted.isEmpty() || searchTexts.get(index)
                    .toLowerCase(Locale.ROOT).contains(wanted);
            row.setVisible(match);
            row.setManaged(match);
            if (match) {
                shown.add(row);
            }
        }

        boolean empty = shown.isEmpty();
        noMatch.setVisible(empty);
        noMatch.setManaged(empty);
        if (empty) {
            return;
        }

        int lastIndex = shown.size() - 1;
        shown.get(0).getStyleClass().add("first");
        shown.get(lastIndex).getStyleClass().add("last");
    }
}
