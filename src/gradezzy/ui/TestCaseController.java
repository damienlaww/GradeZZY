/*
 * File: TestCaseController.java
 *
 * Purpose: Controller of the test case screen.
 *
 * Where it fits: Where the instructor uploads tester files or types test cases.
 */
package gradezzy.ui;

import javafx.fxml.FXML;

/**
 * Controller of the test case screen.
 *
 * <p>Tier: SHOULD. Owner: Seth.
 */
public class TestCaseController extends BaseController {

    /**
     * Creates a new test case controller.
     */
    public TestCaseController() {
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
     * Lets the instructor pick testers.
     *
     * <p>Tier: MUST.
     */
    @FXML
    public void handleUploadTesters() {
        // TODO(Seth): implement: a file picker for tester files
    }

    /**
     * Adds a typed test case.
     */
    @FXML
    public void handleAddTestCase() {
        // TODO(Seth): implement: validating the form and adding a TestCase
    }

    /**
     * Removes the selected test case.
     */
    @FXML
    public void handleRemoveTestCase() {
        // TODO(Seth): implement: removing the selected rows
    }
}
