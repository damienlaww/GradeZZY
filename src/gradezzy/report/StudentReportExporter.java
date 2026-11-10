/*
 * File: StudentReportExporter.java
 *
 * Purpose: Writes the individual report of one student, or of every
 * student in a run.
 *
 * Where it fits: Registered in ApplicationContext without a student, so an
 * export of a run writes every student's report into one file.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Writes the individual report of one student.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class StudentReportExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "Student report";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "txt";

    // The first line of every report
    private static final String TITLE = "GradeZZY student report";

    // Repeated under the title, as wide as the title
    private static final char UNDERLINE = '=';

    // Labels are padded to this width so the values line up
    private static final String LABEL_FORMAT = "%-12s%s";

    // One line of the score table: question, score, maximum, remark
    private static final String SCORE_FORMAT = "  %-6s %5s / %-5s %s";

    // The provided roster writes _ when there is no separate last name
    private static final String ROSTER_EMPTY_NAME = "_";

    // Written under a heading that has nothing to list
    private static final String NONE = "  (none)";

    // Written for a result whose question was not saved
    private static final String NOT_RECORDED = "(not recorded)";

    // Every line of the report ends with the computer's own line ending
    private static final String NEW_LINE = System.lineSeparator();

    // Ends a line once TextUtils.normaliseLineEndings has run
    private static final char LINE_FEED = '\n';

    // Printed between two students' reports in one file
    private static final String REPORT_SEPARATOR = "-";

    // The student the report is for, or null for every student
    private final String username;

    /**
     * Creates a new student report exporter for one student.
     *
     * @param username the student the report is for
     */
    public StudentReportExporter(String username) {
        this.username = username;
    }

    /**
     * Creates a new student report exporter that writes the report of
     * every student in the run, one after another, into one file. This is
     * the one ApplicationContext registers.
     */
    public StudentReportExporter() {
        this.username = null;
    }

    /**
     * Returns the format name.
     *
     * @return the format name
     */
    @Override
    public String getFormatName() {
        return FORMAT_NAME;
    }

    /**
     * Returns the file extension.
     *
     * @return the extension, without the dot
     */
    @Override
    public String getFileExtension() {
        return FILE_EXTENSION;
    }

    /**
     * Writes the individual report of one student.
     *
     * <p>The report lists the student's details, the score of every
     * question, each failed test and each anomaly with what to do about it.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the student is not in the run or the file
     *         cannot be written
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        if (gradingRun == null || outputFile == null) {
            throw new ExportException(
                    "Nothing to export: the grading run or file is missing.");
        }
        String report = writeChosenReport(gradingRun);

        try {
            FileUtils.writeText(outputFile, report);
        } catch (IOException exception) {
            throw new ExportException("Could not write the student report "
                    + "to " + outputFile + ". Check that the folder exists "
                    + "and is not read-only.", exception);
        }
    }

    // The report of the chosen student, or of everyone if none is chosen
    private String writeChosenReport(GradingRun gradingRun)
            throws ExportException {
        if (TextUtils.isBlank(username)) {
            return writeEveryReport(gradingRun);
        }

        Submission submission = findSubmission(gradingRun);
        if (submission == null) {
            throw new ExportException("There is no submission for "
                    + username + " in run " + gradingRun.getId() + ".");
        }
        return writeReport(gradingRun, submission);
    }

    // The reports of every student in the run, in run order, with a line
    // between two reports
    private String writeEveryReport(GradingRun gradingRun)
            throws ExportException {
        List<Submission> submissions = gradingRun.getSubmissions();
        if (submissions.isEmpty()) {
            throw new ExportException("Run " + gradingRun.getId()
                    + " has no students to report on.");
        }

        String separator = REPORT_SEPARATOR.repeat(TITLE.length())
                + NEW_LINE + NEW_LINE;
        List<String> reports = new ArrayList<>();
        for (Submission submission : submissions) {
            reports.add(writeReport(gradingRun, submission));
        }
        return TextUtils.join(separator, reports);
    }

    // The whole report of one student
    private String writeReport(GradingRun gradingRun,
            Submission submission) {
        return writeDetails(gradingRun, submission)
                + writeScores(submission)
                + writeFailedTests(submission)
                + writeAnomalies(submission);
    }

    // Returns the submission of the chosen student, or null if absent
    private Submission findSubmission(GradingRun gradingRun) {
        for (Submission submission : gradingRun.getSubmissions()) {
            if (username.equals(submission.getUsername())) {
                return submission;
            }
        }
        return null;
    }

    // The title, the student and the run
    private String writeDetails(GradingRun gradingRun,
            Submission submission) {
        String report = TITLE + NEW_LINE;
        String underline = String.valueOf(UNDERLINE).repeat(TITLE.length());
        report += underline + NEW_LINE + NEW_LINE;

        // The submission's own username, so the report of every student
        // names the right one
        String studentUsername = submission.getUsername();
        Student student = submission.getStudent();
        if (student == null) {
            String unknown = studentUsername + " (not on the roster)";
            report += labelLine("Student:", unknown);
        } else {
            String named = fullName(student) + " (" + studentUsername + ")";
            report += labelLine("Student:", named);
            report += labelLine("Email:", student.getEmail());
        }

        Assessment assessment = gradingRun.getAssessment();
        Section section = gradingRun.getSection();
        if (assessment != null && section != null) {
            String graded = assessment.getName() + ", "
                    + section.getModuleCode() + " "
                    + section.getSectionCode();
            report += labelLine("Assessment:", graded);
        }
        report += labelLine("Run:", gradingRun.getId());
        report += labelLine("Status:", String.valueOf(submission.getStatus()));
        if (!TextUtils.isBlank(submission.getIngestFailure())) {
            report += labelLine("Problem:", submission.getIngestFailure());
        }

        String total = ScoreFormatter.format(submission.getTotalScore());
        if (assessment != null) {
            total = total + " / "
                    + ScoreFormatter.format(assessment.getMaxTotal());
        }
        report += labelLine("Total:", total);
        report += NEW_LINE;
        return report;
    }

    // One line per question, with a remark for a compile error or timeout
    private String writeScores(Submission submission) {
        String report = "Scores" + NEW_LINE;
        if (submission.getQuestionResults().isEmpty()) {
            report += NONE + NEW_LINE;
        }

        for (QuestionResult result : submission.getQuestionResults()) {
            Question question = result.getQuestion();
            String questionId = NOT_RECORDED;
            String maxScore = "";
            if (question != null) {
                questionId = question.getId();
                maxScore = ScoreFormatter.format(question.getMaxScore());
            }

            String remark = "";
            if (!result.isCompiled()) {
                remark = "did not compile";
            } else if (result.isTimedOut()) {
                remark = "stopped at the time limit";
            }

            String score = ScoreFormatter.format(result.getScore());
            String line = String.format(SCORE_FORMAT, questionId, score,
                    maxScore, remark);
            report += line.stripTrailing() + NEW_LINE;
        }
        report += NEW_LINE;
        return report;
    }

    // Every test case that did not pass, with its first line of detail
    private String writeFailedTests(Submission submission) {
        String report = "Failed tests" + NEW_LINE;
        int failedCount = 0;
        for (QuestionResult result : submission.getQuestionResults()) {
            for (TestCaseResult testCase : result.getTestCaseResults()) {
                if (!testCase.isPassed()) {
                    report += "  " + questionIdOf(result) + " test "
                            + testCase.getTestNumber() + ": "
                            + firstLine(testCase.getDetail()) + NEW_LINE;
                    failedCount++;
                }
            }
        }

        if (failedCount == 0) {
            report += NONE + NEW_LINE;
        }
        report += NEW_LINE;
        return report;
    }

    // Every anomaly, its fix status and what the instructor can do
    private String writeAnomalies(Submission submission) {
        String report = "Anomalies" + NEW_LINE;
        if (submission.getAnomalies().isEmpty()) {
            report += NONE + NEW_LINE;
        }

        for (Anomaly anomaly : submission.getAnomalies()) {
            report += "  [" + anomaly.getSeverity() + "] "
                    + anomaly.getType() + ": "
                    + firstLine(anomaly.getMessage())
                    + " (fix: " + anomaly.getFixStatus() + ")" + NEW_LINE;
            if (!TextUtils.isBlank(anomaly.describeFix())) {
                report += "      Fix made: " + anomaly.describeFix()
                        + NEW_LINE;
            }
            if (!TextUtils.isBlank(anomaly.getSuggestedAction())) {
                report += "      What to do: "
                        + firstLine(anomaly.getSuggestedAction()) + NEW_LINE;
            }
        }
        return report;
    }

    // Returns one "Label:    value" line, line ending included
    private String labelLine(String label, String value) {
        return String.format(LABEL_FORMAT, label, value) + NEW_LINE;
    }

    // First name, then the last name unless the roster left it empty
    private String fullName(Student student) {
        String lastName = student.getLastName();
        boolean noLastName = TextUtils.isBlank(lastName)
                || lastName.equals(ROSTER_EMPTY_NAME);
        if (noLastName) {
            return student.getFirstName();
        }
        return student.getFirstName() + " " + lastName;
    }

    // The question's id, or a placeholder if it was not recorded
    private String questionIdOf(QuestionResult result) {
        if (result.getQuestion() == null) {
            return NOT_RECORDED;
        }
        return result.getQuestion().getId();
    }

    // Keeps a report line short when a detail holds a stack trace
    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String normalised = TextUtils.normaliseLineEndings(text).strip();
        int lineEnd = normalised.indexOf(LINE_FEED);
        if (lineEnd < 0) {
            return normalised;
        }
        return normalised.substring(0, lineEnd);
    }
}
