/*
 * File: ProgressController.java
 *
 * Purpose: Controller of the grading progress screen.
 *
 * Where it fits: Shows what is being graded, a progress bar and the estimated
 * time left, and offers cancel, retry and resume.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the grading progress screen.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class ProgressController extends BaseController {

    /**
     * Creates a new progress controller.
     */
    public ProgressController() {
        // nothing to set up yet
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        // TODO(Seth): implement: setting up this screen once its design is
        //     chosen
    }

    /**
     * Cancels the run.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleCancel() {
        // TODO(Seth): implement: calling GradingService.cancel after a
        //     confirmation
    }

    /**
     * Grades the failed submissions again.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleRetry() {
        // TODO(Seth): implement: calling GradingService.retryFailed
    }

    /**
     * Continues an interrupted run.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleResume() {
        // TODO(Seth): implement: calling GradingService.resume
    }
}
