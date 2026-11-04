/*
 * File: ScoreAdjustmentService.java
 *
 * Purpose: Changes a question's mark by hand, with a reason, and takes
 * the change back. Each change is kept with the earlier mark, and saved
 * with the run when results are saved, so it is there after a restart.
 *
 * Where it fits: LiveBackend calls it from Review's Adjust sheet and the
 * Undo of a changed mark. The student's total, Statistics and Export all
 * read the question's score, so they use the new mark at once.
 */
package gradezzy.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.QuestionResult;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Submission;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
import gradezzy.report.ScoreFormatter;

/**
 * Marks changed by hand.
 */
public class ScoreAdjustmentService {

    // Marks closer than this are the same mark
    private static final double SAME_MARK = 1e-9;

    // The store results are saved to, or null when there is none
    private final ResultRepository repository;

    // The application settings, which say whether results are saved
    private final AppConfig config;

    // The changes of each run, by run id, read once from the store
    private final Map<String, List<ScoreAdjustment>> byRun =
            new HashMap<>();

    /**
     * Creates the service.
     *
     * @param repository the store results are saved to, or null
     * @param config the application settings
     */
    public ScoreAdjustmentService(ResultRepository repository,
            AppConfig config) {
        this.repository = repository;
        this.config = config;
    }

    /**
     * Changes one question's mark of one student and keeps the change.
     *
     * @param run the run the student is in
     * @param submission the student
     * @param questionId the question, such as Q1b
     * @param newScore the new mark, from 0 to the question's maximum
     * @param reason why the mark changed; required
     * @return the change
     * @throws IllegalArgumentException if the mark or reason is not valid,
     *         or the question has no result
     */
    public ScoreAdjustment adjust(GradingRun run, Submission submission,
            String questionId, double newScore, String reason) {
        QuestionResult result = findResult(submission, questionId);
        if (result == null) {
            throw new IllegalArgumentException(questionId + " has no "
                    + "result to change. Grade the student first.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a reason for the "
                    + "change. It is kept with the new mark.");
        }
        double max = result.getQuestion().getMaxScore();
        if (Double.isNaN(newScore) || newScore < 0.0 || newScore > max) {
            throw new IllegalArgumentException("Enter a mark from 0 to "
                    + ScoreFormatter.format(max) + ".");
        }

        dropOutdated(run, submission);
        ScoreAdjustment change = new ScoreAdjustment(
                submission.getUsername(), questionId, result.getScore(),
                newScore, reason.trim(), LocalDateTime.now());
        result.setScore(newScore);
        changesOf(run).add(change);
        save(run, submission, change, true);
        return change;
    }

    /**
     * Takes a change back: the question gets its earlier mark again. Only
     * the newest change of a question can be taken back, so the marks
     * stay in order.
     *
     * @param run the run the student is in
     * @param change the change
     * @throws IllegalArgumentException if the student or question is no
     *         longer in the run, or a later change of it comes first
     */
    public void undo(GradingRun run, ScoreAdjustment change) {
        Submission submission = findSubmission(run, change.getUsername());
        if (submission == null) {
            throw new IllegalArgumentException("The student is no longer "
                    + "in this run.");
        }
        QuestionResult result = findResult(submission,
                change.getQuestionId());
        if (result == null) {
            throw new IllegalArgumentException("The question has no "
                    + "result any more.");
        }
        dropOutdated(run, submission);
        List<ScoreAdjustment> changes = changesOf(run);
        if (!changes.contains(change)) {
            throw new IllegalArgumentException(change.getQuestionId()
                    + " was graded again after this change, so its mark "
                    + "is no longer the changed one.");
        }
        ScoreAdjustment newest = null;
        for (ScoreAdjustment other : changes) {
            if (other.getUsername().equals(change.getUsername())
                    && other.getQuestionId().equals(
                    change.getQuestionId())) {
                newest = other;
            }
        }
        if (newest != change) {
            throw new IllegalArgumentException("Undo the later change of "
                    + change.getQuestionId() + " first.");
        }
        result.setScore(change.getPreviousScore());
        changes.remove(change);
        save(run, submission, change, false);
    }

