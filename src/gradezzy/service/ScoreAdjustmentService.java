/*
 * File: ScoreAdjustmentService.java
 *
 * Purpose: Changes a question's mark by hand, with a reason, and takes
 * the change back. Each change is kept with the earlier mark, and saved
 * with the run when results are saved, so it is there after a restart.
 * Grading a student or a question again from Review puts the marks set by
 * hand back on the new results (keepAfterRegrade).
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
 * Changes marks by hand, undoes those changes and keeps them saved.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
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
     * <p>Tier: SHOULD.
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
     * <p>Tier: SHOULD.
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
        QuestionResult result = submission.findResult(questionId);
        if (result == null) {
            throw new IllegalArgumentException(questionId + " has no "
                    + "result to change. Grade the student first.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a reason for the "
                    + "change. It is kept with the new mark.");
        }
        double max = result.getQuestion().getMaxScore();
        boolean outOfRange = Double.isNaN(newScore) || newScore < 0.0
                || newScore > max;
        if (outOfRange) {
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
     * <p>Tier: SHOULD.
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
        QuestionResult result = submission.findResult(
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
        ScoreAdjustment newest = findNewest(changes, change.getUsername(),
                change.getQuestionId());
        if (newest != change) {
            throw new IllegalArgumentException("Undo the later change of "
                    + change.getQuestionId() + " first.");
        }
        result.setScore(change.getPreviousScore());
        changes.remove(change);
        resultOf.remove(change);
        save(run, submission, change, false);
    }

    // Returns the newest change of one question of a student, or null.
    private ScoreAdjustment findNewest(List<ScoreAdjustment> changes,
            String username, String questionId) {
        ScoreAdjustment newest = null;
        for (ScoreAdjustment other : changes) {
            if (isChangeOf(other, username, questionId)) {
                newest = other;
            }
        }
        return newest;
    }

    // Says whether a change is of one question of a student.
    private boolean isChangeOf(ScoreAdjustment change, String username,
            String questionId) {
        return change.getUsername().equals(username)
                && change.getQuestionId().equals(questionId);
    }

    /**
     * Lists one student's changes in a run, oldest first.
     *
     * <p>Tier: SHOULD.
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
     * Lists the marks a student has set by hand now: the newest change of
     * each question that still describes its result. Take this list
     * before grading the student again, and pass it to keepAfterRegrade.
     *
     * <p>Tier: SHOULD.
     *
     * @param run the run
     * @param submission the student
     * @return the newest change of each changed question, in the order
     *         the questions were first changed
     */
    public synchronized List<ScoreAdjustment> listNewest(GradingRun run,
            Submission submission) {
        List<ScoreAdjustment> newest = new ArrayList<>();
        List<String> questionIds = new ArrayList<>();
        for (ScoreAdjustment change : list(run, submission.getUsername())) {
            String questionId = change.getQuestionId();
            int place = questionIds.indexOf(questionId);
            if (place < 0) {
                questionIds.add(questionId);
                newest.add(change);
            } else {
                newest.set(place, change);
            }
        }
        return newest;
    }

    /**
     * Puts the marks set by hand back after a student, or one question of
     * the student, was graded again, so grading again never undoes the
     * instructor's decision. For each change, the question's new result
     * gets the mark set by hand, and the change is kept with the new
     * grader's mark as its earlier mark, so Undo gives the mark the files
     * earn now. A question with no result after grading (the student
     * could not be graded) keeps nothing, as before.
     *
     * <p>Tier: SHOULD.
     *
     * @param run the run the student is in
     * @param submission the student, graded again
     * @param handMarks the changes from listNewest, taken before grading
     * @return the changes kept, one per question
     */
    public synchronized List<ScoreAdjustment> keepAfterRegrade(
            GradingRun run, Submission submission,
            List<ScoreAdjustment> handMarks) {
        List<ScoreAdjustment> kept = new ArrayList<>();
        for (ScoreAdjustment change : handMarks) {
            String questionId = change.getQuestionId();
            QuestionResult result = submission.findResult(questionId);
            // The old changes of the question describe the old result
            dropQuestion(run, submission.getUsername(), questionId);
            if (result != null) {
                ScoreAdjustment again = new ScoreAdjustment(
                        submission.getUsername(), questionId,
                        result.getScore(), change.getNewScore(),
                        change.getReason(), change.getSavedAt());
                result.setScore(change.getNewScore());
                changesOf(run).add(again);
                resultOf.put(again, result);
                kept.add(again);
            }
        }
        saveKept(run, submission, kept);
        return kept;
    }

    // Saves the student's marks and each change kept after grading again.
    private void saveKept(GradingRun run, Submission submission,
            List<ScoreAdjustment> kept) {
        saveProblem = "";
        boolean nothingToSave = kept.isEmpty() || !isSaving()
                || run.getId() == null;
        if (nothingToSave) {
            return;
        }
        try {
            repository.saveSubmission(run.getId(), submission);
            for (ScoreAdjustment change : kept) {
                repository.saveAdjustment(run.getId(), change);
            }
        } catch (PersistenceException exception) {
            saveProblem = exception.getMessage();
        }
    }

    /**
     * Forgets every change read so far, so each run's changes are read
     * from the store again. Called after a backup is put back, because
     * the store then holds other changes.
     *
     * <p>Tier: SHOULD.
     */
    public synchronized void forgetAll() {
        byRun.clear();
        resultOf.clear();
    }

    /**
     * Says why the last change or undo could not be saved.
     *
     * <p>Tier: SHOULD.
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
     * <p>Tier: SHOULD.
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
        // The keys of newest, in the order the questions first appear
        List<String> questionIds = new ArrayList<>();
        for (ScoreAdjustment change : changes) {
            if (change.getUsername().equals(submission.getUsername())) {
                String questionId = change.getQuestionId();
                if (!questionIds.contains(questionId)) {
                    questionIds.add(questionId);
                }
                newest.put(questionId, change);
            }
        }
        for (String questionId : questionIds) {
            ScoreAdjustment change = newest.get(questionId);
            QuestionResult result = submission.findResult(questionId);
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
        double difference = result.getScore() - change.getNewScore();
        return Math.abs(difference) >= SAME_MARK;
    }

    // Forgets every change of one question of a student, here and in the
    // store.
    private void dropQuestion(GradingRun run, String username,
            String questionId) {
        List<ScoreAdjustment> changes = changesOf(run);
        List<ScoreAdjustment> outdated = new ArrayList<>();
        for (ScoreAdjustment change : changes) {
            if (isChangeOf(change, username, questionId)) {
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
}
