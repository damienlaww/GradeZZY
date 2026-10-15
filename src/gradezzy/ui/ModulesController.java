/*
 * File: ModulesController.java
 *
 * Purpose: Controller of the Modules screen.
 *
 * Where it fits: Shows one panel per module with a tile for each of its
 * sections, so sections always stay grouped under their module. Each
 * tile says how many submissions the section's run holds. Choosing a
 * tile makes that section the one in the context bar, which every other
 * screen reads. More than one module is a proposed backend feature, so
 * the live screen says so instead of pretending.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.List;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.NotConnectedPanel;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * Controller of the Modules screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class ModulesController extends BaseController {

    // The width of one section tile
    private static final double TILE_WIDTH = 190.0;

    // The gap between section tiles
    private static final double TILE_GAP = 8.0;

    // The gap between the lines inside a tile
    private static final double LINE_GAP = 4.0;

    // The gap between a section code and its Chosen badge
    private static final double BADGE_GAP = 8.0;

    @FXML
    private Label statusLabel;

    @FXML
    private HBox demoBanner;

    @FXML
    private VBox modulePanels;

    @FXML
    private VBox multiModulePanel;

    /**
     * Creates a new modules controller.
     */
    public ModulesController() {
        // nothing to set up
    }

    /**
     * Shows the demo banner or the Not connected yet panel for more than
     * one module.
     */
    @Override
    public void onAttach() {
        show(demoBanner, getBackend().isDemo());
        boolean multiple = getBackend().supports(
                Capability.MULTIPLE_MODULES);
        show(multiModulePanel, !multiple);
        if (!multiple) {
            multiModulePanel.getChildren().add(new NotConnectedPanel(
                    Capability.MULTIPLE_MODULES));
        }
    }

    /**
     * Rebuilds the module panels each time the screen is shown.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Opens the Assessments screen for the chosen section.
     */
    @FXML
    public void handleOpenAssessment() {
        getShell().navigate(Screen.TEST_CASES);
    }

    // Redraws the status line and one panel per module.
    private void refresh() {
        statusLabel.setText("Sections stay grouped under their module. "
                + "Choose a section to work on. Chosen: "
                + getState().getSection().getLabel() + ".");

        modulePanels.getChildren().clear();
        List<String> moduleCodes = getBackend().getModuleCodes();
        if (moduleCodes.isEmpty()) {
            EmptyState empty = new EmptyState(Icon.MODULES,
                    "No module is configured",
                    "config.properties names no module. Set "
                    + "app.module.code, then open GradeZZY again.");
            empty.setAction("Open Help",
                    () -> getShell().navigate(Screen.HELP));
            modulePanels.getChildren().add(empty);
            return;
        }
        for (String moduleCode : moduleCodes) {
            modulePanels.getChildren().add(modulePanel(moduleCode));
        }
    }

    // Builds the panel of one module: its name, a caption and its tiles.
    private VBox modulePanel(String moduleCode) {
        List<String> sections = getBackend().getSectionCodes(moduleCode);
        int withRuns = countSectionsWithRuns(moduleCode, sections);

        Label title = new Label(moduleTitle(moduleCode));
        title.getStyleClass().add("panel-title");
        title.setWrapText(true);
        Label caption = new Label(sectionCaption(sections.size(),
                withRuns));
        caption.getStyleClass().add("secondary-text");
        caption.setWrapText(true);
        VBox panel = new VBox(title, caption);
        panel.getStyleClass().add("panel");

        if (sections.isEmpty()) {
            panel.getChildren().add(new EmptyState(Icon.MODULES,
                    "No sections in " + moduleCode,
                    "config.properties lists no sections for this module. "
                    + "Add them to app.section.codes, then open GradeZZY "
                    + "again."));
            return panel;
        }

        FlowPane tiles = new FlowPane(TILE_GAP, TILE_GAP);
        for (String sectionCode : sections) {
            tiles.getChildren().add(sectionTile(moduleCode, sectionCode));
        }
        panel.getChildren().add(tiles);

        if (withRuns == 0) {
            EmptyState empty = new EmptyState(Icon.MODULES,
                    "No grading runs in " + moduleCode + " yet",
                    "Choose a section above, then add its students' ZIP "
                    + "files on the Submissions screen.");
            empty.setAction("Add Submissions",
                    () -> addSubmissions(moduleCode, sections.get(0)));
            panel.getChildren().add(empty);
        }
        return panel;
    }

    // Builds the tile of one section: its code, its run and whether it
    // is the chosen section.
    private Button sectionTile(String moduleCode, String sectionCode) {
        Section section = new Section(moduleCode, sectionCode);
        GradingRun run = getBackend().getRun(section);
        boolean chosen = isChosen(section);

        Label code = new Label(sectionCode);
        code.getStyleClass().add("context-value");
        HBox top = new HBox(BADGE_GAP, code);
        top.setAlignment(Pos.CENTER_LEFT);
        if (chosen) {
            top.getChildren().add(new StatusBadge("Chosen", Tone.NEUTRAL,
                    Icon.CHECK));
        }
        Label detail = new Label(describeRun(run));
        detail.getStyleClass().add("secondary-text");
        VBox text = new VBox(LINE_GAP, top, detail);

        Button tile = new Button();
        tile.setGraphic(text);
        tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.setPrefWidth(TILE_WIDTH);
        tile.setMinWidth(Region.USE_PREF_SIZE);
        String spoken = section.getLabel() + ", " + detail.getText();
        if (chosen) {
            spoken += ", chosen";
        }
        tile.setAccessibleText(spoken);
        tile.setOnAction(event -> chooseSection(moduleCode, sectionCode));
        return tile;
    }

    // Makes a section the one in the context bar and redraws the tiles.
    private void chooseSection(String moduleCode, String sectionCode) {
        getState().moduleCodeProperty().set(moduleCode);
        getState().sectionCodeProperty().set(sectionCode);
        getShell().refreshContext();
        refresh();
        showInfo("Chose " + getState().getSection().getLabel()
                + " for every screen.");
    }

    // Chooses a module's section unless one of its sections is already
    // chosen, then opens the Submissions screen.
    private void addSubmissions(String moduleCode, String firstSection) {
        if (!moduleCode.equals(getState().moduleCodeProperty().get())) {
            chooseSection(moduleCode, firstSection);
        }
        getShell().navigate(Screen.UPLOAD);
    }

    // Says whether a section is the one in the context bar.
    private boolean isChosen(Section section) {
        Section chosen = getState().getSection();
        return chosen.getModuleCode().equals(section.getModuleCode())
                && chosen.getSectionCode().equals(section.getSectionCode());
    }

    // Describes a section's run in a few words.
    private String describeRun(GradingRun run) {
        if (run == null || run.getSubmissions().isEmpty()) {
            return "No submissions yet";
        }
        int total = run.getSubmissions().size();
        int graded = 0;
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded++;
            }
        }
        return total + " submission" + Labels.plural(total) + ", "
                + graded + " graded";
    }

    // Counts the sections of a module that have a grading run.
    private int countSectionsWithRuns(String moduleCode,
            List<String> sections) {
        int count = 0;
        for (String sectionCode : sections) {
            if (getBackend().getRun(new Section(moduleCode, sectionCode))
                    != null) {
                count++;
            }
        }
        return count;
    }

    // Describes how many sections a module has and how many have runs.
    private String sectionCaption(int sections, int withRuns) {
        String text = sections + " section" + Labels.plural(sections);
        if (sections == 0) {
            return text + ".";
        }
        if (withRuns == 0) {
            return text + ", none with a grading run yet.";
        }
        return text + ", " + withRuns + " with a grading run.";
    }

    // Returns the module code with its name when one is known.
    private String moduleTitle(String moduleCode) {
        String name = getBackend().getModuleName(moduleCode);
        if (name.isEmpty()) {
            return moduleCode;
        }
        return moduleCode + ", " + name;
    }

    // Shows or hides a node and frees its space.
    private void show(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
