/*
 * File: StudentReportExporter.java
 *
 * Purpose: Writes the individual report of one student.
 *
 * Where it fits: Used from the student report screen.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;

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

    private static final String TITLE = "GradeZZY student report";

    private static final char UNDERLINE = '=';

    // Labels are padded to this width so the values line up
    private static final String LABEL_FORMAT = "%-12s%s";

    // One line of the score table: question, score, maximum, remark
    private static final String SCORE_FORMAT = "  %-6s %5s / %-5s %s";

    // The provided roster writes _ when there is no separate last name
    private static final String ROSTER_EMPTY_NAME = "_";

    private static final String NONE = "  (none)";

    private static final String NOT_RECORDED = "(not recorded)";

    private static final String NEW_LINE = System.lineSeparator();

    // The student the report is for
    private final String username;

    /**
     * Creates a new student report exporter.
     *
     * @param username the student the report is for
     */
    public StudentReportExporter(String username) {
        this.username = username;
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        if (gradingRun == null || outputFile == null) {
            throw new ExportException(
                    "Nothing to export: the grading run or file is missing.");
        }
        if (TextUtils.isBlank(username)) {
            throw new ExportException("Choose a student for the report.");
        }

        Submission submission = findSubmission(gradingRun);
        if (submission == null) {
            throw new ExportException("There is no submission for "
                    + username + " in run " + gradingRun.getId() + ".");
        }

        StringBuilder report = new StringBuilder();
        writeDetails(report, gradingRun, submission);
        writeScores(report, submission);
        writeFailedTests(report, submission);
        writeAnomalies(report, submission);

        try {
            FileUtils.writeText(outputFile, report.toString());
        } catch (IOException exception) {
            throw new ExportException("Could not write the student report "
                    + "to " + outputFile + ". Check that the folder exists "
                    + "and is not read-only.", exception);
        }
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
    private void writeDetails(StringBuilder report, GradingRun gradingRun,
            Submission submission) {
        report.append(TITLE).append(NEW_LINE);
        report.append(String.valueOf(UNDERLINE).repeat(TITLE.length()))
                .append(NEW_LINE).append(NEW_LINE);

        Student student = submission.getStudent();
        if (student == null) {
            addLine(report, "Student:", username + " (not on the roster)");
        } else {
            addLine(report, "Student:", fullName(student) + " ("
                    + username + ")");
            addLine(report, "Email:", student.getEmail());
        }

        Assessment assessment = gradingRun.getAssessment();
        Section section = gradingRun.getSection();
        if (assessment != null && section != null) {
            addLine(report, "Assessment:", assessment.getName() + ", "
                    + section.getModuleCode() + " "
                    + section.getSectionCode());
        }
        addLine(report, "Run:", gradingRun.getId());
        addLine(report, "Status:", String.valueOf(submission.getStatus()));
        if (!TextUtils.isBlank(submission.getIngestFailure())) {
            addLine(report, "Problem:", submission.getIngestFailure());
        }

        String total = ScoreFormatter.format(submission.getTotalScore());
        if (assessment != null) {
            total = total + " / "
                    + ScoreFormatter.format(assessment.getMaxTotal());
        }
        addLine(report, "Total:", total);
        report.append(NEW_LINE);
    }

    // One line per question, with a remark for a compile error or timeout
    private void writeScores(StringBuilder report, Submission submission) {
        report.append("Scores").append(NEW_LINE);
        if (submission.getQuestionResults().isEmpty()) {
            report.append(NONE).append(NEW_LINE);
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

            report.append(String.format(SCORE_FORMAT, questionId,
                    ScoreFormatter.format(result.getScore()), maxScore,
                    remark).stripTrailing()).append(NEW_LINE);
        }
        report.append(NEW_LINE);
    }

    // Every test case that did not pass, with its first line of detail
    private void writeFailedTests(StringBuilder report,
            Submission submission) {
        report.append("Failed tests").append(NEW_LINE);
        int failedCount = 0;
        for (QuestionResult result : submission.getQuestionResults()) {
            for (TestCaseResult testCase : result.getTestCaseResults()) {
                if (!testCase.isPassed()) {
                    report.append("  ").append(questionIdOf(result))
                            .append(" test ")
                            .append(testCase.getTestNumber()).append(": ")
                            .append(firstLine(testCase.getDetail()))
                            .append(NEW_LINE);
                    failedCount++;
                }
            }
        }

        if (failedCount == 0) {
            report.append(NONE).append(NEW_LINE);
        }
        report.append(NEW_LINE);
    }

    // Every anomaly, its fix status and what the instructor can do
    private void writeAnomalies(StringBuilder report, Submission submission) {
        report.append("Anomalies").append(NEW_LINE);
        if (submission.getAnomalies().isEmpty()) {
            report.append(NONE).append(NEW_LINE);
        }

        for (Anomaly anomaly : submission.getAnomalies()) {
            report.append("  [").append(anomaly.getSeverity()).append("] ")
                    .append(anomaly.getType()).append(": ")
                    .append(firstLine(anomaly.getMessage()))
                    .append(" (fix: ").append(anomaly.getFixStatus())
                    .append(")").append(NEW_LINE);
            if (!TextUtils.isBlank(anomaly.getSuggestedAction())) {
                report.append("      What to do: ")
                        .append(firstLine(anomaly.getSuggestedAction()))
                        .append(NEW_LINE);
            }
        }
    }

    // Adds one "Label:    value" line
    private void addLine(StringBuilder report, String label, String value) {
        report.append(String.format(LABEL_FORMAT, label, value))
                .append(NEW_LINE);
    }

    // First name, then the last name unless the roster left it empty
    private String fullName(Student student) {
        String lastName = student.getLastName();
        if (TextUtils.isBlank(lastName)
                || lastName.equals(ROSTER_EMPTY_NAME)) {
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
        int lineEnd = normalised.indexOf('\n');
        if (lineEnd < 0) {
            return normalised;
        }
        return normalised.substring(0, lineEnd);
    }
}
