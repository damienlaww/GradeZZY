/*
 * File: AnomalyLogController.java
 *
 * Purpose: Controller of the anomaly log screen.
 *
 * Where it fits: Lists every anomaly with an action button, and lets the
 * instructor approve or undo fixes, singly or in bulk.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the anomaly log screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class AnomalyLogController extends BaseController {

    /**
     * Creates a new anomaly log controller.
     */
    public AnomalyLogController() {
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
     * Approves the selected fixes.
     */
    @FXML
    public void handleApprove() {
        // TODO(Damien): implement: calling AnomalyService.approveAll for the
        //     selection
    }

    /**
     * Undoes the selected fixes.
     */
    @FXML
    public void handleUndo() {
        // TODO(Damien): implement: calling AnomalyService.undoAll for the
        //     selection
    }

    /**
     * Selects every row.
     */
    @FXML
    public void handleSelectAll() {
        // TODO(Damien): implement: selecting all rows of the table
    }

    /**
     * Applies the search and filters.
     */
    @FXML
    public void handleFilterChanged() {
        // TODO(Damien): implement: refreshing the table from SearchService
    }
}
