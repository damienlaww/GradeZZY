/*
 * File: ScoreAdjustmentService.java
 *
 * Purpose: Changes a question's mark by hand, with a reason, and takes
 * the change back. Each change is kept with the earlier mark, and saved
 * with the run when results are saved, so it is there after a restart.
 *
 * Where it fits: LiveBackend calls it from Review's Adjust sheet and the
 * Undo of a changed mark. The student's total, Statistics and Export all
 * read the question's score, so they use the new mark at once. The
 * methods are synchronized, so a grading thread and the window can call
 * them together.
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

    // The question result each change was made on or last checked
    // against. Grading again makes new results, so a change whose result
    // was replaced is outdated even when the new mark happens to match.
    private final Map<ScoreAdjustment, QuestionResult> resultOf =
            new HashMap<>();

    // Why the last change could not be saved, or "" when it was
    private String saveProblem = "";

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
    public synchronized ScoreAdjustment adjust(GradingRun run,
            Submission submission, String questionId, double newScore,
            String reason) {
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
        resultOf.put(change, result);
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
    public synchronized void undo(GradingRun run, ScoreAdjustment change) {
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
        resultOf.remove(change);
        save(run, submission, change, false);
    }

    /**
     * Lists one student's changes in a run, oldest first.
     *
     * @param run the run
     * @param username the student
     * @return the changes
     */
    public synchronized List<ScoreAdjustment> list(GradingRun run,
            String username) {
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

    /**
     * Forgets every change read so far, so each run's changes are read
     * from the store again. Called after a backup is put back, because
     * the store then holds other changes.
     */
    public synchronized void forgetAll() {
        byRun.clear();
        resultOf.clear();
    }

    /**
     * Says why the last change or undo could not be saved.
     *
     * @return the reason, or "" when it was saved or saving is off
     */
    public synchronized String getSaveProblem() {
        return saveProblem;
    }

    /**
     * Says whether changes are saved with the results, so they are kept
     * after a restart.
     *
     * @return true when results are saved
     */
    public boolean isSaving() {
        return repository != null
                && config.getBoolean(ConfigKeys.DATABASE_ENABLED);
    }

    // Returns a run's changes, reading them from the store the first
    // time. If they cannot be read, only this session's changes show.
    private List<ScoreAdjustment> changesOf(GradingRun run) {
        String id = "";
        if (run.getId() != null) {
            id = run.getId();
        }
        List<ScoreAdjustment> changes = byRun.get(id);
        if (changes == null) {
            changes = new ArrayList<>();
            if (isSaving() && !id.isEmpty()) {
                try {
                    changes.addAll(repository.listAdjustments(id));
                } catch (PersistenceException exception) {
                    saveProblem = exception.getMessage();
                }
            }
            byRun.put(id, changes);
        }
        return changes;
    }

    // Forgets a student's changes of a question that was graded again,
    // retried, or put back from a backup since its newest change: its
    // result was replaced, or its mark is no longer the change's mark.
    // The changes no longer describe it, and undoing one would put back
    // a mark the files did not earn.
    private void dropOutdated(GradingRun run, Submission submission) {
        List<ScoreAdjustment> changes = changesOf(run);
        Map<String, ScoreAdjustment> newest = new HashMap<>();
        for (ScoreAdjustment change : changes) {
            if (change.getUsername().equals(submission.getUsername())) {
                newest.put(change.getQuestionId(), change);
            }
        }
        for (String questionId : newest.keySet()) {
            ScoreAdjustment change = newest.get(questionId);
            QuestionResult result = findResult(submission, questionId);
            if (!isOutdated(change, result)) {
                // A change read from the store is tied to the result it
                // describes the first time it is checked
                resultOf.put(change, result);
            } else {
                dropQuestion(run, submission.getUsername(), questionId);
            }
        }
    }

    // Says whether a question's newest change no longer describes its
    // result.
    private boolean isOutdated(ScoreAdjustment change,
            QuestionResult result) {
        if (result == null) {
            return true;
        }
        QuestionResult madeOn = resultOf.get(change);
        if (madeOn != null && madeOn != result) {
            return true;
        }
        return Math.abs(result.getScore() - change.getNewScore())
                >= SAME_MARK;
    }

    // Forgets every change of one question of a student, here and in the
    // store.
    private void dropQuestion(GradingRun run, String username,
            String questionId) {
        List<ScoreAdjustment> changes = changesOf(run);
        List<ScoreAdjustment> outdated = new ArrayList<>();
        for (ScoreAdjustment change : changes) {
            if (change.getUsername().equals(username)
                    && change.getQuestionId().equals(questionId)) {
                outdated.add(change);
            }
        }
        changes.removeAll(outdated);
        for (ScoreAdjustment change : outdated) {
            resultOf.remove(change);
        }
        if (!isSaving() || run.getId() == null) {
            return;
        }
        for (ScoreAdjustment change : outdated) {
            try {
                repository.deleteAdjustment(run.getId(), change);
            } catch (PersistenceException exception) {
                // Forgotten for this session; the store may list it again
                saveProblem = exception.getMessage();
            }
        }
    }

    // Saves the student's marks and the change, or removes the change.
    // When results are not saved, the change lasts until GradeZZY closes;
    // when saving fails, getSaveProblem says why.
    private void save(GradingRun run, Submission submission,
            ScoreAdjustment change, boolean added) {
        saveProblem = "";
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
            saveProblem = exception.getMessage();
        }
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
