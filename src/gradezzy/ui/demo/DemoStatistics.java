/*
 * File: DemoStatistics.java
 *
 * Purpose: Works out the statistics of a demo run.
 *
 * Where it fits: Used only by DemoBackend.summarise; live mode uses
 * StatisticsService. Ungraded students are left out, never counted as zero.
 */
package gradezzy.ui.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.stats.MarkBand;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.ResultState;

/**
 * Statistics for a demo run.
 */
public final class DemoStatistics {

    // One hundred percent
    private static final double FULL_PERCENT = 100.0;

    // Two, for the middle of an even-length list
    private static final int HALF = 2;

    // The text in a compile error message before the reason
    private static final String COMPILE_PREFIX = "did not compile: ";

    // Utility class, never instantiated.
    private DemoStatistics() {
        // nothing to set up
    }

    /**
     * Works out the statistics of a run.
     *
     * @param run the run, or null
     * @param passMarkPercent the percentage that counts as a pass
     * @param bandWidthPercent the width of each mark band
     * @return the summary, or null when no student was graded
     */
    public static StatisticsSummary summarise(GradingRun run,
            double passMarkPercent, double bandWidthPercent) {
        if (run == null) {
            return null;
        }

        List<Submission> graded = new ArrayList<>();
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded.add(submission);
            }
        }
        if (graded.isEmpty()) {
            return null;
        }

        double maxTotal = run.getAssessment().getMaxTotal();
        List<Double> sortedTotals = new ArrayList<>();
        double sum = 0.0;
        int passed = 0;
        for (Submission submission : graded) {
            double total = submission.getTotalScore();
            sortedTotals.add(total);
            sum += total;
            if (isPass(total, maxTotal, passMarkPercent)) {
                passed++;
            }
        }
        Collections.sort(sortedTotals);

        double[] totals = new double[sortedTotals.size()];
        for (int index = 0; index < totals.length; index++) {
            totals[index] = sortedTotals.get(index);
        }

        StatisticsSummary summary = new StatisticsSummary();
        summary.setMean(sum / totals.length);
        summary.setMedian(median(totals));
        summary.setPassRate((double) passed / totals.length);
        addMarkBands(summary, totals, maxTotal, bandWidthPercent);
        addQuestionPassRates(summary, run, graded, passMarkPercent);
        summary.setMostFailedTest(mostFailedTest(graded));
        addCompileErrors(summary, graded);
        return summary;
    }

    // Says whether a mark reaches the pass mark.
    private static boolean isPass(double score, double max,
            double passMarkPercent) {
        return max > 0.0 && score / max * FULL_PERCENT >= passMarkPercent;
    }

    // Returns the middle of sorted values.
    private static double median(double[] sorted) {
        int middle = sorted.length / HALF;
        if (sorted.length % HALF == 1) {
            return sorted[middle];
        }
        return (sorted[middle - 1] + sorted[middle]) / HALF;
    }

    // Counts the students in each mark band; the last includes full marks.
    private static void addMarkBands(StatisticsSummary summary,
            double[] totals, double maxTotal, double bandWidthPercent) {
        for (double lower = 0.0; lower < FULL_PERCENT;
                lower += bandWidthPercent) {
            double upper = Math.min(lower + bandWidthPercent, FULL_PERCENT);
            int count = 0;
            for (double total : totals) {
                double percent = total / maxTotal * FULL_PERCENT;
                boolean inBand = percent >= lower && (percent < upper
                        || upper == FULL_PERCENT && percent <= upper);
                if (inBand) {
                    count++;
                }
            }
            summary.getMarkBands().add(new MarkBand(lower, upper, count));
        }
    }

    // Works out the share of students who passed each question.
    private static void addQuestionPassRates(StatisticsSummary summary,
            GradingRun run, List<Submission> graded,
            double passMarkPercent) {
        for (Question question : run.getAssessment().getQuestions()) {
            int passed = 0;
            for (Submission submission : graded) {
                QuestionResult result = find(submission, question.getId());
                if (result != null && isPass(result.getScore(),
                        question.getMaxScore(), passMarkPercent)) {
                    passed++;
                }
            }
            summary.getQuestionPassRates().put(question.getId(),
                    (double) passed / graded.size());
        }
    }

    // Finds the test case that failed most often, such as "Q2b case 4".
    private static String mostFailedTest(List<Submission> graded) {
        Map<String, Integer> failures = new HashMap<>();
        String worst = "";
        int worstCount = 0;
        for (Submission submission : graded) {
            for (QuestionResult result : submission.getQuestionResults()) {
                for (TestCaseResult testCase
                        : result.getTestCaseResults()) {
                    if (testCase.isPassed()) {
                        continue;
                    }
                    String name = result.getQuestion().getId() + " case "
                            + testCase.getTestNumber();
                    int count = 1;
                    if (failures.containsKey(name)) {
                        count = failures.get(name) + 1;
                    }
                    failures.put(name, count);
                    if (count > worstCount) {
                        worst = name;
                        worstCount = count;
                    }
                }
            }
        }
        if (worstCount == 0) {
            return "";
        }
        if (worstCount == 1) {
            return worst + " (failed by 1 student)";
        }
        return worst + " (failed by " + worstCount + " students)";
    }

    // Counts the compile error messages, by their reason.
    private static void addCompileErrors(StatisticsSummary summary,
            List<Submission> graded) {
        for (Submission submission : graded) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (anomaly.getType() != AnomalyType.COMPILE_ERROR) {
                    continue;
                }
                String reason = reasonOf(anomaly.getMessage());
                Map<String, Integer> errors =
                        summary.getCommonCompileErrors();
                int count = 0;
                if (errors.containsKey(reason)) {
                    count = errors.get(reason);
                }
                errors.put(reason, count + 1);
            }
        }
    }

    // Takes the reason out of a compile error message.
    private static String reasonOf(String message) {
        int start = message.indexOf(COMPILE_PREFIX);
        if (start < 0) {
            return message;
        }
        String rest = message.substring(start + COMPILE_PREFIX.length());
        int end = rest.indexOf('.');
        if (end < 0) {
            return rest;
        }
        return rest.substring(0, end);
    }

    // Finds the result of one question.
    private static QuestionResult find(Submission submission,
            String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }
}
