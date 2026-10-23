/*
 * File: ValidationFilters.java
 *
 * Purpose: The student and state filters of the Validation screen.
 *
 * Where it fits: ValidationController creates one when its FXML is loaded.
 * The screen is loaded once and kept, so the choices survive navigation.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.scene.control.ComboBox;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.component.Labels;

/**
 * The filters of the finding list.
 */
public final class ValidationFilters {

    // The words shown for "every student" in the student filter
    private static final String ALL_STUDENTS = "All students";

    // The words shown for "every state" in the state filter
    private static final String ALL_STATES = "All states";

    // The student filter
    private final ComboBox<String> studentFilter;

    // The repair state filter
    private final ComboBox<FixStatus> stateFilter;

    // Whether the choices are being set by code
    private boolean restoring = false;

    /**
     * Fills the state choices and an empty student list.
     *
     * @param studentFilter the student filter
     * @param stateFilter the repair state filter
     */
    public ValidationFilters(ComboBox<String> studentFilter,
            ComboBox<FixStatus> stateFilter) {
        this.studentFilter = studentFilter;
        this.stateFilter = stateFilter;
        ReviewChoices.fill(studentFilter, new ArrayList<>(), ALL_STUDENTS,
                username -> username);
        ReviewChoices.fill(stateFilter, Arrays.asList(FixStatus.APPLIED,
                FixStatus.APPROVED, FixStatus.UNDONE, FixStatus.NOT_FIXABLE),
                ALL_STATES, status -> Labels.fixLabel(status));
    }

    /**
     * Tells the screen when the instructor changes a filter.
     *
     * @param onChange reloads the table
     */
    public void listen(Runnable onChange) {
        studentFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        onChange.run();
                    }
                });
        stateFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        onChange.run();
                    }
                });
    }

    /**
     * Lists the students of a new run, keeping the chosen one if present.
     *
     * @param usernames the usernames of the run
     */
    public void fillStudents(List<String> usernames) {
        String chosen = studentFilter.getValue();
        restoring = true;
        ReviewChoices.fill(studentFilter, usernames, ALL_STUDENTS,
                username -> username);
        if (chosen != null && usernames.contains(chosen)) {
            studentFilter.setValue(chosen);
        } else {
            studentFilter.setValue(null);
        }
        restoring = false;
    }

    /**
     * Shows every student and every state again, without reloading.
     */
    public void clear() {
        restoring = true;
        studentFilter.setValue(null);
        stateFilter.setValue(null);
        restoring = false;
    }

    /**
     * Says whether a finding passes both filters.
     *
     * @param repair the finding
     * @return true when it is shown
     */
    public boolean matches(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        String student = studentFilter.getValue();
        FixStatus state = stateFilter.getValue();
        boolean studentMatches = student == null
                || student.equals(anomaly.getUsername());
        boolean stateMatches = state == null
                || state == anomaly.getFixStatus();
        return studentMatches && stateMatches;
    }
}
