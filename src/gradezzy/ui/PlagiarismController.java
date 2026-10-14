/*
 * File: PlagiarismController.java
 *
 * Purpose: Controller of the plagiarism screen.
 *
 * Where it fits: Lists pairs of similar submissions for the instructor to
 * compare.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the plagiarism screen.
 *
 * <p>Tier: COULD. Owner: Seth.
 */
public class PlagiarismController extends BaseController {

    /**
     * Creates a new plagiarism controller.
     */
    public PlagiarismController() {
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
     * Runs the similarity check.
     */
    @FXML
    public void handleRunCheck() {
        // TODO(Seth): implement: calling PlagiarismService.check in the
        //     background
    }
}
