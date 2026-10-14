/*
 * File: MainController.java
 *
 * Purpose: Controller of the main window that hosts every other screen.
 *
 * Where it fits: Owns navigation: Next, Back and Cancel, and the side menu.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the main window that hosts every other screen.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class MainController extends BaseController {

    /**
     * Creates a new main controller.
     */
    public MainController() {
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
     * Moves to the next step.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleNext() {
        // TODO(Damien): implement: navigation to the next screen
    }

    /**
     * Moves to the previous step.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleBack() {
        // TODO(Damien): implement: navigation to the previous screen
    }

    /**
     * Leaves the current step.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleCancel() {
        // TODO(Damien): implement: a confirmation and a return to the start
        //     screen
    }

    /**
     * Switches light and dark mode.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleToggleTheme() {
        // TODO(Damien): implement: calling ThemeManager and saving the choice
    }
}
