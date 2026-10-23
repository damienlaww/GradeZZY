/*
 * File: ReviewInspector.java
 *
 * Purpose: Fills the inspector part that the review screens share.
 *
 * Where it fits: AnomalyInspector and RepairInspector each use one and add
 * their own badges and boxes.
 */
package gradezzy.ui.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.Student;
import gradezzy.ui.component.Labels;

/**
 * The shared part of the review inspectors.
 */
public final class ReviewInspector {

    // The finding's title, or what to do when nothing is selected
    private final Label titleLabel;

    // The hint shown when nothing is selected
    private final Label hintLabel;

    // The details, hidden when nothing is selected
    private final VBox detailBox;

    // The student's name, or the username if unknown
    private final Label nameLabel;

    // The username under the full name
    private final Label usernameLabel;

    // The finding's message
    private final Label messageLabel;

    // What the instructor can do about it
    private final Label actionLabel;

    /**
     * Creates the shared part of an inspector from its nodes.
     *
     * @param titleLabel the title
     * @param hintLabel the hint shown when nothing is selected
     * @param detailBox the details, hidden when nothing is selected
     * @param nameLabel the student's full name
     * @param usernameLabel the username under the full name
     * @param messageLabel the message
     * @param actionLabel what the instructor can do
     */
    public ReviewInspector(Label titleLabel, Label hintLabel,
            VBox detailBox, Label nameLabel, Label usernameLabel,
            Label messageLabel, Label actionLabel) {
        this.titleLabel = titleLabel;
        this.hintLabel = hintLabel;
        this.detailBox = detailBox;
        this.nameLabel = nameLabel;
        this.usernameLabel = usernameLabel;
        this.messageLabel = messageLabel;
        this.actionLabel = actionLabel;
    }

    /**
     * Shows that nothing is selected, with a hint about what to do.
     *
     * @param title the title, such as "No flag selected"
     * @param hint what selecting a row shows
     */
    public void showNothing(String title, String hint) {
        titleLabel.setText(title);
        hintLabel.setText(hint);
        setShown(hintLabel, true);
        setShown(detailBox, false);
    }

    /**
     * Shows one finding: its title, student, message and suggested action.
     *
     * @param anomaly the finding or flag
     * @param student the student, or null when the roster names none
     */
    public void showAnomaly(Anomaly anomaly, Student student) {
        setShown(hintLabel, false);
        setShown(detailBox, true);
        titleLabel.setText(Labels.anomalyTitle(anomaly.getType()));
        if (student == null) {
            nameLabel.setText(anomaly.getUsername());
            setShown(usernameLabel, false);
        } else {
            nameLabel.setText(student.getFirstName() + " "
                    + student.getLastName());
            usernameLabel.setText(anomaly.getUsername());
            setShown(usernameLabel, true);
        }
        messageLabel.setText(anomaly.getMessage());
        actionLabel.setText(anomaly.getSuggestedAction());
    }

    /**
     * Shows or hides a node and frees its space.
     *
     * @param node the node
     * @param visible true to show it
     */
    public static void setShown(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
