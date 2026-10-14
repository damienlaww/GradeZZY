/*
 * File: SettingsController.java
 *
 * Purpose: Controller of the settings page.
 *
 * Where it fits: Shows the values of config.properties and saves changes.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the settings page.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class SettingsController extends BaseController {

    /**
     * Creates a new settings controller.
     */
    public SettingsController() {
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
     * Saves the changed settings.
     */
    @FXML
    public void handleSave() {
        // TODO(Damien): implement: validating each value, then
        //     SettingsService.save
    }

    /**
     * Discards unsaved changes.
     */
    @FXML
    public void handleReset() {
        // TODO(Damien): implement: reloading the values from SettingsService
    }
}
