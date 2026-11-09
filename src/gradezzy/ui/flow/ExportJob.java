/*
 * File: ExportJob.java
 *
 * Purpose: Writes the score sheet CSV and, if chosen, the JSON file and
 * the reports of a run, on a background thread so the window does not
 * freeze.
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
import gradezzy.report.AnomalyReportExporter;
import gradezzy.report.AuditLogExporter;
import gradezzy.report.StudentReportExporter;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.util.TextUtils;

/**
 * A background task that exports the results of a run.
 *
 * <p>Tier: SHOULD.
 */
public class ExportJob extends Task<ActionResult> {

    // The format name of the score sheet, written first
    private static final String CSV_FORMAT = "csv";

    // Put between the names of the other files saved
    private static final String LIST_SEPARATOR = ", ";

    // The backend that writes the files
    private final DashboardBackend backend;

    // The run to export
    private final GradingRun run;

    // The students to export
    private final List<Submission> scope;

    // The other files to write after the CSV, by format name
    private final List<String> otherFormats;

    /**
     * Creates the export of a run.
     *
     * @param backend the backend that writes the files
     * @param run the run to export
     * @param scope the students to export
     * @param otherFormats the other files to write after the CSV, by
     *        format name, such as json or Student report
     */
    public ExportJob(DashboardBackend backend, GradingRun run,
            List<Submission> scope, List<String> otherFormats) {
        this.backend = backend;
        this.run = run;
        this.scope = new ArrayList<>(scope);
        this.otherFormats = new ArrayList<>(otherFormats);
    }

    /**
     * Writes the CSV, then each other file chosen. A file that fails
     * does not stop the ones after it.
     *
     * @return done with what was saved, or failed when the CSV failed
     */
    public ActionResult exportNow() {
        ActionResult csv = backend.export(run, scope, CSV_FORMAT);
        if (!csv.isDone()) {
            return csv;
        }
        String message = csv.getMessage();
        if (backend.isDemo() && !otherFormats.isEmpty()) {
            return ActionResult.done(message + " Demo mode saves no other "
                    + "file either.");
        }

        List<String> savedFiles = new ArrayList<>();
        for (String formatName : otherFormats) {
            ActionResult other = backend.export(run, scope, formatName);
            if (other.isDone()) {
                savedFiles.add(describe(formatName));
            } else {
                message = message + " Saving " + describe(formatName)
                        + " failed: " + other.getMessage();
            }
        }
        if (!savedFiles.isEmpty()) {
            String saved = TextUtils.join(LIST_SEPARATOR, savedFiles);
            message = message + " Also saved beside it: " + saved + ".";
        }
        return ActionResult.done(message);
    }

    // Names a file the way the Export step does, so a format name such
    // as Anomaly report never reaches the screen.
    private String describe(String formatName) {
        if (AnomalyReportExporter.FORMAT_NAME.equals(formatName)) {
            return "the list of problems";
        } else if (StudentReportExporter.FORMAT_NAME.equals(formatName)) {
            return "the report on each student";
        } else if (AuditLogExporter.FORMAT_NAME.equals(formatName)) {
            return "the activity log";
        }
        return "the " + formatName + " file";
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
