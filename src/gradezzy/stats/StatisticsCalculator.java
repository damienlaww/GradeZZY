/*
 * File: StatisticsCalculator.java
 *
 * Purpose: Computes the statistics of one or more grading runs.
 *
 * Where it fits: Pure calculation with no user interface, so it can be tested
 * from the console.
 */
package gradezzy.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;

/**
 * Computes the statistics of one or more grading runs.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class StatisticsCalculator {

    // One hundred percent
    private static final double FULL_PERCENT = 100.0;

    // Two, for the middle of an even-length list
    private static final int HALF = 2;

    // The compiler writes this just before the reason of an error
    private static final String COMPILER_ERROR_MARKER = "error: ";

    // The reason ends at the first full stop followed by a space
    private static final String REASON_END = ". ";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new statistics calculator.
     *
     * @param config the application settings
     */
    public StatisticsCalculator(AppConfig config) {
        this.config = config;
    }

    /**
     * Builds the full summary for the runs that match a filter.
     *
     * <p>Only the newest run of each module and section counts, so a
     * section graded twice is not counted twice. Only graded submissions
     * with results count.
     *
     * @param gradingRuns the grading runs
     * @param filter the filter
     * @return the summary; empty values when no run matches
     */
    public StatisticsSummary summarise(List<GradingRun> gradingRuns,
            StatisticsFilter filter) {
        StatisticsSummary summary = new StatisticsSummary();
        if (gradingRuns == null) {
            return summary;
        }

        List<GradingRun> runs = newestRunPerSection(gradingRuns, filter);
        List<Submission> graded = new ArrayList<>();
        List<Double> totals = new ArrayList<>();
        List<Double> percents = new ArrayList<>();
        for (GradingRun run : runs) {
            double maxTotal = run.getAssessment().getMaxTotal();
            for (Submission submission : run.getSubmissions()) {
                if (isGraded(submission)) {
                    graded.add(submission);
                    totals.add(submission.getTotalScore());
                    percents.add(toPercent(submission.getTotalScore(),
                            maxTotal));
                }
            }
        }
        if (graded.isEmpty()) {
            return summary;
        }

        summary.setMean(mean(totals));
        summary.setMedian(median(totals));
        // Percentages let runs with different maximums share one scale
        summary.setPassRate(passRate(percents, FULL_PERCENT));
        summary.getMarkBands().addAll(markBands(percents, FULL_PERCENT));
        addQuestionPassRates(summary, runs, graded);
        summary.setMostFailedTest(mostFailedTest(graded));
        addCompileErrors(summary, graded);
        return summary;
    }

    /**
     * Returns the mean of a list of scores.
     *
     * @param scores the scores
     * @return the mean, or 0 for an empty list
     */
    public double mean(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (double score : scores) {
            sum += score;
        }
        return sum / scores.size();
    }

    /**
     * Returns the median of a list of scores.
     *
     * @param scores the scores
     * @return the median, or 0 for an empty list
     */
    public double median(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }

        // Sort a copy so the caller's list keeps its order
        List<Double> sorted = new ArrayList<>(scores);
        Collections.sort(sorted);
        int middle = sorted.size() / HALF;
        if (sorted.size() % HALF == 1) {
            return sorted.get(middle);
        }
        int beforeMiddle = middle - 1;
        return (sorted.get(beforeMiddle) + sorted.get(middle)) / HALF;
    }

    /**
     * Returns the share of scores at or above the pass mark.
     *
     * @param scores the scores
     * @param maxScore the max score
     * @return the pass rate, from 0 to 1
     */
    public double passRate(List<Double> scores, double maxScore) {
        boolean nothingToRate = scores == null || scores.isEmpty()
                || maxScore <= 0.0;
        if (nothingToRate) {
            return 0.0;
        }

        int passed = 0;
        for (double score : scores) {
            if (isPass(score, maxScore)) {
                passed++;
            }
        }
        return (double) passed / scores.size();
    }

    /**
     * Groups scores into bands of equal width.
     *
     * <p>Each band holds the scores from its lower end up to, but not
     * including, its upper end. The last band also holds the full mark.
     *
     * @param scores the scores
     * @param maxScore the max score
     * @return the bands, lowest first
     */
    public List<MarkBand> markBands(List<Double> scores, double maxScore) {
        List<MarkBand> bands = new ArrayList<>();
        double bandWidth =
                config.getDouble(ConfigKeys.MARK_BAND_WIDTH_PERCENT);
        boolean nothingToBand = scores == null || maxScore <= 0.0
                || bandWidth <= 0.0;
        if (nothingToBand) {
            return bands;
        }

        // Counting bands with a whole number avoids adding up rounding
        // errors, for example when the width is 33.3
        double bandsNeeded = FULL_PERCENT / bandWidth;
        int bandCount = (int) Math.ceil(bandsNeeded);
        if (bandCount < 1) {
            return bands;
        }

        int lastIndex = bandCount - 1;
        for (int bandIndex = 0; bandIndex < lastIndex; bandIndex++) {
            double lower = bandIndex * bandWidth;
            double nextLower = lower + bandWidth;
            double upper = Math.min(nextLower, FULL_PERCENT);
            int studentCount = countBelow(scores, maxScore, lower, upper);
            bands.add(new MarkBand(lower, upper, studentCount));
        }

        // The last band also holds the full mark
        double lastLower = lastIndex * bandWidth;
        double pastLast = lastLower + bandWidth;
        double lastUpper = Math.min(pastLast, FULL_PERCENT);
        int lastCount = countUpTo(scores, maxScore, lastLower, lastUpper);
        bands.add(new MarkBand(lastLower, lastUpper, lastCount));
        return bands;
    }

    // Counts the scores from lower up to, but not including, upper
    private int countBelow(List<Double> scores, double maxScore,
            double lower, double upper) {
        int studentCount = 0;
        for (double score : scores) {
            double percent = toPercent(score, maxScore);
            if (percent >= lower && percent < upper) {
                studentCount++;
            }
        }
        return studentCount;
    }

    // Counts the scores from lower up to and including upper
    private int countUpTo(List<Double> scores, double maxScore,
            double lower, double upper) {
        int studentCount = 0;
        for (double score : scores) {
            double percent = toPercent(score, maxScore);
            if (percent >= lower && percent <= upper) {
                studentCount++;
            }
        }
        return studentCount;
    }

    // Keeps the runs that match the filter, then only the newest run of
    // each module and section
    private List<GradingRun> newestRunPerSection(List<GradingRun> gradingRuns,
            StatisticsFilter filter) {
        Map<String, GradingRun> newestBySection = new HashMap<>();
        List<String> sectionOrder = new ArrayList<>();
        for (GradingRun run : gradingRuns) {
            boolean counted = run != null && run.getAssessment() != null
                    && run.getSection() != null && matches(run, filter);
            if (counted) {
                String key = run.getSection().getLabel();
                GradingRun newest = newestBySection.get(key);
                if (newest == null) {
                    sectionOrder.add(key);
                    newestBySection.put(key, run);
                } else if (isNewer(run, newest)) {
                    newestBySection.put(key, run);
                }
            }
        }

        List<GradingRun> runs = new ArrayList<>();
        for (String key : sectionOrder) {
            runs.add(newestBySection.get(key));
        }
        return runs;
    }

    // A blank module or section in the filter matches every run
    private boolean matches(GradingRun run, StatisticsFilter filter) {
        if (filter == null) {
            return true;
        }

        Section section = run.getSection();
        boolean moduleMatches = matchesCode(filter.getModuleCode(),
                section.getModuleCode());
        boolean sectionMatches = matchesCode(filter.getSectionCode(),
                section.getSectionCode());
        return moduleMatches && sectionMatches;
    }

    // Compares one code of the filter with the run, ignoring case
    private boolean matchesCode(String wanted, String actual) {
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        return wanted.equalsIgnoreCase(actual);
    }

    // Says whether the first run started after the second one
    private boolean isNewer(GradingRun run, GradingRun other) {
        if (run.getStartedAt() == null) {
            return false;
        }
        if (other.getStartedAt() == null) {
            return true;
        }
        return run.getStartedAt().isAfter(other.getStartedAt());
    }

    // Only a graded submission with results has a total worth counting
    private boolean isGraded(Submission submission) {
        boolean graded = submission.getStatus() == SubmissionStatus.GRADED;
        boolean hasResults = !submission.getQuestionResults().isEmpty();
        return graded && hasResults;
    }

    // Says whether a score reaches the pass mark from the settings
    private boolean isPass(double score, double maxScore) {
        double passMark = config.getDouble(ConfigKeys.PASS_MARK_PERCENT);
        return maxScore > 0.0 && toPercent(score, maxScore) >= passMark;
    }

    // Turns a score into a percentage of the maximum
    private double toPercent(double score, double maxScore) {
        if (maxScore <= 0.0) {
            return 0.0;
        }
        return score / maxScore * FULL_PERCENT;
    }

    // The share of graded students who reached the pass mark on each
    // question. A question a student has no result for counts as failed.
    private void addQuestionPassRates(StatisticsSummary summary,
            List<GradingRun> runs, List<Submission> graded) {
        for (Question question : runs.get(0).getAssessment().getQuestions()) {
            int passed = 0;
            for (Submission submission : graded) {
                QuestionResult result = findResult(submission,
                        question.getId());
                boolean passedQuestion = result != null
                        && isPass(result.getScore(), question.getMaxScore());
                if (passedQuestion) {
                    passed++;
                }
            }
            double questionPassRate = (double) passed / graded.size();
            summary.getQuestionPassRates().put(question.getId(),
                    questionPassRate);
        }
    }

    // Returns the result of one question, or null if there is none
    private QuestionResult findResult(Submission submission,
            String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            boolean forQuestion = result.getQuestion() != null
                    && result.getQuestion().getId().equals(questionId);
            if (forQuestion) {
                return result;
            }
        }
        return null;
    }

    // The test case failed by the most students, such as
    // "Q2b case 4 (failed by 3 students)". On a tie the first one found
    // is kept. Empty when no test case failed.
    private String mostFailedTest(List<Submission> graded) {
        Map<String, Integer> failures = new HashMap<>();
        String worstName = "";
        int worstCount = 0;
        for (Submission submission : graded) {
            for (QuestionResult result : submission.getQuestionResults()) {
                for (TestCaseResult testCase : result.getTestCaseResults()) {
                    if (!testCase.isPassed() && result.getQuestion() != null) {
                        String name = result.getQuestion().getId()
                                + " case " + testCase.getTestNumber();
                        int count = failures.getOrDefault(name, 0) + 1;
                        failures.put(name, count);
                        if (count > worstCount) {
                            worstName = name;
                            worstCount = count;
                        }
                    }
                }
            }
        }

        if (worstCount == 0) {
            return "";
        }
        if (worstCount == 1) {
            return worstName + " (failed by 1 student)";
        }
        return worstName + " (failed by " + worstCount + " students)";
    }

    // Counts compile errors by their reason, so the same mistake in
    // different files adds up
    private void addCompileErrors(StatisticsSummary summary,
            List<Submission> graded) {
        Map<String, Integer> errors = summary.getCommonCompileErrors();
        for (Submission submission : graded) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                boolean compileError =
                        anomaly.getType() == AnomalyType.COMPILE_ERROR
                        && anomaly.getMessage() != null;
                if (compileError) {
                    String reason = compileErrorReason(anomaly.getMessage());
                    int count = errors.getOrDefault(reason, 0) + 1;
                    errors.put(reason, count);
                }
            }
        }
    }

    // Takes "cannot find symbol" out of a message such as
    // "Q3 did not compile: Q3.java:17: error: cannot find symbol. The..."
    private String compileErrorReason(String message) {
        int markerStart = message.indexOf(COMPILER_ERROR_MARKER);
        if (markerStart < 0) {
            return message;
        }

        int reasonStart = markerStart + COMPILER_ERROR_MARKER.length();
        String reason = message.substring(reasonStart);
        int reasonEnd = reason.indexOf(REASON_END);
        if (reasonEnd < 0) {
            return reason;
        }
        return reason.substring(0, reasonEnd);
    }
}
