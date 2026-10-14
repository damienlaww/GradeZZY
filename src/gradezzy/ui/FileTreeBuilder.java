/*
 * File: FileTreeBuilder.java
 *
 * Purpose: Builds the tree view of a submission's folders and files.
 *
 * Where it fits: Used by the upload and student report screens.
 */
package gradezzy.ui;

import java.nio.file.Path;
import javafx.scene.control.TreeItem;

/**
 * Builds the tree view of a submission's folders and files.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class FileTreeBuilder {

    /**
     * Creates a new file tree builder.
     */
    public FileTreeBuilder() {
        // nothing to set up yet
    }

    /**
     * Builds a tree of the folder and everything in it.
     *
     * @param rootDirectory the root directory
     * @return the root item, or null until this is implemented
     */
    public TreeItem<String> build(Path rootDirectory) {
        // TODO(Damien): implement: a recursive walk that adds one TreeItem per
        //     folder and file, folders first
        return null;
    }
}
