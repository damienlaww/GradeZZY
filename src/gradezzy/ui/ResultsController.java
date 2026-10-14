/*
 * File: ResultsController.java
 *
 * Purpose: Controller of the results screen.
 *
 * Where it fits: Shows per-question and per-student scores with search, filter
 * and sort, and starts exports.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the results screen.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class ResultsController extends BaseController {

    /**
     * Creates a new results controller.
     */
    public ResultsController() {
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
     * Exports the score sheet.
     */
    @FXML
    public void handleExportCsv() {
        // TODO(Seth): implement: calling ExportService.export for the CSV
        //     format
    }

    /**
     * Exports the results as JSON.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleExportJson() {
        // TODO(Seth): implement: calling ExportService.export for the JSON
        //     format
    }

    /**
     * Applies the search text.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleSearch() {
        // TODO(Seth): implement: refreshing the table from SearchService
    }

    /**
     * Opens the selected student.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleOpenStudent() {
        // TODO(Seth): implement: navigating to the student report screen
    }
}
