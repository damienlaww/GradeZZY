/*
 * File: SectionProgress.java
 *
 * Purpose: Says how far a section has got with an assessment: which run
 * is shown for it, how many students wait to be graded or need a look,
 * the Grading step it needs next and a few words for its state, such as
 * "Not graded" or "5 to check".
 *
 * Where it fits: Home's section cards, the section picker of the
 * Grading tab and the Continue card on Set up all read a section's state
 * here, so they always agree.
 */
package gradezzy.ui.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RunRecord;
import gradezzy.ui.backend.StatusCounts;

/**
 * Reads how far a section has got.
 *
 * <p>Tier: SHOULD.
 */
public final class SectionProgress {

    // Utility class, never instantiated.
    private SectionProgress() {
        // nothing to set up
    }

    /**
     * Finds the run shown for each section of a module and an
     * assessment: the current one, else the newest.
     *
     * @param records the runs, newest first
     * @param module the module, such as CS102
     * @param assessmentName the assessment, such as Lab Test 2
     * @return the run of each section code that has one
     */
    public static Map<String, GradingRun> findRuns(List<RunRecord> records,
            String module, String assessmentName) {
        Map<String, GradingRun> runs = new HashMap<>();
        Map<String, Boolean> fromCurrent = new HashMap<>();
        for (RunRecord record : records) {
            GradingRun run = record.getRun();
            Section section = run.getSection();
            boolean matches = section != null
                    && module.equalsIgnoreCase(section.getModuleCode())
                    && isGradedWith(run, assessmentName);
            String code = "";
            if (matches) {
                code = section.getSectionCode();
            }
            boolean better = matches && (!runs.containsKey(code)
                    || (record.isCurrent() && !fromCurrent.get(code)));
            if (better) {
                runs.put(code, run);
                fromCurrent.put(code, record.isCurrent());
            }
        }
        return runs;
    }

    /**
     * Finds the run shown for each section as findRuns does, except that
     * the run opened on Review is shown for its own section when it is
     * of that module and assessment, so Open shows an earlier run.
     *
     * @param records the runs, newest first
     * @param module the module, such as CS102
     * @param assessmentName the assessment, such as Lab Test 2
     * @param opened the run open on Review, or null
     * @return the run of each section code that has one
     */
    public static Map<String, GradingRun> findShownRuns(
            List<RunRecord> records, String module, String assessmentName,
            GradingRun opened) {
        Map<String, GradingRun> runs = findRuns(records, module,
                assessmentName);
        boolean openedFits = opened != null && opened.getSection() != null
                && module.equalsIgnoreCase(
                        opened.getSection().getModuleCode())
                && isGradedWith(opened, assessmentName);
        if (openedFits) {
            runs.put(opened.getSection().getSectionCode(), opened);
        }
        return runs;
    }

    /**
     * Says whether a run was graded with an assessment.
     *
     * @param run the run
     * @param assessmentName the assessment's name
     * @return true when the names match, in any case
     */
    public static boolean isGradedWith(GradingRun run,
            String assessmentName) {
        Assessment assessment = run.getAssessment();
        return assessment != null && assessmentName != null
                && assessmentName.equalsIgnoreCase(assessment.getName());
    }

    /**
     * Counts the students of a run not graded yet, such as those
     * cancelled before their turn, or left RUNNING when GradeZZY stopped
     * while grading them. These are the students a resume grades, as
     * GradingService decides; a submission that could not be opened is
     * not waiting.
     *
     * @param run the run
     * @return how many wait to be graded
     */
    public static int countWaiting(GradingRun run) {
        int waiting = 0;
        for (Submission submission : run.getSubmissions()) {
            boolean notGraded = submission.getIngestFailure() == null
                    && GradingJob.isUnfinished(submission);
            if (notGraded) {
                waiting++;
            }
        }
        return waiting;
    }

    /**
     * Counts the students who need the instructor: open items, or a
     * submission that could not be graded or did not compile.
     *
     * @param run the run
     * @param backend the backend, which knows the review rules
     * @return how many need a look
     */
    public static int countToCheck(GradingRun run, DashboardBackend backend) {
        return StatusCounts.of(run.getSubmissions(), backend).getNeedsLook();
    }

    /**
     * Returns the Grading step a section needs: Grade while it is being
     * graded or stopped early, Set up before it is graded, Review while
     * students need a look, and Export once nothing is left to check,
     * also after an export, to export again or see where it went.
     *
     * @param state what the window remembers
     * @param backend the backend
     * @param section the section
     * @param run the run shown for it, or null
     * @return the step to open
     */
    public static Step stepToOpen(FlowState state, DashboardBackend backend,
            Section section, GradingRun run) {
        if (state.isGrading(section)) {
            return Step.GRADE;
        } else if (run == null) {
            return Step.SET_UP;
        } else if (countWaiting(run) > 0) {
            return Step.GRADE;
        } else if (countToCheck(run, backend) > 0) {
            return Step.REVIEW;
        }
        return Step.EXPORT;
    }

    /**
     * Describes where a section is in a few words, as Home's pills do:
     * "Grading 12 of 65", "Not graded", "Stopped", "5 to check",
     * "Exported" or "Graded".
     *
     * @param state what the window remembers
     * @param backend the backend
     * @param section the section
     * @param run the run shown for it, or null
     * @return the words
     */
    public static String describe(FlowState state, DashboardBackend backend,
            Section section, GradingRun run) {
        if (state.isGrading(section)) {
            return describeGrading(state, section);
        } else if (run == null) {
            return "Not graded";
        } else if (countWaiting(run) > 0) {
            return "Stopped";
        } else if (countToCheck(run, backend) > 0) {
            return countToCheck(run, backend) + " to check";
        } else if (state.isExported(run)) {
            return "Exported";
        }
        return "Graded";
    }

    /**
     * Says how far a section's batch has got, such as "Grading 12 of
     * 65", or "Grading now" before it knows how many it grades.
     *
     * @param state what the window remembers
     * @param section the section being graded
     * @return the words
     */
    public static String describeGrading(FlowState state, Section section) {
        GradingJob job = state.getJob(section);
        if (job == null || job.getTotal() <= 0) {
            return "Grading now";
        }
        return "Grading " + job.getFinished() + " of " + job.getTotal();
    }

    /**
     * Returns the tone of a section's state: good while grading or when
     * graded, a warning when stopped or with students to check, and
     * neutral before grading or after an export.
     *
     * @param state what the window remembers
     * @param backend the backend
     * @param section the section
     * @param run the run shown for it, or null
     * @return the tone
     */
    public static Tone toneOf(FlowState state, DashboardBackend backend,
            Section section, GradingRun run) {
        if (state.isGrading(section)) {
            return Tone.GOOD;
        } else if (run == null) {
            return Tone.NEUTRAL;
        } else if (countWaiting(run) > 0) {
            return Tone.WARN;
        } else if (countToCheck(run, backend) > 0) {
            return Tone.WARN;
        } else if (state.isExported(run)) {
            return Tone.NEUTRAL;
        }
        return Tone.GOOD;
    }
}
