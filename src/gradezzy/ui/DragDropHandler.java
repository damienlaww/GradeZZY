/*
 * File: DragDropHandler.java
 *
 * Purpose: Lets the instructor drop files onto the window.
 *
 * Where it fits: Used by the upload screen next to the file picker.
 */
package gradezzy.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;

/**
 * Lets the instructor drop files onto the window.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class DragDropHandler {

    /**
     * Creates a new drag drop handler.
     */
    public DragDropHandler() {
        // nothing to set up yet
    }

    /**
     * Makes a part of the window accept dropped files.
     *
     * @param target the target
     */
    public void install(Node target) {
        // TODO(Seth): implement: setOnDragOver and setOnDragDropped handlers
        //     that accept zip files and folders only
    }

    /**
     * Returns the files dropped most recently.
     *
     * @return the dropped paths
     */
    public List<Path> getDroppedPaths() {
        // TODO(Seth): implement: returning the paths from the last drop
        return new ArrayList<>();
    }
}