    /**
     * Lists one student's changes in a run, oldest first.
     *
     * @param run the run
     * @param username the student
     * @return the changes
     */
    public List<ScoreAdjustment> list(GradingRun run, String username) {
        List<ScoreAdjustment> found = new ArrayList<>();
        if (run == null) {
            return found;
        }
        Submission submission = findSubmission(run, username);
        if (submission != null) {
            dropOutdated(run, submission);
        }
        for (ScoreAdjustment change : changesOf(run)) {
            if (change.getUsername().equals(username)) {
                found.add(change);
            }
        }
        return found;
    }

    // Returns a run's changes, reading them from the store the first
    // time.
    private List<ScoreAdjustment> changesOf(GradingRun run) {
        String id = run.getId() == null ? "" : run.getId();
        List<ScoreAdjustment> changes = byRun.get(id);
        if (changes == null) {
            changes = new ArrayList<>();
            if (isSaving() && !id.isEmpty()) {
                try {
                    changes.addAll(repository.listAdjustments(id));
                } catch (PersistenceException exception) {
                    // Results are not saved; only this session's show
                }
            }
            byRun.put(id, changes);
        }
        return changes;
    }

    // Forgets a student's changes of a question whose mark is no longer
    // the newest change's mark: the question was graded again, retried,
    // or a backup was put back, so the changes no longer describe it and
    // undoing one would put back a wrong mark.
    private void dropOutdated(GradingRun run, Submission submission) {
        List<ScoreAdjustment> changes = changesOf(run);
        Map<String, ScoreAdjustment> newest = new HashMap<>();
        for (ScoreAdjustment change : changes) {
            if (change.getUsername().equals(submission.getUsername())) {
                newest.put(change.getQuestionId(), change);
            }
        }
        for (Map.Entry<String, ScoreAdjustment> entry : newest.entrySet()) {
            QuestionResult result = findResult(submission, entry.getKey());
            if (result != null && Math.abs(result.getScore()
                    - entry.getValue().getNewScore()) < SAME_MARK) {
                continue;
            }
            List<ScoreAdjustment> outdated = new ArrayList<>();
            for (ScoreAdjustment change : changes) {
                if (change.getUsername().equals(submission.getUsername())
                        && change.getQuestionId().equals(entry.getKey())) {
                    outdated.add(change);
                }
            }
            changes.removeAll(outdated);
            if (isSaving() && run.getId() != null) {
                for (ScoreAdjustment change : outdated) {
                    try {
                        repository.deleteAdjustment(run.getId(), change);
                    } catch (PersistenceException exception) {
                        // Forgotten for this session; the store is broken
                    }
                }
            }
        }
    }

    // Saves the student's marks and the change, or removes the change.
    // When results are not saved, the change lasts until GradeZZY closes.
    private void save(GradingRun run, Submission submission,
            ScoreAdjustment change, boolean added) {
        if (!isSaving() || run.getId() == null) {
            return;
        }
        try {
            repository.saveSubmission(run.getId(), submission);
            if (added) {
                repository.saveAdjustment(run.getId(), change);
            } else {
                repository.deleteAdjustment(run.getId(), change);
            }
        } catch (PersistenceException exception) {
            // The mark is changed for this session; saving is off or broken
        }
    }

    // Says whether results are saved.
    private boolean isSaving() {
        return repository != null
                && config.getBoolean(ConfigKeys.DATABASE_ENABLED);
    }

    // Finds a student in a run.
    private Submission findSubmission(GradingRun run, String username) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }

    // Finds a question's result of a student.
    private QuestionResult findResult(Submission submission,
            String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion() != null
                    && result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }
}
