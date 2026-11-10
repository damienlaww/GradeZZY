/*
 * File: SettingsScope.java
 *
 * Purpose: Marks the groups on the Settings tab that change with the
 * module chosen under Course or the assessment being edited, with a
 * note under each title, and briefly lights them up when the module or
 * the assessment shown changes.
 *
 * Where it fits: SettingsScreen marks those groups as it builds them,
 * and calls show whenever the module or the assessment shown changes.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javafx.scene.control.Label;

/**
 * The notes on the Settings groups that change with the module or the
 * assessment.
 *
 * <p>Tier: SHOULD.
 */
public class SettingsScope {

    // Joins a card's title and the name it is about
    private static final String NAME_SEPARATOR = " · ";

    // The title of the score sheets group, before the assessment's name
    private static final String SCORE_SHEETS_TITLE = "Score sheets";

    // What the module shows when there is no module
    private static final String NO_MODULE = "No module";

    // Stands for the module's name in the note of a group of the module
    private static final String MODULE_MARK = "%s";

    // Scrolls the cards to a group
    private final Consumer<SettingsGroup> jump;

    // Says whether Subtle animations is on in Settings
    private final BooleanSupplier animated;

    // The groups of the module, with the note of each
    private final List<SettingsGroup> moduleGroups = new ArrayList<>();
    private final List<Label> moduleNotes = new ArrayList<>();
    private final List<String> moduleNoteTexts = new ArrayList<>();

    // The groups of the module whose title names it, with the title of
    // each before the name
    private final List<SettingsGroup> titledGroups = new ArrayList<>();
    private final List<String> titleTexts = new ArrayList<>();

    // The note of the assessment group
    private final Label assessmentNote = new Label();

    // The note of the score sheets group
    private final Label scoreSheetsNote = new Label();

    // The group of the score sheets of the assessment in use
    private SettingsGroup scoreSheetsGroup = null;

    // The group of the assessment
    private SettingsGroup assessmentGroup = null;

    // The module shown, or null for none
    private String module = null;

    // The assessment being edited, or null for none
    private String editing = null;

    // True while a new assessment is filled in
    private boolean creating = false;

    // The name of the assessment in use, or "" for none
    private String inUse = "";

    // True once the page is built, so a change of module or assessment
    // lights up the groups it changes
    private boolean armed = false;

    /**
     * Creates the notes, with no module or assessment yet.
     *
     * @param jump scrolls the cards to a group
     * @param animated says whether Subtle animations is on in Settings
     */
    public SettingsScope(Consumer<SettingsGroup> jump,
            BooleanSupplier animated) {
        this.jump = jump;
        this.animated = animated;
        assessmentNote.getStyleClass().addAll("muted", "small-text",
                "scope-note");
        assessmentNote.setWrapText(true);
        scoreSheetsNote.getStyleClass().addAll("muted", "small-text",
                "scope-note");
        scoreSheetsNote.setWrapText(true);
        refresh();
    }

    /**
     * Marks a group whose settings are the chosen module's own, with a
     * note under its title.
     *
     * @param group the group
     * @param note the note, with %s where the module's name goes
     */
    public void forModule(SettingsGroup group, String note) {
        Label noteLabel = new Label();
        noteLabel.getStyleClass().addAll("muted", "small-text",
                "scope-note");
        noteLabel.setWrapText(true);
        moduleGroups.add(group);
        moduleNotes.add(noteLabel);
        moduleNoteTexts.add(note);
        group.markChanging(noteLabel);
        refresh();
    }

    /**
     * Puts the module's name after a group's title, such as "Export
     * folders · CS102".
     *
     * @param group the group, marked with forModule
     * @param title the title before the module's name
     */
    public void nameModuleInTitle(SettingsGroup group, String title) {
        titledGroups.add(group);
        titleTexts.add(title);
        refresh();
    }

    /**
     * Marks the group whose settings are saved in the assessment's own
     * file, with a note under its title.
     *
     * @param group the group
     */
    public void forAssessment(SettingsGroup group) {
        assessmentGroup = group;
        group.markChanging(assessmentNote);
        refresh();
    }

