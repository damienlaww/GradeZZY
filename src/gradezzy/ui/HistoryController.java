/*
 * File: HistoryController.java
 *
 * Purpose: Controller of the history screen.
 *
 * Where it fits: Lists earlier runs and offers backup and recovery.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the history screen.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public class HistoryController extends BaseController {

    /**
     * Creates a new history controller.
     */
    public HistoryController() {
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
     * Opens the selected run.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleOpenRun() {
        // TODO(Damien): implement: loading the run and showing its results
    }

    /**
     * Makes a backup now.
     */
    @FXML
    public void handleBackup() {
        // TODO(Damien): implement: calling HistoryService.backupNow
    }

    /**
     * Restores the selected backup.
     */
    @FXML
    public void handleRestore() {
        // TODO(Damien): implement: calling HistoryService.restore after a
        //     confirmation
    }
}
