/*
 * File: UploadController.java
 *
 * Purpose: Controller of the upload screen.
 *
 * Where it fits: Where the instructor picks a section and adds submissions by
 * file picker or drag and drop, then sees each one as a folder tree.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the upload screen.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class UploadController extends BaseController {

    /**
     * Creates a new upload controller.
     */
    public UploadController() {
        // nothing to set up yet
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        // TODO(Damien): implement: setting up this screen once its design is
        //     chosen
    }

    /**
     * Opens the system file picker.
     */
    @FXML
    public void handleChooseFiles() {
        // TODO(Damien): implement: a FileChooser for zip files, with input
        //     validation
    }

    /**
     * Opens the system folder picker.
     */
    @FXML
    public void handleChooseFolder() {
        // TODO(Damien): implement: a DirectoryChooser, with input validation
    }

    /**
     * Reacts to a new section choice.
     */
    @FXML
    public void handleSectionChosen() {
        // TODO(Damien): implement: storing the chosen section for this upload
        //     batch
    }

    /**
     * Starts grading the whole batch.
     */
    @FXML
    public void handleStartGrading() {
        // TODO(Damien): implement: starting a GradingTask and moving to the
        //     progress screen
    }
}