    /**
     * Marks the group of the score sheets of the assessment in use, one
     * for each section of the module. Its title names that assessment,
     * which may not be the one shown under Assessment.
     *
     * @param group the group
     */
    public void forScoreSheets(SettingsGroup group) {
        scoreSheetsGroup = group;
        group.markChanging(scoreSheetsNote);
        refresh();
    }

    /**
     * Scrolls the cards to the group of the assessment.
     */
    public void showAssessmentGroup() {
        if (assessmentGroup != null) {
            jump.accept(assessmentGroup);
        }
    }

    /**
     * Lets a change of module or assessment light up the groups it
     * changes. Called once the page is built, so the first build does
     * not.
     */
    public void arm() {
        armed = true;
    }

    /**
     * Shows a module and the assessment being edited in the titles and
     * the notes. Once armed, lights up the groups of the
     * module when it changed, the assessment group when the assessment
     * shown changed, and the score sheets when either the module or the
     * assessment in use changed.
     *
     * @param module the module chosen under Course, or null for none
     * @param editing the assessment being edited, or null for none
     * @param creating true while a new assessment is filled in
     * @param inUse the name of the assessment in use, or "" for none
     */
    public void show(String module, String editing, boolean creating,
            String inUse) {
        boolean otherModule = !Objects.equals(module, this.module);
        boolean otherAssessment = !Objects.equals(editing, this.editing)
                || creating != this.creating;
        boolean otherInUse = !inUse.equalsIgnoreCase(this.inUse);
        this.module = module;
        this.editing = editing;
        this.creating = creating;
        this.inUse = inUse;
        refresh();
        if (!armed || !animated.getAsBoolean()) {
            return;
        }
        if (otherModule) {
            for (SettingsGroup group : moduleGroups) {
                group.flash();
            }
        }
        if (otherAssessment && assessmentGroup != null) {
            assessmentGroup.flash();
        }
        boolean sheetsChanged = otherModule || otherInUse;
        if (sheetsChanged && scoreSheetsGroup != null) {
            scoreSheetsGroup.flash();
        }
    }

    // Puts the module and the assessment in the titles and the notes.
    private void refresh() {
        String moduleText = NO_MODULE;
        if (module != null) {
            moduleText = module;
        }
        for (int i = 0; i < moduleNotes.size(); i++) {
            String note = moduleNoteTexts.get(i).replace(MODULE_MARK,
                    moduleText);
            moduleNotes.get(i).setText(note);
        }
        for (int i = 0; i < titledGroups.size(); i++) {
            titledGroups.get(i).setTitle(titleTexts.get(i) + NAME_SEPARATOR
                    + moduleText);
        }

        assessmentNote.setText(assessmentNoteText(moduleText));
        refreshScoreSheets();
    }

    // Names the assessment in use in the title of the score sheets, and
    // says in the note when it is not the one shown under Assessment.
    private void refreshScoreSheets() {
        if (scoreSheetsGroup == null) {
            return;
        }
        if (inUse.isEmpty()) {
            scoreSheetsGroup.setTitle(SCORE_SHEETS_TITLE);
            scoreSheetsNote.setText("No assessment is in use, so no score "
                    + "sheet is kept.");
            return;
        }
        scoreSheetsGroup.setTitle(SCORE_SHEETS_TITLE + NAME_SEPARATOR
                + inUse);
        boolean otherShown = editing != null && !creating && !isInUse();
        if (otherShown) {
            scoreSheetsNote.setText("These are " + inUse + "'s, the "
                    + "assessment in use, not " + editing + "'s.");
            return;
        }
        scoreSheetsNote.setText("Each assessment has its own class lists. "
                + "Switching assessment shows another's.");
    }

    // Returns the note under the assessment group's title.
    private String assessmentNoteText(String moduleText) {
        if (creating) {
            return "A new assessment for " + moduleText + ", saved in its "
                    + "own file.";
        }
        if (editing == null) {
            return moduleText + " has no assessment yet.";
        }
        return "This is " + editing + "'s own; switching assessment shows "
                + "another.";
    }

    // Says whether the assessment being edited is the one in use.
    private boolean isInUse() {
        return editing != null && editing.equalsIgnoreCase(inUse);
    }
}
