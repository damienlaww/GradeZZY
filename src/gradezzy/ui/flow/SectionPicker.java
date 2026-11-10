/*
 * File: SectionPicker.java
 *
 * Purpose: A small sheet that lists a module's sections with how far
 * each has got, in the same words as Home's cards, and opens the one
 * picked on the step it needs. A dropdown at the top shows another
 * module's sections.
 *
 * Where it fits: Opened by "Change section" on the line under the steps
 * of the Grading tab, with FlowWindow.openSectionPicker. Opening a
 * section goes through FlowWindow.openSection, as Home's cards do.
 */
package gradezzy.ui.flow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.RunRecord;

/**
 * Builds the sheet that changes the section of the Grading tab.
 *
 * <p>Tier: SHOULD.
 */
public final class SectionPicker {

    // The width of the sheet
    private static final double WIDTH = 520.0;

    // The tallest the list may get before it scrolls
    private static final double LIST_HEIGHT = 320.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The width of the module dropdown
    private static final double MODULE_WIDTH = 160.0;

    // When a section was graded, such as "11 Nov, 00:32"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // The separator between facts on one line
    private static final String DOT = "  ·  ";

    // The window that shows the sheet
    private final FlowWindow window;

    // The module whose sections are listed
    private final ComboBox<String> moduleBox = new ComboBox<>();

    // The sections of that module
    private final VBox list = new VBox();

    // Says which assessment the list is for, or that the module has none
    private final Label forLine = Parts.muted("");

    // Not used: create builds the sheet.
    private SectionPicker(FlowWindow window) {
        this.window = window;
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @return the sheet's content
     */
    public static Node create(FlowWindow window) {
        return new SectionPicker(window).build();
    }

    // Builds the title, the module dropdown and the list.
    private Node build() {
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close");
        HBox top = Parts.row(Parts.GAP, new Node[] {
            Parts.title("Change section"), Parts.grow(), close});

        Label help = Parts.subtitle("Each section opens on the step it "
                + "needs: Set up before it is graded, Review while "
                + "students need a look, else Export.");
        help.setWrapText(true);
        help.setMinHeight(Label.USE_PREF_SIZE);
        help.setMaxWidth(WIDTH);

        moduleBox.getItems().setAll(window.getBackend().getModuleCodes());
        moduleBox.setValue(window.getState().getModuleCode());
        moduleBox.setPrefWidth(MODULE_WIDTH);
        moduleBox.setAccessibleText("Module");
        moduleBox.setOnAction(event -> fillList());
        Label moduleName = Parts.muted("Module");
        moduleName.getStyleClass().add("field-label");
        forLine.getStyleClass().add("small-text");
        forLine.setWrapText(true);
        forLine.setMinWidth(0);
        HBox.setHgrow(forLine, Priority.ALWAYS);
        HBox moduleRow = Parts.row(Parts.GAP, new Node[] {moduleName,
            moduleBox, forLine});

        list.getStyleClass().add("case-list");
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("page-scroll");
        fillList();

        VBox body = new VBox(Parts.GAP, top, help, moduleRow, scroll);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("section-picker");
        return body;
    }

    // Lists the sections of the module chosen in the dropdown.
    private void fillList() {
        list.getChildren().clear();
        String module = moduleBox.getValue();
        if (module == null) {
            return;
        }
        String assessmentName = findAssessment(module);
        if (assessmentName.isEmpty()) {
            forLine.setText(module + " has no assessment yet.");
        } else {
            forLine.setText("With " + assessmentName);
        }
        List<RunRecord> records = new ArrayList<>();
        if (window.getBackend().supports(Capability.HISTORY)) {
            records = window.getBackend().listRuns();
        }
        Map<String, GradingRun> runs = SectionProgress.findShownRuns(
                records, module, assessmentName,
                window.getState().getRun());
        for (String code : window.getBackend().getSectionCodes(module)) {
            HBox row = row(new Section(module, code), runs.get(code));
            row.getStyleClass().add("case-row");
            list.getChildren().add(row);
        }
        if (list.getChildren().isEmpty()) {
            Label none = Parts.muted(module + " has no sections yet. Add "
                    + "one on Home.");
            none.getStyleClass().add("small-text");
            list.getChildren().add(none);
        } else {
            list.getChildren().get(0).getStyleClass().add("first");
        }
    }

    // Builds one row: the section, its state, when it was graded and the
    // button that opens it.
    private HBox row(Section section, GradingRun run) {
        FlowState state = window.getState();
        Label name = Parts.strong(section.getSectionCode());
        name.setMinWidth(Label.USE_PREF_SIZE);
        Label pill = Parts.pill(SectionProgress.describe(state,
                window.getBackend(), section, run), SectionProgress.toneOf(
                state, window.getBackend(), section, run));
        Step step = SectionProgress.stepToOpen(state, window.getBackend(),
                section, run);
        String facts = "Opens on " + step.getLabel() + DOT
                + describeWhen(section, run);
        boolean onScreen = section.getLabel().equalsIgnoreCase(
                state.getSection().getLabel());
        if (onScreen) {
            facts = facts + DOT + "on screen now";
        }
        Label factsLabel = Parts.muted(facts);
        factsLabel.getStyleClass().add("small-text");
        factsLabel.setMinWidth(0);
        factsLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(factsLabel, Priority.ALWAYS);
        Button open = Parts.small("Open", () -> open(section, run));
        open.setMinWidth(Button.USE_PREF_SIZE);
        Parts.tip(open, "Open " + section.getLabel() + " on "
                + step.getLabel() + ".");
        HBox row = Parts.row(Parts.GAP_SMALL, new Node[] {name, pill,
            factsLabel, open});
        row.getStyleClass().add("section-picker-row");
        return row;
    }

    // Says when a section was graded, or that it was not.
    private String describeWhen(Section section, GradingRun run) {
        if (window.getState().isGrading(section)) {
            return "Being graded now";
        } else if (run == null) {
            return "Not graded yet";
        } else if (run.getStartedAt() == null) {
            return "Graded date unknown";
        }
        return "Graded " + run.getStartedAt().format(WHEN);
    }

    // Opens a section on the step it needs, with the module's assessment
    // when the section is of another module.
    private void open(Section section, GradingRun run) {
        window.closeSheet();
        window.useModuleAssessment(section.getModuleCode());
        window.openSection(section, run);
    }

    // Returns the assessment a module's sections are listed for: the one
    // in use when it is the module's, else the module's first, else "".
    private String findAssessment(String module) {
        List<SavedAssessment> own = window.listModuleAssessments(module);
        Assessment inUse = window.getBackend().getAssessment();
        for (SavedAssessment saved : own) {
            boolean isInUse = inUse != null
                    && saved.getName().equalsIgnoreCase(inUse.getName());
            if (isInUse) {
                return saved.getName();
            }
        }
        if (own.isEmpty()) {
            return "";
        }
        return own.get(0).getName();
    }
}
