/*
 * File: DragDropHandler.java
 *
 * Purpose: Lets the instructor drop files onto the window.
 *
 * Where it fits: Used by the upload screen next to the file picker. It
 * accepts ZIP files and folders only, shows the drop area as ready while
 * they are dragged over it, and tells the screen when they are dropped.
 * It never opens the dropped files.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

/**
 * Lets the instructor drop files onto the window.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class DragDropHandler {

    /** The style class added to the target while files hover over it. */
    public static final String DRAG_OVER_CLASS = "drag-over";

    // The extension of an accepted file
    private static final String ZIP_EXTENSION = ".zip";

    // The paths from the last drop
    private final List<Path> droppedPaths = new ArrayList<>();

    // Called after each drop, or null
    private Runnable onDropped = null;

    /**
     * Creates a new drag drop handler.
     */
    public DragDropHandler() {
        // nothing to set up
    }

    /**
     * Sets what happens after files are dropped.
     *
     * @param onDropped called after each drop; read getDroppedPaths()
     */
    public void setOnDropped(Runnable onDropped) {
        this.onDropped = onDropped;
    }

    /**
     * Makes a part of the window accept dropped files.
     *
     * @param target the target
     */
    public void install(Node target) {
        target.setOnDragOver(event -> handleDragOver(event, target));
        target.setOnDragExited(event -> target.getStyleClass().remove(
                DRAG_OVER_CLASS));
        target.setOnDragDropped(event -> handleDrop(event, target));
    }

    /**
     * Returns the files dropped most recently.
     *
     * @return the dropped paths
     */
    public List<Path> getDroppedPaths() {
        return new ArrayList<>(droppedPaths);
    }

    // Accepts the drag when it holds at least one ZIP file or folder.
    private void handleDragOver(DragEvent event, Node target) {
        Dragboard board = event.getDragboard();
        if (board.hasFiles() && !acceptedFiles(board).isEmpty()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!target.getStyleClass().contains(DRAG_OVER_CLASS)) {
                target.getStyleClass().add(DRAG_OVER_CLASS);
            }
        }
        event.consume();
    }

    // Keeps the accepted paths and tells the screen.
    private void handleDrop(DragEvent event, Node target) {
        target.getStyleClass().remove(DRAG_OVER_CLASS);
        Dragboard board = event.getDragboard();
        boolean accepted = false;

        if (board.hasFiles()) {
            droppedPaths.clear();
            for (File file : acceptedFiles(board)) {
                droppedPaths.add(file.toPath());
            }
            accepted = !droppedPaths.isEmpty();
        }

        event.setDropCompleted(accepted);
        event.consume();
        if (accepted && onDropped != null) {
            onDropped.run();
        }
    }

    // Returns the dropped files that are ZIP files or folders.
    private List<File> acceptedFiles(Dragboard board) {
        List<File> accepted = new ArrayList<>();
        for (File file : board.getFiles()) {
            boolean isZip = file.getName().toLowerCase().endsWith(
                    ZIP_EXTENSION);
            if (file.isDirectory() || isZip) {
                accepted.add(file);
            }
        }
        return accepted;
    }
}
