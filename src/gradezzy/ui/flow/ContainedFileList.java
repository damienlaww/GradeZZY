/*
 * File: ContainedFileList.java
 *
 * Purpose: The "Show files" link under a chosen folder or zip of student
 * zips, and the list it opens: every file inside, by name, with a tag on
 * each file that is not graded. A filter box appears when the list is
 * long.
 *
 * Where it fits: SetUpScreen adds one under each submissions row whose
 * ImportItem lists files. Set up remembers which lists are open, so a
 * list stays open when the rows are drawn again.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.ui.backend.ContainedFile;

/**
 * A link that shows or hides the files inside a chosen folder or zip.
 *
 * <p>Tier: SHOULD.
 */
public class ContainedFileList {

    // The tallest the open list grows before it scrolls, in pixels
    private static final double LIST_HEIGHT = 220.0;

    // The fewest files that get a filter box
    private static final int FILTER_MIN_FILES = 15;

    // The space between the link, the filter and the list
    private static final double SPACING = 6.0;

    // The files, in the order shown
    private final List<ContainedFile> files;

    // Called after the list opens or closes
    private final Runnable onToggle;

    // Whether the list is open
    private boolean open;

    // The link that opens and closes the list
    private final Hyperlink toggle = new Hyperlink();

    // The filter box, list and empty note shown while open
    private final VBox body = new VBox(SPACING);

    // One row per file, in the order of files
    private final List<HBox> rows = new ArrayList<>();

    // Shown when the filter matches no file
    private final Label noMatch = Parts.muted("No file matches.");

    /**
     * Creates the link and its list.
     *
     * @param files the files inside the folder or zip
     * @param open whether the list starts open
     * @param onToggle called after the list opens or closes, or null
     */
    public ContainedFileList(List<ContainedFile> files, boolean open,
            Runnable onToggle) {
        this.files = new ArrayList<>(files);
        this.open = open;
        this.onToggle = onToggle;
    }

    /**
     * Builds the link and the list it opens. The link goes under the
     * row's notes and the list under the whole row, so opening it does
     * not move the row's icon, status or links.
     *
     * @return the link; the list is returned by getList
     */
    public Node build() {
        toggle.getStyleClass().add("small-text");
        toggle.setOnAction(event -> setOpen(!open));

        VBox list = new VBox();
        list.getStyleClass().add("contained-files");
        for (ContainedFile file : files) {
            HBox row = createRow(file);
            rows.add(row);
            list.getChildren().add(row);
        }

        noMatch.getStyleClass().addAll("small-text", "contained-file");
        show(noMatch, false);
        list.getChildren().add(noMatch);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.getStyleClass().addAll("page-scroll", "contained-files-box");

        if (files.size() >= FILTER_MIN_FILES) {
            TextField filter = new TextField();
            filter.setPromptText("Filter by file name or username");
            filter.getStyleClass().add("small-text");
            filter.textProperty().addListener((seen, before, now) ->
                    applyFilter(now));
            body.getChildren().add(filter);
        }

        body.getChildren().add(scroll);
        update();

        return toggle;
    }

    /**
     * Returns the list the link opens, with its filter box. Call build
     * first.
     *
     * @return the list, hidden while closed
     */
    public Node getList() {
        return body;
    }

    // Builds the row of one file: its name, and a tag when not graded.
    private HBox createRow(ContainedFile file) {
        Label name = new Label(file.getName());
        name.getStyleClass().add("small-text");
        name.setMinWidth(0);
        HBox.setHgrow(name, Priority.ALWAYS);

        HBox row = Parts.row(Parts.GAP_SMALL, new Node[] {name});
        if (!file.isGraded()) {
            name.getStyleClass().add("muted");
            Label tag = Parts.muted(file.getNote());
            tag.getStyleClass().add("small-text");
            tag.setMinWidth(Label.USE_PREF_SIZE);
            row.getChildren().addAll(Parts.grow(), tag);
        }
        row.getStyleClass().add("contained-file");

        return row;
    }

    // Shows only the rows whose file name holds the text, ignoring case.
    private void applyFilter(String text) {
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int index = 0; index < rows.size(); index++) {
            String name = files.get(index).getName().toLowerCase(Locale.ROOT);
            boolean match = wanted.isEmpty() || name.contains(wanted);
            show(rows.get(index), match);
            if (match) {
                shown++;
            }
        }

        show(noMatch, shown == 0);
    }

    // Opens or closes the list and tells Set up.
    private void setOpen(boolean nowOpen) {
        open = nowOpen;
        update();

        if (onToggle != null) {
            onToggle.run();
        }
    }

    // Shows the list and sets the link's words to match.
    private void update() {
        show(body, open);

        if (open) {
            toggle.setText("Hide files");
        } else {
            toggle.setText("Show " + Parts.plural(files.size(), "file"));
        }
    }

    // Shows or hides a node, leaving no gap when hidden.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
