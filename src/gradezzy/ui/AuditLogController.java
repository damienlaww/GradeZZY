/*
 * File: AuditLogController.java
 *
 * Purpose: Controller of the audit log screen.
 *
 * Where it fits: Lists every recorded instructor action and exports the list.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the audit log screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class AuditLogController extends BaseController {

    /**
     * Creates a new audit log controller.
     */
    public AuditLogController() {
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
     * Exports the audit log.
     */
    @FXML
    public void handleExport() {
        // TODO(Damien): implement: calling ExportService with AuditLogExporter
    }
}
