/*
 * File: CodeSearch.java
 *
 * Purpose: Searches the code of every student in a run on a background
 * thread, so the window does not freeze while their files are read.
 *
 * Where it fits: ReviewScreen starts one in live mode when the search
 * looks inside the code, and filters the table when it succeeds.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the search on a
 * background thread, as SimilarityCheck does for the similar code check.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that finds the students whose code holds some text.
 */
public class CodeSearch extends Task<List<Submission>> {

    // The backend that reads the code
    private final DashboardBackend backend;

    // The run whose students are searched
    private final GradingRun run;

    // The text to find
    private final String text;

    /**
     * Creates the search of one run.
     *
     * @param backend the backend that reads the code
     * @param run the run whose students are searched
     * @param text the text to find
     */
    public CodeSearch(DashboardBackend backend, GradingRun run,
            String text) {
        this.backend = backend;
        this.run = run;
        this.text = text;
    }

    /**
     * Finds the students. JavaFX calls this on a background thread.
     *
     * @return the students whose code holds the text
     */
    @Override
    protected List<Submission> call() {
        return backend.searchCode(run, text);
    }
}
