/*
 * File: StatisticsController.java
 *
 * Purpose: Controller of the statistics dashboard.
 *
 * Where it fits: Shows charts and numbers, filtered by module and section.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the statistics dashboard.
 *
 * <p>Tier: SHOULD. Owner: Seth.
 */
public class StatisticsController extends BaseController {

    /**
     * Creates a new statistics controller.
     */
    public StatisticsController() {
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
     * Reacts to a new filter.
     */
    @FXML
    public void handleFilterChanged() {
        // TODO(Seth): implement: calling StatisticsService.summarise and
        //     redrawing
    }
}
