/*
 * File: ReviewStudentCell.java
 *
 * Purpose: The Student cell of the Review table: the student's name
 * above the username, with a Similar code warning beside the name when
 * the similarity check flagged the student.
 *
 * Where it fits: ReviewScreen's Student column makes one per visible
 * row.
 */
package gradezzy.ui.flow;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The student's name above the username.
 *
 * <p>Tier: SHOULD.
 */
public class ReviewStudentCell extends TableCell<Submission, Submission> {

    // The line break between the name and the username in the tooltip
    private static final String LINE_END = "\n";

    // The backend that knows which flags were dismissed
    private final DashboardBackend backend;

    /**
     * Creates an empty cell.
     *
     * @param backend the backend that knows the review decisions
     */
    public ReviewStudentCell(DashboardBackend backend) {
        this.backend = backend;
    }

    /**
     * Shows the name and username of a student.
     *
     * @param submission the student, or null
     * @param isEmpty true when the row shows no student
     */
    @Override
    protected void updateItem(Submission submission, boolean isEmpty) {
        super.updateItem(submission, isEmpty);
        if (isEmpty || submission == null) {
            setGraphic(null);
            setTooltip(null);
            return;
        }
        // The whole name, for a name too long for the column
        String tipText = Students.name(submission) + LINE_END
                + submission.getUsername();
        setTooltip(new Tooltip(tipText));
        Label name = Parts.strong(Students.name(submission));
        name.setMinWidth(0);
        HBox nameLine = Parts.row(Parts.GAP_SMALL, new Node[] {name});
        Label warning = Students.similarWarning(submission, backend);
        if (warning != null) {
            nameLine.getChildren().add(warning);
        }
        Label username = Parts.muted(submission.getUsername());
        username.getStyleClass().add("small-text");
        VBox box = new VBox(nameLine, username);
        box.getStyleClass().add("student-cell");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        setGraphic(box);
    }
}
