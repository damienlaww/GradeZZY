/*
 * File: CompileFailureCheck.java
 *
 * Purpose: Warns when a question did not compile for nearly every graded
 * student, such as Q3 for 44 of 45. That is almost never the students'
 * fault: a provided class file is missing from the assessment, or the
 * starter folder is the wrong one.
 *
 * Where it fits: GradingService calls it each time a run is saved. The
 * warning is added to every graded student with the same words, so
 * Review shows it once above the table as a problem they all share.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * Warns when a question did not compile for nearly every graded student.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class CompileFailureCheck {

    /**
     * What the instructor can do about the warning. It also tells this
     * check's warnings apart, so they can be replaced on the next save.
     */
    public static final String ACTION = "Check Provided class files and "
            + "Starter files of the assessment in Settings, then grade "
            + "again.";

    // A run needs at least this many graded students for the warning,
    // so two students who both got a question wrong raise nothing
    private static final int MIN_STUDENTS = 5;

    // The warning is given when at least this percentage of the graded
    // students' copies of a question did not compile
    private static final int MIN_PERCENT = 80;

    // One hundred percent
    private static final int ALL_PERCENT = 100;

    // The text in front of the id of every warning added here
    private static final String ID_PREFIX = "compile-check-";

    /**
     * Creates the check.
     *
     * <p>Tier: SHOULD.
     */
    public CompileFailureCheck() {
        // nothing to set up
    }

    /**
     * Replaces the warnings of an earlier check of the run with new ones:
     * one sentence per question that did not compile for nearly every
     * graded student, added to every graded student.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the run
     */
    public void refresh(GradingRun gradingRun) {
        removeWarnings(gradingRun);
        List<String> warnings = findWarnings(gradingRun);
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getStatus() == SubmissionStatus.GRADED) {
                for (String warning : warnings) {
                    submission.addAnomaly(new Anomaly(
                            ID_PREFIX + UUID.randomUUID(),
                            AnomalyType.INTERNAL_ERROR, Severity.WARNING,
                            submission.getUsername(), warning, ACTION));
                }
            }
        }
    }

    /**
     * Lists one sentence per question that did not compile for at least
     * MIN_PERCENT percent of the graded students, when at least
     * MIN_STUDENTS were graded.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the run
     * @return the sentences, such as "Q3 did not compile for 44 of 45
     *         graded students. ...", empty when nothing is wrong
     */
    public List<String> findWarnings(GradingRun gradingRun) {
        List<String> warnings = new ArrayList<>();
        List<Submission> graded = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getStatus() == SubmissionStatus.GRADED) {
                graded.add(submission);
            }
        }
        if (graded.size() < MIN_STUDENTS) {
            return warnings;
        }
        for (Question question : gradingRun.getAssessment().getQuestions()) {
            int failed = countNotCompiled(graded, question.getId());
            boolean nearlyAll = failed * ALL_PERCENT
                    >= graded.size() * MIN_PERCENT;
            if (nearlyAll) {
                warnings.add(question.getId() + " did not compile for "
                        + failed + " of " + graded.size() + " graded "
                        + "students. This is rarely the students' fault: "
                        + "check the provided class files and the starter "
                        + "folder.");
            }
        }
        return warnings;
    }

    // Counts the students whose copy of a question did not compile.
    private int countNotCompiled(List<Submission> graded, String questionId) {
        int failed = 0;
        for (Submission submission : graded) {
            QuestionResult result = submission.findResult(questionId);
            if (result != null && !result.isCompiled()) {
                failed++;
            }
        }
        return failed;
    }

    // Removes the warnings an earlier check added to the run.
    private void removeWarnings(GradingRun gradingRun) {
        for (Submission submission : gradingRun.getSubmissions()) {
            List<Anomaly> old = new ArrayList<>();
            for (Anomaly anomaly : submission.getAnomalies()) {
                boolean fromThisCheck = anomaly.getType()
                        == AnomalyType.INTERNAL_ERROR
                        && ACTION.equals(anomaly.getSuggestedAction());
                if (fromThisCheck) {
                    old.add(anomaly);
                }
            }
            submission.getAnomalies().removeAll(old);
        }
    }
}
