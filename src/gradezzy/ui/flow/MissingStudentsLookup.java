/*
 * File: MissingStudentsLookup.java
 *
 * Purpose: Reads a run's score sheet on a background thread and finds the
 * students on it who have no submission in the run.
 *
 * Where it fits: ReviewScreen and ExportScreen start one when a run is
 * shown and keep the result in FlowState, so the score sheet is read
 * once per run and never on the window's thread.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the lookup on a
 * background thread, as FileInspection does for the zip files.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.model.Student;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that lists the students with no submission.
 */
public class MissingStudentsLookup extends Task<List<Student>> {

    // The backend that reads the score sheet
    private final DashboardBackend backend;

    // The run whose students are looked up
    private final GradingRun run;

    /**
     * Creates the lookup for one run.
     *
     * @param backend the backend that reads the score sheet
     * @param run the run
     */
    public MissingStudentsLookup(DashboardBackend backend, GradingRun run) {
        this.backend = backend;
        this.run = run;
    }

    /**
     * Returns the run being looked up.
     *
     * @return the run
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Reads the score sheet. JavaFX calls this on a background thread.
     *
     * @return the students with no submission, in score sheet order
     */
    @Override
    protected List<Student> call() {
        return backend.listMissingStudents(run);
    }
}
