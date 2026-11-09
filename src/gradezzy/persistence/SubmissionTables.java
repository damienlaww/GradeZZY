/*
 * File: SubmissionTables.java
 *
 * Purpose: Writes and reads each submission of a run with its question
 * results, test cases and anomalies.
 *
 * Where it fits: Called by SqliteResultRepository, which owns the
 * connection and the transaction. Review decisions on the anomalies are
 * in ReviewTables.
 */
package gradezzy.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;
import gradezzy.util.TextUtils;

/**
 * The SQL for the submissions, question_results, test_case_results and
 * anomalies tables. Each method works on the connection it is given and
 * never commits, rolls back or closes it.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class SubmissionTables {

    private static final String INSERT_SUBMISSION = "INSERT INTO "
            + "submissions (run_id, username, position, status, "
            + "original_path, working_copy_path, ingest_failure, "
            + "org_defined_id, student_username, last_name, first_name, "
            + "email, rejected_fixes) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_QUESTION_RESULT = "INSERT INTO "
            + "question_results (run_id, username, position, question_id, "
            + "score, compiled, timed_out, output) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_TEST_CASE = "INSERT INTO "
            + "test_case_results (run_id, username, question_id, position, "
            + "test_number, passed, detail) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_ANOMALY = "INSERT INTO anomalies "
            + "(run_id, username, position, anomaly_id, type, severity, "
            + "message, suggested_action, fix_status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Removes every submission of a run before the run is written again
    private static final String[] DELETE_RUN_SUBMISSIONS = {
        "DELETE FROM test_case_results WHERE run_id = ?",
        "DELETE FROM question_results WHERE run_id = ?",
        "DELETE FROM anomalies WHERE run_id = ?",
        "DELETE FROM submissions WHERE run_id = ?"
    };

    // Removes everything saved for one submission of a run
    private static final String[] DELETE_SUBMISSION = {
        "DELETE FROM test_case_results WHERE run_id = ? AND username = ?",
        "DELETE FROM question_results WHERE run_id = ? AND username = ?",
        "DELETE FROM anomalies WHERE run_id = ? AND username = ?",
        "DELETE FROM submissions WHERE run_id = ? AND username = ?"
    };

    private static final String SELECT_SUBMISSIONS = "SELECT username, "
            + "status, original_path, working_copy_path, ingest_failure, "
            + "org_defined_id, student_username, last_name, first_name, "
            + "email, rejected_fixes FROM submissions WHERE run_id = ? "
            + "ORDER BY position";

    private static final String SELECT_QUESTION_RESULTS = "SELECT "
            + "username, question_id, score, compiled, timed_out, output "
            + "FROM question_results WHERE run_id = ? "
            + "ORDER BY username, position";

    private static final String SELECT_TEST_CASES = "SELECT username, "
            + "question_id, test_number, passed, detail "
            + "FROM test_case_results WHERE run_id = ? "
            + "ORDER BY username, question_id, position";

    private static final String SELECT_ANOMALIES = "SELECT username, "
            + "anomaly_id, type, severity, message, suggested_action, "
            + "fix_status FROM anomalies WHERE run_id = ? "
            + "ORDER BY username, position";

    // Keeps a submission's place in the run when it is saved again
    private static final String SELECT_POSITION = "SELECT position "
            + "FROM submissions WHERE run_id = ? AND username = ?";

    // The next free place at the end of a run
    private static final String SELECT_NEXT_POSITION = "SELECT "
            + "COALESCE(MAX(position) + 1, 0) AS next_position "
            + "FROM submissions WHERE run_id = ?";

    // Joins a username and a question id into one map key, and the keys
    // of the undone fixes into one column. A newline cannot appear in
    // any of them.
    private static final String KEY_SEPARATOR = "\n";

    // Used when a saved question is missing from the saved assessment
    private static final double UNKNOWN_MAX_SCORE = 0.0;

    /**
     * Not used: every method here is static.
     */
    private SubmissionTables() {
        // utility class, never instantiated
    }

    /**
     * Writes one submission with its results, test cases and anomalies.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param submission the submission
     * @param position the place of the submission in the run
     * @throws SQLException if a row cannot be written
     */
    static void insertSubmission(Connection connection, String runId,
            Submission submission, int position) throws SQLException {
        String username = submission.getUsername();
        Student student = submission.getStudent();

        // A student who is not on the roster leaves these columns empty
        String orgDefinedId = null;
        String studentUsername = null;
        String lastName = null;
        String firstName = null;
        String email = null;
        if (student != null) {
            orgDefinedId = student.getOrgDefinedId();
            studentUsername = student.getUsername();
            lastName = student.getLastName();
            firstName = student.getFirstName();
            email = student.getEmail();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_SUBMISSION);
            statement.setString(1, runId);
            statement.setString(2, username);
            statement.setInt(3, position);
            statement.setString(4,
                    JdbcSupport.textOf(submission.getStatus()));
            statement.setString(5,
                    JdbcSupport.textOf(submission.getOriginalPath()));
            statement.setString(6,
                    JdbcSupport.textOf(submission.getWorkingCopyPath()));
            statement.setString(7, submission.getIngestFailure());
            statement.setString(8, orgDefinedId);
            statement.setString(9, studentUsername);
            statement.setString(10, lastName);
            statement.setString(11, firstName);
            statement.setString(12, email);
            statement.setString(13, joinFixKeys(
                    submission.getRejectedFixes()));
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }

        insertQuestionResults(connection, runId, username,
                submission.getQuestionResults());
        insertAnomalies(connection, runId, username,
                submission.getAnomalies());
    }

    /**
     * Removes every submission of a run with its results, test cases and
     * anomalies.
     *
     * @param connection the open connection
     * @param runId the run id
     * @throws SQLException if the rows cannot be removed
     */
    static void deleteRunSubmissions(Connection connection, String runId)
            throws SQLException {
        for (String sql : DELETE_RUN_SUBMISSIONS) {
            PreparedStatement statement = null;
            try {
                statement = connection.prepareStatement(sql);
                statement.setString(1, runId);
                statement.executeUpdate();
            } finally {
                JdbcSupport.closeStatement(statement);
            }
        }
    }

    /**
     * Removes one submission of a run with its results, test cases and
     * anomalies.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param username the student
     * @throws SQLException if the rows cannot be removed
     */
    static void deleteSubmission(Connection connection, String runId,
            String username) throws SQLException {
        for (String sql : DELETE_SUBMISSION) {
            PreparedStatement statement = null;
            try {
                statement = connection.prepareStatement(sql);
                statement.setString(1, runId);
                statement.setString(2, username);
                statement.executeUpdate();
            } finally {
                JdbcSupport.closeStatement(statement);
            }
        }
    }

    /**
     * Finds the place a submission already has in the run, or the next
     * free one at the end.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param username the student
     * @return the place, counting from 0
     * @throws SQLException if the rows cannot be read
     */
    static int findPosition(Connection connection, String runId,
            String username) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_POSITION);
            statement.setString(1, runId);
            statement.setString(2, username);
            rows = statement.executeQuery();
            if (rows.next()) {
                return rows.getInt("position");
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }

        return findNextPosition(connection, runId);
    }

    /**
     * Adds the saved submissions to the run in their saved order, each
     * with its question results, test cases and anomalies.
     *
     * @param connection the open connection
     * @param gradingRun the run, as rebuilt by RunTables.readRun
     * @throws SQLException if the rows cannot be read
     */
    static void readSubmissions(Connection connection,
            GradingRun gradingRun) throws SQLException {
        String runId = gradingRun.getId();
        Map<String, Submission> submissions =
                readSubmissionRows(connection, gradingRun);
        Map<String, QuestionResult> results = readQuestionResults(
                connection, runId, gradingRun.getAssessment(), submissions);
        readTestCases(connection, runId, results);
        readAnomalies(connection, runId, submissions);
    }

    // Writes the score of each question and each of its test cases
    private static void insertQuestionResults(Connection connection,
            String runId, String username, List<QuestionResult> results)
            throws SQLException {
        PreparedStatement resultStatement = null;
        PreparedStatement testStatement = null;
        try {
            resultStatement = connection.prepareStatement(
                    INSERT_QUESTION_RESULT);
            testStatement = connection.prepareStatement(INSERT_TEST_CASE);
            for (int index = 0; index < results.size(); index++) {
                QuestionResult result = results.get(index);
                String questionId = questionIdOf(result);
                resultStatement.setString(1, runId);
                resultStatement.setString(2, username);
                resultStatement.setInt(3, index);
                resultStatement.setString(4, questionId);
                resultStatement.setDouble(5, result.getScore());
                resultStatement.setBoolean(6, result.isCompiled());
                resultStatement.setBoolean(7, result.isTimedOut());
                resultStatement.setString(8, result.getOutput());
                resultStatement.executeUpdate();

                List<TestCaseResult> testCases = result.getTestCaseResults();
                for (int testIndex = 0; testIndex < testCases.size();
                        testIndex++) {
                    TestCaseResult testCase = testCases.get(testIndex);
                    testStatement.setString(1, runId);
                    testStatement.setString(2, username);
                    testStatement.setString(3, questionId);
                    testStatement.setInt(4, testIndex);
                    testStatement.setInt(5, testCase.getTestNumber());
                    testStatement.setBoolean(6, testCase.isPassed());
                    testStatement.setString(7, testCase.getDetail());
                    testStatement.executeUpdate();
                }
            }
        } finally {
            // Closed in the opposite order to opening
            JdbcSupport.closeStatements(testStatement, resultStatement);
        }
    }

    // Writes each anomaly of one submission
    private static void insertAnomalies(Connection connection,
            String runId, String username, List<Anomaly> anomalies)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_ANOMALY);
            for (int index = 0; index < anomalies.size(); index++) {
                Anomaly anomaly = anomalies.get(index);
                statement.setString(1, runId);
                statement.setString(2, username);
                statement.setInt(3, index);
                statement.setString(4, anomaly.getId());
                statement.setString(5, JdbcSupport.textOf(anomaly.getType()));
                statement.setString(6,
                        JdbcSupport.textOf(anomaly.getSeverity()));
                statement.setString(7, anomaly.getMessage());
                statement.setString(8, anomaly.getSuggestedAction());
                statement.setString(9,
                        JdbcSupport.textOf(anomaly.getFixStatus()));
                statement.executeUpdate();
            }
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    // The next free place at the end of a run
    private static int findNextPosition(Connection connection,
            String runId) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_NEXT_POSITION);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            rows.next();
            return rows.getInt("next_position");
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
    }

    // Adds the saved submissions to the run in their saved order and
    // returns them by username
    private static Map<String, Submission> readSubmissionRows(
            Connection connection, GradingRun gradingRun)
            throws SQLException {
        Map<String, Submission> submissions = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_SUBMISSIONS);
            statement.setString(1, gradingRun.getId());
            rows = statement.executeQuery();
            while (rows.next()) {
                Submission submission = readSubmission(rows,
                        gradingRun.getSection());
                gradingRun.addSubmission(submission);
                submissions.put(submission.getUsername(), submission);
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return submissions;
    }

    // Rebuilds one submission and its roster entry from a row
    private static Submission readSubmission(ResultSet row,
            Section section) throws SQLException {
        Submission submission = new Submission(row.getString("username"),
                section, JdbcSupport.toPath(row.getString("original_path")));
        submission.setWorkingCopyPath(
                JdbcSupport.toPath(row.getString("working_copy_path")));
        submission.setIngestFailure(row.getString("ingest_failure"));

        String status = row.getString("status");
        if (status != null) {
            submission.setStatus(submissionStatusOf(status));
        }

        String orgDefinedId = row.getString("org_defined_id");
        if (orgDefinedId != null) {
            submission.setStudent(new Student(orgDefinedId,
                    row.getString("student_username"),
                    row.getString("last_name"),
                    row.getString("first_name"),
                    row.getString("email")));
        }

        // Undone fixes stay undone when the run is resumed or retried
        String rejectedFixes = row.getString(
                SqliteSchema.REJECTED_FIXES_COLUMN);
        if (rejectedFixes != null && !rejectedFixes.isEmpty()) {
            for (String fixKey : rejectedFixes.split(KEY_SEPARATOR)) {
                submission.rejectFix(fixKey);
            }
        }
        return submission;
    }

    // Joins the keys of the undone fixes into one column, or returns null
    // when there are none. A fix key names a fixer and a path, so it has
    // no newline in it.
    private static String joinFixKeys(List<String> fixKeys) {
        if (fixKeys.isEmpty()) {
            return null;
        }
        return TextUtils.join(KEY_SEPARATOR, fixKeys);
    }

    // Adds each saved question result to its submission and returns them
    // by username and question id, so test cases can find them
    private static Map<String, QuestionResult> readQuestionResults(
            Connection connection, String runId, Assessment assessment,
            Map<String, Submission> submissions) throws SQLException {
        Map<String, QuestionResult> results = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_QUESTION_RESULTS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                String username = rows.getString("username");
                String questionId = rows.getString("question_id");
                Submission submission = submissions.get(username);
                if (submission != null) {
                    QuestionResult result = new QuestionResult(
                            findQuestion(assessment, questionId));
                    result.setScore(rows.getDouble("score"));
                    result.setCompiled(rows.getBoolean("compiled"));
                    result.setTimedOut(rows.getBoolean("timed_out"));
                    result.setOutput(rows.getString("output"));
                    submission.addQuestionResult(result);
                    results.put(username + KEY_SEPARATOR + questionId,
                            result);
                }
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return results;
    }

    // Adds each saved test case to its question result
    private static void readTestCases(Connection connection, String runId,
            Map<String, QuestionResult> results) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_TEST_CASES);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                QuestionResult result = results.get(
                        rows.getString("username") + KEY_SEPARATOR
                        + rows.getString("question_id"));
                if (result != null) {
                    result.addTestCaseResult(new TestCaseResult(
                            rows.getInt("test_number"),
                            rows.getBoolean("passed"),
                            rows.getString("detail")));
                }
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
    }

    // Adds each saved anomaly, with its fix status, to its submission
    private static void readAnomalies(Connection connection, String runId,
            Map<String, Submission> submissions) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_ANOMALIES);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                String username = rows.getString("username");
                Submission submission = submissions.get(username);
                if (submission != null) {
                    submission.addAnomaly(readAnomaly(rows, username));
                }
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
    }

    // Rebuilds one anomaly from a row
    private static Anomaly readAnomaly(ResultSet row,
            String username) throws SQLException {
        String type = row.getString("type");
        String severity = row.getString("severity");
        String anomalyId = row.getString("anomaly_id");

        // A missing type or severity stays null
        AnomalyType anomalyType = null;
        if (type != null) {
            anomalyType = anomalyTypeOf(type);
        }
        Severity anomalySeverity = null;
        if (severity != null) {
            anomalySeverity = severityOf(severity);
        }

        Anomaly anomaly = new Anomaly(anomalyId, anomalyType,
                anomalySeverity, username, row.getString("message"),
                row.getString("suggested_action"));

        FixStatus fixStatus = fixStatusOf(row.getString("fix_status"));
        if (fixStatus != null) {
            anomaly.setFixStatus(fixStatus);
        }
        return anomaly;
    }

    // Reads a saved submission status. A name this version does not know
    // becomes FAILED, so the student still shows and can be graded again.
    private static SubmissionStatus submissionStatusOf(String name) {
        try {
            return SubmissionStatus.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return SubmissionStatus.FAILED;
        }
    }

    // Reads a saved anomaly type. A type removed from AnomalyType becomes
    // INTERNAL_ERROR, so the flag still shows for review.
    private static AnomalyType anomalyTypeOf(String name) {
        try {
            return AnomalyType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return AnomalyType.INTERNAL_ERROR;
        }
    }

    // Reads a saved severity. An unknown name stays null, the same as a
    // missing one.
    private static Severity severityOf(String name) {
        try {
            return Severity.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // Reads a saved fix status. A missing or unknown name gives null, and
    // the anomaly keeps its default status.
    private static FixStatus fixStatusOf(String name) {
        if (name == null) {
            return null;
        }
        try {
            return FixStatus.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // The question of the saved assessment, or a stand-in with no marks
    // if the run's questions were not saved
    private static Question findQuestion(Assessment assessment,
            String questionId) {
        Question question = assessment.findQuestion(questionId);
        if (question == null) {
            return new Question(questionId, "", "", UNKNOWN_MAX_SCORE);
        }
        return question;
    }

    // The question id of a result, or null if it has no question
    private static String questionIdOf(QuestionResult result) {
        if (result.getQuestion() == null) {
            return null;
        }
        return result.getQuestion().getId();
    }
}
