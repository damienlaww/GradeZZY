/*
 * File: SimilarityComparison.java
 *
 * Purpose: Shows the two files of a similar pair side by side, with the
 * files left out of the comparison.
 *
 * Where it fits: The Similarity Review screen creates one and calls show
 * for the chosen pair.
 */
package gradezzy.ui.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.SimilarityDetail;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.NotConnectedPanel;

/**
 * The side-by-side comparison of one similar pair.
 */
public final class SimilarityComparison {

    // The first student's username
    private final Label firstName;

    // The first student's compared file
    private final Label firstFile;

    // The excerpt from the first student's file
    private final TextArea firstCode;

    // The second student's username
    private final Label secondName;

    // The second student's compared file
    private final Label secondFile;

    // The excerpt from the second student's file
    private final TextArea secondCode;

    // The files left out of the comparison
    private final VBox excludedList;

    /**
     * Creates the comparison from its nodes.
     *
     * @param firstName the first student's username
     * @param firstFile the first student's compared file
     * @param firstCode the excerpt from the first file
     * @param secondName the second student's username
     * @param secondFile the second student's compared file
     * @param secondCode the excerpt from the second file
     * @param excludedList the files left out of the comparison
     */
    public SimilarityComparison(Label firstName, Label firstFile,
            TextArea firstCode, Label secondName, Label secondFile,
            TextArea secondCode, VBox excludedList) {
        this.firstName = firstName;
        this.firstFile = firstFile;
        this.firstCode = firstCode;
        this.secondName = secondName;
        this.secondFile = secondFile;
        this.secondCode = secondCode;
        this.excludedList = excludedList;
    }

    /**
     * Empties the list of left-out files.
     */
    public void clear() {
        excludedList.getChildren().clear();
    }

    /**
     * Shows both files, their excerpts and the files left out.
     *
     * @param pair the pair
     * @param detail the evidence for it
     */
    public void show(SimilarityPair pair, SimilarityDetail detail) {
        firstName.setText(pair.getFirstUsername());
        firstFile.setText(detail.getFirstFile());
        firstCode.setText(detail.getFirstExcerpt());
        firstCode.setAccessibleText("Excerpt from " + pair.getFirstUsername()
                + ", " + detail.getFirstFile());
        secondName.setText(pair.getSecondUsername());
        secondFile.setText(detail.getSecondFile());
        secondCode.setText(detail.getSecondExcerpt());
        secondCode.setAccessibleText("Excerpt from "
                + pair.getSecondUsername() + ", " + detail.getSecondFile());

        clear();
        for (String file : detail.getExcludedFiles()) {
            Label line = new Label(file);
            line.getStyleClass().add("mono");
            line.setWrapText(true);
            line.setMinHeight(Region.USE_PREF_SIZE);
            excludedList.getChildren().add(line);
        }
        if (detail.getExcludedFiles().isEmpty()) {
            Label none = new Label("The check listed no starter or tester "
                    + "files for this pair.");
            none.getStyleClass().add("secondary-text");
            none.setWrapText(true);
            excludedList.getChildren().add(none);
        }
    }

    /**
     * Explains why a pair has no excerpts to compare.
     *
     * @param backend the backend
     * @return a Not connected panel or an empty state
     */
    public static Node missingPanel(DashboardBackend backend) {
        if (backend.supports(Capability.SIMILARITY_DETAIL)) {
            return new EmptyState(Icon.SIMILARITY,
                    "No excerpts for this pair", "The check listed the pair "
                    + "but returned no files to compare. Open each "
                    + "student's evidence to read the code.");
        }
        return new NotConnectedPanel(Capability.SIMILARITY_DETAIL);
    }
}
