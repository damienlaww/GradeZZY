/*
 * File: ExportJob.java
 *
 * Purpose: Writes the score sheet CSV and, if chosen, the JSON file of a
 * run, on a background thread so the window does not freeze.
 *
 * Where it fits: ExportScreen starts one in live mode when Export is
 * pressed, and shows the outcome when it succeeds. The demo calls
 * exportNow directly, as it writes no file.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the export on a
 * background thread, as SimilarityCheck does for the similar code check.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that exports the results of a run.
 */
public class ExportJob extends Task<ActionResult> {

    // The backend that writes the files
    private final DashboardBackend backend;

    // The run to export
    private final GradingRun run;

    // The students to export
    private final List<Submission> scope;

    // True to write the JSON file as well
    private final boolean withJson;

    /**
     * Creates the export of a run.
     *
     * @param backend the backend that writes the files
     * @param run the run to export
     * @param scope the students to export
     * @param withJson true to write the JSON file as well
     */
    public ExportJob(DashboardBackend backend, GradingRun run,
            List<Submission> scope, boolean withJson) {
        this.backend = backend;
        this.run = run;
        this.scope = new ArrayList<>(scope);
        this.withJson = withJson;
    }

    /**
     * Writes the CSV and, if chosen, the JSON file.
     *
     * @return done with what was saved, or failed when the CSV failed
     */
    public ActionResult exportNow() {
        ActionResult csv = backend.export(run, scope, "csv");
        if (!csv.isDone()) {
            return csv;
        }
        String message = csv.getMessage();
        if (withJson) {
            ActionResult jsonResult = backend.export(run, scope, "json");
            if (!jsonResult.isDone()) {
                message = message + " The JSON file failed: "
                        + jsonResult.getMessage();
            }
        }
        return ActionResult.done(message);
    }

    /**
     * Exports. JavaFX calls this on a background thread.
     *
     * @return the outcome
     */
    @Override
    protected ActionResult call() {
        return exportNow();
    }
}
