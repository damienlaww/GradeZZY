/*
 * File: StudentReportController.java
 *
 * Purpose: Controller of the individual student report screen.
 *
 * Where it fits: Shows one student's scores, failed tests, anomalies and files.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the individual student report screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class StudentReportController extends BaseController {

    /**
     * Creates a new student report controller.
     */
    public StudentReportController() {
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
     * Exports this student's report.
     */
    @FXML
    public void handleExport() {
        // TODO(Damien): implement: calling ExportService with
        //     StudentReportExporter
    }
}
