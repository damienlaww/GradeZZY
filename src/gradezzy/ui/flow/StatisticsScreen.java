/*
 * File: StatisticsScreen.java
 *
 * Purpose: The Statistics tab: charts and figures of one graded section,
 * or of up to three sections side by side.
 *
 * Where it fits: FlowWindow shows it when the Statistics tab is picked,
 * and openStatistics points it at one section first.
 */
package gradezzy.ui.flow;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * The Statistics tab.
 */
public class StatisticsScreen extends FlowScreen {

    // The page
    private final ScrollPane view = Parts.widePage(Parts.header(
            "Statistics", "Charts of the graded sections."));

    // The module shown
    private String moduleCode = null;

    // The section shown
    private String sectionCode = null;

    /**
     * Creates the screen.
     *
     * @param window the window that shows it
     */
    public StatisticsScreen(FlowWindow window) {
        super(window);
    }

    /**
     * Points the screen at one section.
     *
     * @param newModuleCode the module
     * @param newSectionCode the section
     */
    public void selectSection(String newModuleCode,
            String newSectionCode) {
        moduleCode = newModuleCode;
        sectionCode = newSectionCode;
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        // TODO(Jing Yuan): implement: the charts (placeholder until the
        // Statistics screen is built)
    }

    @Override
    public boolean usesActionBar() {
        return false;
    }
}
