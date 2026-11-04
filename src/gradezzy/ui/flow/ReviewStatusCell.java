/*
 * File: ReviewStatusCell.java
 *
 * Purpose: The Status cell of the Review table: the student's status
 * pill, with a tip that lists what still needs the instructor.
 *
 * Where it fits: ReviewScreen's Status column makes one per visible
 * row. The backend says which flags still need review.
 */
package gradezzy.ui.flow;

import javafx.scene.control.TableCell;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;

/**
 * The status pill of a student.
 */
public class ReviewStatusCell extends TableCell<Submission, Submission> {

    // The backend that knows the review decisions
    private final DashboardBackend backend;

    // The most open items the tip lists before it says how many more
    private static final int MAX_LISTED = 5;

    // Starts each listed item on its own line
    private static final String BULLET = "\n\u2022 ";

    /**
     * Creates the cell.
     *
     * @param backend the backend that knows the review decisions
     */
    public ReviewStatusCell(DashboardBackend backend) {
        this.backend = backend;
    }

    /**
     * Shows the status pill of a student.
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
        setGraphic(Students.pill(submission, backend));
        Parts.tip(this, describe(submission));
    }

    // Says what the pill means for this student and what a click shows.
    private String describe(Submission submission) {
        String items = "";
        int listed = 0;
        int open = 0;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                open++;
                if (listed < MAX_LISTED) {
                    items += BULLET + kindOf(anomaly)
                            + anomaly.getMessage();
                    listed++;
                }
            }
        }
        if (open > listed) {
            items += BULLET + (open - listed) + " more";
        }
        ResultState state = Students.state(submission, backend);
        if (!items.isEmpty()) {
            return "To check:" + items + "\nClick the student to approve, "
                    + "undo or dismiss each one.";
        } else if (Students.isFailed(state)) {
            return Students.words(state) + ". Click the student to see "
                    + "the message for each question.";
        }
        return "Nothing waits for you. Click the student to see each "
                + "question's tests.";
    }

    // Names whether an item is a fix to approve or a flag to look at.
    private String kindOf(Anomaly anomaly) {
        if (anomaly.getFixStatus() == FixStatus.APPLIED) {
            return "Fix to approve: ";
        }
        return "Flag: ";
    }
}
