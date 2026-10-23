/*
 * File: EvidenceSourceTab.java
 *
 * Purpose: Fills the Source tab of Student Evidence: the working-copy tree
 * beside the text of the chosen file.
 *
 * Where it fits: StudentReportController calls fill when the screen is
 * shown and select when another question comes into focus.
 */
package gradezzy.ui.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.FileTreeBuilder;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;

/**
 * The Source tab of the Student Evidence screen.
 */
public final class EvidenceSourceTab {

    // The backend, for the tree and the file text
    private final DashboardBackend backend;

    // The note above the tree
    private final Label note;

    // Holds the tree and the file text
    private final SplitPane split;

    // The folder tree of the working copy
    private final TreeView<String> tree;

    // The read-only text of the chosen file
    private final TextArea text;

    // Covers the file text with a message when there is no text
    private final VBox fileEmptyHolder;

    // Replaces the tree when there is no working copy
    private final VBox emptyHolder;

    // The student on show, or null
    private Submission shown = null;

    // The run the student is in, or null
    private GradingRun run = null;

    // The file chosen in the tree, relative to the working copy
    private String chosenFile = "";

    // The student whose files the tree shows
    private String treeUsername = "";

    /**
     * Creates the tab over the nodes it fills.
     *
     * @param backend the backend, for the tree and the file text
     * @param note the note above the tree
     * @param split holds the tree and the file text
     * @param tree the folder tree of the working copy
     * @param text the read-only text of the chosen file
     * @param fileEmptyHolder covers the file text with a message
     * @param emptyHolder replaces the tree when there is no working copy
     */
    public EvidenceSourceTab(DashboardBackend backend, Label note,
            SplitPane split, TreeView<String> tree, TextArea text,
            VBox fileEmptyHolder, VBox emptyHolder) {
        this.backend = backend;
        this.note = note;
        this.split = split;
        this.tree = tree;
        this.text = text;
        this.fileEmptyHolder = fileEmptyHolder;
        this.emptyHolder = emptyHolder;
        tree.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> fileChosen(newValue));
    }

    /**
     * Shows the working copy of one student and opens the file of the
     * question in focus, or the file chosen before for the same student.
     *
     * @param student the student
     * @param studentRun the run the student is in
     * @param questionId the question in focus
     */
    public void fill(Submission student, GradingRun studentRun,
            String questionId) {
        shown = student;
        run = studentRun;
        emptyHolder.getChildren().clear();
        TreeItem<String> root = backend.getFileTree(student);
        boolean hasTree = root != null;
        show(split, hasTree);
        show(emptyHolder, !hasTree);
        if (!hasTree) {
            tree.setRoot(null);
            treeUsername = "";
            note.setText("");
            emptyHolder.getChildren().add(new EmptyState(Icon.FOLDER,
                    "No working copy yet", "GradeZZY makes a working copy "
                    + "when it unpacks the ZIP file for grading. Grade or "
                    + "retry this submission to create it."));
            return;
        }

        String where = "";
        if (student.getWorkingCopyPath() != null) {
            where = ", in " + student.getWorkingCopyPath();
        }
        note.setText("The working copy of " + student.getUsername()
                + where + ", after any repairs. The original ZIP file is "
                + "unchanged.");
        if (!student.getUsername().equals(treeUsername)) {
            chosenFile = EvidenceQuestions.questionFile(run, questionId);
        }
        treeUsername = student.getUsername();
        expandAll(root);
        tree.setRoot(root);
        TreeItem<String> wanted = findItem(root, chosenFile);
        if (wanted == null) {
            wanted = firstFile(root);
        }
        if (wanted == null) {
            showFileProblem("This working copy has no files",
                    "The ZIP file held no files GradeZZY could unpack.");
            return;
        }
        tree.getSelectionModel().select(wanted);
    }

    /**
     * Opens the file of a question, when the working copy has it.
     *
     * @param questionId the question now in focus
     */
    public void select(String questionId) {
        TreeItem<String> root = tree.getRoot();
        if (root == null) {
            return;
        }
        TreeItem<String> item = findItem(root,
                EvidenceQuestions.questionFile(run, questionId));
        if (item != null) {
            tree.getSelectionModel().select(item);
        }
    }

    // Shows the text of the file chosen in the tree.
    private void fileChosen(TreeItem<String> item) {
        if (shown == null || item == null || !item.isLeaf()
                || item.getParent() == null) {
            showFileProblem("Choose a file",
                    "Pick a file in the tree to read it here.");
            return;
        }
        chosenFile = FileTreeBuilder.relativePath(item);
        String content = backend.readSourceFile(shown, chosenFile);
        if (content == null || content.isEmpty()) {
            showFileProblem("This file could not be read.",
                    unreadableMessage());
            return;
        }
        show(fileEmptyHolder, false);
        text.setText(content);
        text.setScrollTop(0.0);
    }

    // Explains why a file shows no text.
    private String unreadableMessage() {
        Capability source = Capability.SOURCE_VIEW;
        if (!backend.isDemo()
                && source.getStatus() != CapabilityStatus.WORKS_NOW) {
            return "Showing working-copy files waits for "
                    + source.getServiceMethod() + " (owner: "
                    + source.getOwner() + ").";
        }
        return "The file may be empty or not a text file. Choose another "
                + "file in the tree.";
    }

    // Covers the file text with a message.
    private void showFileProblem(String title, String message) {
        text.setText("");
        fileEmptyHolder.getChildren().setAll(new EmptyState(Icon.FILE,
                title, message));
        show(fileEmptyHolder, true);
    }

    // Finds the file with a relative path in a tree.
    private static TreeItem<String> findItem(TreeItem<String> item,
            String path) {
        if (item.isLeaf() && item.getParent() != null
                && FileTreeBuilder.relativePath(item).equals(path)) {
            return item;
        }
        for (TreeItem<String> child : item.getChildren()) {
            TreeItem<String> found = findItem(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // Finds the first file in a tree.
    private static TreeItem<String> firstFile(TreeItem<String> item) {
        if (item.isLeaf() && item.getParent() != null) {
            return item;
        }
        for (TreeItem<String> child : item.getChildren()) {
            TreeItem<String> found = firstFile(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // Opens every folder of a tree.
    private static void expandAll(TreeItem<String> item) {
        item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) {
            expandAll(child);
        }
    }

    // Shows or hides a node and frees its space.
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
