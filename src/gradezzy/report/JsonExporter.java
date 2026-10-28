/*
 * File: JsonExporter.java
 *
 * Purpose: Writes the full results of a run as JSON.
 *
 * Where it fits: Holds what the score sheet cannot: per-question scores, test
 * case results and anomalies.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

/**
 * Writes the full results of a run as JSON.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class JsonExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "JSON";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "json";

    // The helper that formats JSON text
    private final JsonWriter jsonWriter;

    /**
     * Creates a new json exporter.
     *
     * @param jsonWriter the helper that formats JSON text
     */
    public JsonExporter(JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
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
     * Writes the full results of a run as JSON.
     *
     * <p>The file holds the run, its assessment and section, and for every
     * submission the student, the per-question scores, the test case
     * results, the tester output and the anomalies.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        if (gradingRun == null || outputFile == null) {
            throw new ExportException(
                    "Nothing to export: the grading run or file is missing.");
        }

        String json = writeRun(gradingRun) + System.lineSeparator();
        try {
            FileUtils.writeText(outputFile, json);
        } catch (IOException exception) {
            throw new ExportException("Could not write the JSON file to "
                    + outputFile + ". Check that the folder exists and is "
                    + "not read-only.", exception);
        }
    }

    // The whole run: the top level of the file
    private String writeRun(GradingRun gradingRun) {
        List<String> submissions = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            submissions.add(writeSubmission(submission));
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("runId", text(gradingRun.getId()));
        fields.put("startedAt", text(gradingRun.getStartedAt()));
        fields.put("assessment", writeAssessment(gradingRun.getAssessment()));
        fields.put("section", writeSection(gradingRun.getSection()));
        fields.put("submissions", jsonWriter.writeArray(submissions));
        return jsonWriter.writeObject(fields);
    }

    // The assessment and the maximum score of each question
    private String writeAssessment(Assessment assessment) {
        if (assessment == null) {
            return JsonWriter.NULL;
        }

        List<String> questions = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            questions.add(writeQuestion(question));
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", text(assessment.getName()));
        fields.put("moduleCode", text(assessment.getModuleCode()));
        fields.put("maxTotal", number(assessment.getMaxTotal()));
        fields.put("questions", jsonWriter.writeArray(questions));
        return jsonWriter.writeObject(fields);
    }

    // One question of the assessment
    private String writeQuestion(Question question) {
        if (question == null) {
            return JsonWriter.NULL;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", text(question.getId()));
        fields.put("folder", text(question.getFolderName()));
        fields.put("file", text(question.getFileName()));
        fields.put("maxScore", number(question.getMaxScore()));
        return jsonWriter.writeObject(fields);
    }

    // The module and section that were graded
    private String writeSection(Section section) {
        if (section == null) {
            return JsonWriter.NULL;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("moduleCode", text(section.getModuleCode()));
        fields.put("sectionCode", text(section.getSectionCode()));
        return jsonWriter.writeObject(fields);
    }

    // One student's submission with its results and anomalies
    private String writeSubmission(Submission submission) {
        List<String> questionResults = new ArrayList<>();
        for (QuestionResult questionResult
                : submission.getQuestionResults()) {
            questionResults.add(writeQuestionResult(questionResult));
        }

        List<String> anomalies = new ArrayList<>();
        for (Anomaly anomaly : submission.getAnomalies()) {
            anomalies.add(writeAnomaly(anomaly));
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", text(submission.getUsername()));
        fields.put("status", text(submission.getStatus()));
        fields.put("totalScore", number(submission.getTotalScore()));
        fields.put("student", writeStudent(submission.getStudent()));
        fields.put("originalPath", text(submission.getOriginalPath()));
        fields.put("workingCopyPath", text(submission.getWorkingCopyPath()));
        fields.put("ingestFailure", text(submission.getIngestFailure()));
        fields.put("questions", jsonWriter.writeArray(questionResults));
        fields.put("anomalies", jsonWriter.writeArray(anomalies));
        return jsonWriter.writeObject(fields);
    }

    // The roster entry; null when the student is not on the roster
    private String writeStudent(Student student) {
        if (student == null) {
            return JsonWriter.NULL;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("orgDefinedId", text(student.getOrgDefinedId()));
        fields.put("username", text(student.getUsername()));
        fields.put("lastName", text(student.getLastName()));
        fields.put("firstName", text(student.getFirstName()));
        fields.put("email", text(student.getEmail()));
        return jsonWriter.writeObject(fields);
    }

    // The score of one question and the result of each of its test cases
    private String writeQuestionResult(QuestionResult questionResult) {
        List<String> testCases = new ArrayList<>();
        for (TestCaseResult testCaseResult
                : questionResult.getTestCaseResults()) {
            testCases.add(writeTestCase(testCaseResult));
        }

        Question question = questionResult.getQuestion();
        String questionId = null;
        String maxScore = JsonWriter.NULL;
        if (question != null) {
            questionId = question.getId();
            maxScore = number(question.getMaxScore());
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("questionId", text(questionId));
        fields.put("score", number(questionResult.getScore()));
        fields.put("maxScore", maxScore);
        fields.put("compiled", String.valueOf(questionResult.isCompiled()));
        fields.put("timedOut", String.valueOf(questionResult.isTimedOut()));
        fields.put("testCases", jsonWriter.writeArray(testCases));
        fields.put("output", text(questionResult.getOutput()));
        return jsonWriter.writeObject(fields);
    }

    // One test case of a tester run
    private String writeTestCase(TestCaseResult testCaseResult) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("testNumber",
                String.valueOf(testCaseResult.getTestNumber()));
        fields.put("passed", String.valueOf(testCaseResult.isPassed()));
        fields.put("detail", text(testCaseResult.getDetail()));
        return jsonWriter.writeObject(fields);
    }

    // One anomaly and the state of its automatic fix
    private String writeAnomaly(Anomaly anomaly) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", text(anomaly.getId()));
        fields.put("type", text(anomaly.getType()));
        fields.put("severity", text(anomaly.getSeverity()));
        fields.put("message", text(anomaly.getMessage()));
        fields.put("suggestedAction", text(anomaly.getSuggestedAction()));
        fields.put("fixStatus", text(anomaly.getFixStatus()));
        return jsonWriter.writeObject(fields);
    }

    // Any value as a JSON string: text, enums, paths and times all use
    // toString. A missing value becomes null.
    private String text(Object value) {
        if (value == null) {
            return JsonWriter.NULL;
        }
        return jsonWriter.quote(value.toString());
    }

    // Writes 12 for a whole number and 10.5 otherwise, as the CSV does.
    // JSON has no form for infinity or not-a-number, so those become null.
    private String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return JsonWriter.NULL;
        }
        return ScoreFormatter.format(value);
    }
}
