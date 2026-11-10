/*
 * File: ScoreSheetSearch.java
 *
 * Purpose: Looks near a graded run's score sheet, on a background
 * thread, for one that lists more of its graded students.
 *
 * Where it fits: ExportScreen starts one when most graded students are
 * not on the score sheet, and offers the sheet it finds with "Use this
 * file instead". DashboardBackend.findBetterScoreSheet does the work.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the search on a
 * background thread, as ClassFileMatchCheck does on Set up.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.service.ClassFileMatch;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that looks for a better score sheet for a run.
 *
 * <p>Tier: SHOULD.
 */
public class ScoreSheetSearch extends Task<ClassFileMatch> {

    // The backend that reads the files
    private final DashboardBackend backend;

    // The run whose score sheet is checked
    private final GradingRun run;

    // The run's score sheet when the search began
    private final Path sheet;

    /**
     * Creates the search for one run.
     *
     * @param backend the backend that reads the files
     * @param run the run
     */
    public ScoreSheetSearch(DashboardBackend backend, GradingRun run) {
        this.backend = backend;
        this.run = run;
        this.sheet = run.getScoreSheetFile();
    }

    /**
     * Says whether this search is for a run and the score sheet it has
     * now.
     *
     * @param other the run
     * @return true when the run and its sheet are the ones searched
     */
    public boolean isFor(GradingRun other) {
        if (other != run) {
            return false;
        }
        if (sheet == null) {
            return run.getScoreSheetFile() == null;
        }
        return sheet.equals(run.getScoreSheetFile());
    }

    /**
     * Looks for the sheet. The demo calls this directly.
     *
     * @return the better sheet, or ClassFileMatch.fits()
     */
    public ClassFileMatch search() {
        return backend.findBetterScoreSheet(run);
    }

    /**
     * Looks for the sheet. JavaFX calls this on a background thread.
     *
     * @return the better sheet, or ClassFileMatch.fits()
     */
    @Override
    protected ClassFileMatch call() {
        return search();
    }
}
