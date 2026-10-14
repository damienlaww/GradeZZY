/*
 * File: SearchService.java
 *
 * Purpose: Searches, filters and sorts the submissions of a run.
 *
 * Where it fits: Used by the results and anomaly screens.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * Searches, filters and sorts the submissions of a run.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class SearchService {

    /**
     * Creates a new search service.
     */
    public SearchService() {
        // nothing to set up yet
    }

    /**
     * Finds submissions whose username or name contains a keyword.
     *
     * @param gradingRun the grading run
     * @param keyword the keyword
     * @return the matching submissions
     */
    public List<Submission> search(GradingRun gradingRun, String keyword) {
        // TODO(Jing Yuan): implement: a case-insensitive match on username and
        //     names
        return new ArrayList<>();
    }

    /**
     * Finds submissions whose source contains a piece of code.
     *
     * @param gradingRun the grading run
     * @param codeFragment the code fragment
     * @return the matching submissions
     */
    public List<Submission> searchCode(GradingRun gradingRun,
            String codeFragment) {
        // TODO(Jing Yuan): implement: a text search of the source files in each
        //     working copy
        return new ArrayList<>();
    }

    /**
     * Keeps the submissions with a given status.
     *
     * @param gradingRun the grading run
     * @param status the status
     * @return the matching submissions
     */
    public List<Submission> filterByStatus(GradingRun gradingRun,
            SubmissionStatus status) {
        // TODO(Jing Yuan): implement: a filter on getStatus
        return new ArrayList<>();
    }

    /**
     * Keeps the submissions that have a given kind of anomaly.
     *
     * @param gradingRun the grading run
     * @param anomalyType the anomaly type
     * @return the matching submissions
     */
    public List<Submission> filterByAnomalyType(GradingRun gradingRun,
            AnomalyType anomalyType) {
        // TODO(Jing Yuan): implement: a filter on the anomaly types of each
        //     submission
        return new ArrayList<>();
    }

    /**
     * Sorts submissions by total score.
     *
     * @param submissions the submissions
     * @param ascending the ascending
     * @return a new sorted list
     */
    public List<Submission> sortByScore(List<Submission> submissions,
            boolean ascending) {
        // TODO(Jing Yuan): implement: a sort on getTotalScore in the requested
        //     order
        return new ArrayList<>();
    }
}
