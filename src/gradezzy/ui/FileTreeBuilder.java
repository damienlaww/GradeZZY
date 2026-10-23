/*
 * File: FileTreeBuilder.java
 *
 * Purpose: Builds the tree view of a submission's folders and files.
 *
 * Where it fits: Used by the Source tab of Student Evidence, through
 * LiveBackend for a working copy and DemoBackend for sample files.
 */
package gradezzy.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.scene.control.TreeItem;

/**
 * Builds the tree view of a submission's folders and files.
 */
public class FileTreeBuilder {

    /**
     * The most entries in one tree, so a huge folder cannot freeze the app.
     */
    public static final int MAX_ENTRIES = 2000;

    // The separator in relative paths
    private static final String SEPARATOR = "/";

    // The number of entries added to the tree being built
    private int entryCount = 0;

    /**
     * Creates a new file tree builder.
     */
    public FileTreeBuilder() {
        // nothing to set up
    }

    /**
     * Builds a tree of the folder and everything in it.
     *
     * @param rootDirectory the root directory
     * @return the root item, or null when the folder cannot be read
     */
    public TreeItem<String> build(Path rootDirectory) {
        if (rootDirectory == null || !Files.isDirectory(rootDirectory)) {
            return null;
        }

        entryCount = 0;
        File root = rootDirectory.toFile();
        TreeItem<String> rootItem = new TreeItem<>(root.getName());
        rootItem.setExpanded(true);
        addChildren(rootItem, root);
        return rootItem;
    }

    /**
     * Builds a tree from relative file paths, such as "Q1/Q1a.java".
     *
     * @param rootName the name of the top folder
     * @param relativePaths the files, with folders separated by "/"
     * @return the root item
     */
    public TreeItem<String> buildFromPaths(String rootName,
            List<String> relativePaths) {
        TreeItem<String> rootItem = new TreeItem<>(rootName);
        rootItem.setExpanded(true);

        for (String relativePath : relativePaths) {
            TreeItem<String> parent = rootItem;
            String[] parts = relativePath.split(SEPARATOR);
            for (String part : parts) {
                parent = findOrAddChild(parent, part);
            }
        }
        return rootItem;
    }

    /**
     * Returns the path of a tree item relative to its tree's root.
     *
     * @param item an item of a tree built here
     * @return the relative path, such as "Q1/Q1a.java"
     */
    public static String relativePath(TreeItem<String> item) {
        String path = item.getValue();
        TreeItem<String> parent = item.getParent();

        while (parent != null && parent.getParent() != null) {
            path = parent.getValue() + SEPARATOR + path;
            parent = parent.getParent();
        }
        return path;
    }

    // Adds one item per folder and file, folders first, in name order.
    private void addChildren(TreeItem<String> parentItem, File folder) {
        File[] entries = folder.listFiles();
        if (entries == null) {
            return;
        }

        Arrays.sort(entries);
        List<File> folders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File entry : entries) {
            // Links are skipped so a link to a parent folder cannot loop.
            if (Files.isSymbolicLink(entry.toPath())) {
                continue;
            }
            if (entry.isDirectory()) {
                folders.add(entry);
            } else {
                files.add(entry);
            }
        }

        for (File child : folders) {
            if (entryCount >= MAX_ENTRIES) {
                return;
            }
            entryCount++;
            TreeItem<String> childItem = new TreeItem<>(child.getName());
            parentItem.getChildren().add(childItem);
            addChildren(childItem, child);
        }

        for (File child : files) {
            if (entryCount >= MAX_ENTRIES) {
                return;
            }
            entryCount++;
            parentItem.getChildren().add(new TreeItem<>(child.getName()));
        }
    }

    // Returns the child with a name, adding it first when it is missing.
    private TreeItem<String> findOrAddChild(TreeItem<String> parent,
            String name) {
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().equals(name)) {
                return child;
            }
        }

        TreeItem<String> child = new TreeItem<>(name);
        child.setExpanded(true);
        parent.getChildren().add(child);
        return child;
    }
}
