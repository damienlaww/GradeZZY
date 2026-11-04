/*
 * File: ScoreSheetChange.java
 *
 * Purpose: Gives a graded run another score sheet on a background
 * thread, so the marks go to the right sheet without grading again.
 *
 * Where it fits: ExportScreen starts one when "Use this file instead"
 * is pressed. DashboardBackend.changeScoreSheet matches the students to
 * the new sheet and saves the run.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the change on a
 * background thread, as ExportJob does for the export.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that changes the score sheet of a run.
 */
public class ScoreSheetChange extends Task<ActionResult> {

    // The backend that changes the sheet
    private final DashboardBackend backend;

    // The run to change
    private final GradingRun run;

    // The score sheet to use from now on
    private final Path sheet;

    /**
     * Creates the change of a run's score sheet.
     *
     * @param backend the backend that changes the sheet
     * @param run the run
     * @param sheet the score sheet to use from now on
     */
    public ScoreSheetChange(DashboardBackend backend, GradingRun run,
            Path sheet) {
        this.backend = backend;
        this.run = run;
        this.sheet = sheet;
    }

    /**
     * Changes the sheet. The demo calls this directly.
     *
     * @return done, or failed with the reason
     */
    public ActionResult changeNow() {
        return backend.changeScoreSheet(run, sheet);
    }

    /**
     * Changes the sheet. JavaFX calls this on a background thread.
     *
     * @return done, or failed with the reason
     */
    @Override
    protected ActionResult call() {
        return changeNow();
    }
}
