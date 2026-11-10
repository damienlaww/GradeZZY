/*
 * File: RunTables.java
 *
 * Purpose: Writes and reads the row of each grading run and the questions
 * saved with it.
 *
 * Where it fits: Called by SqliteResultRepository, which owns the
 * connection and the transaction. The submissions of a run are in
 * SubmissionTables.
 */
package gradezzy.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.util.TextUtils;

/**
 * The SQL for the runs and run_questions tables. Each method works on the
 * connection it is given and never commits, rolls back or closes it.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class RunTables {

    // Saves the row of a run, replacing an earlier one with the same id
    private static final String INSERT_RUN = "INSERT OR REPLACE INTO runs "
            + "(run_id, started_at, module_code, section_code, "
            + "assessment_name, assessment_module, placeholder_folders, "
            + "score_sheet_file, testers_dir, starter_dir, "
            + "grading_settings) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Saves one question of a run
    private static final String INSERT_QUESTION = "INSERT INTO "
            + "run_questions (run_id, position, question_id, folder_name, "
            + "file_name, max_score) VALUES (?, ?, ?, ?, ?, ?)";

    // Removes the saved questions of a run
    private static final String DELETE_QUESTIONS = "DELETE FROM "
            + "run_questions WHERE run_id = ?";

    // Reads the row of one run
    private static final String SELECT_RUN = "SELECT started_at, "
            + "module_code, section_code, assessment_name, "
            + "assessment_module, placeholder_folders, score_sheet_file, "
            + "testers_dir, starter_dir, grading_settings FROM runs "
            + "WHERE run_id = ?";

    // Newest first; a run without a start time comes last
    private static final String SELECT_RUN_IDS = "SELECT run_id FROM runs "
            + "ORDER BY started_at DESC, run_id DESC";

    // Lists the saved questions of a run in their saved order
    private static final String SELECT_QUESTIONS = "SELECT question_id, "
            + "folder_name, file_name, max_score FROM run_questions "
            + "WHERE run_id = ? ORDER BY position";

    // Placeholder folder names are stored in one column, as in the
    // settings, where a comma already separates them
    private static final String LIST_SEPARATOR = ",";

    /**
     * Not used: every method here is static.
     */
    private RunTables() {
        // utility class, never instantiated
    }

    /**
     * Writes the row of the run itself, replacing any earlier row with
     * the same id.
     *
     * @param connection the open connection
     * @param gradingRun the run
     * @throws SQLException if the row cannot be written
     */
    static void insertRun(Connection connection, GradingRun gradingRun)
            throws SQLException {
        Section section = gradingRun.getSection();
        Assessment assessment = gradingRun.getAssessment();

        // A missing section or assessment leaves its columns empty (null)
        String moduleCode = null;
        String sectionCode = null;
        if (section != null) {
            moduleCode = section.getModuleCode();
            sectionCode = section.getSectionCode();
        }
        String assessmentName = null;
        String assessmentModule = null;
        String placeholders = null;
        if (assessment != null) {
            assessmentName = assessment.getName();
            assessmentModule = assessment.getModuleCode();
            placeholders = TextUtils.join(LIST_SEPARATOR,
                    assessment.getPlaceholderFolderNames());
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_RUN);
            statement.setString(1, gradingRun.getId());
            statement.setString(2,
                    JdbcSupport.textOf(gradingRun.getStartedAt()));
            statement.setString(3, moduleCode);
            statement.setString(4, sectionCode);
            statement.setString(5, assessmentName);
            statement.setString(6, assessmentModule);
            statement.setString(7, placeholders);
            statement.setString(8,
                    JdbcSupport.textOf(gradingRun.getScoreSheetFile()));
            statement.setString(9,
                    JdbcSupport.textOf(gradingRun.getTestersDirectory()));
            statement.setString(10,
                    JdbcSupport.textOf(gradingRun.getStarterDirectory()));
            statement.setString(11, gradingRun.getGradingSettings());
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Saves the questions so a run still shows the right maximums after
     * the settings change. A run with no assessment saves none.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param assessment the assessment of the run, or null
     * @throws SQLException if a question cannot be written
     */
    static void insertQuestions(Connection connection, String runId,
            Assessment assessment) throws SQLException {
        if (assessment == null) {
            return;
        }

        List<Question> questions = assessment.getQuestions();
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_QUESTION);
            for (int index = 0; index < questions.size(); index++) {
                Question question = questions.get(index);
                statement.setString(1, runId);
                statement.setInt(2, index);
                statement.setString(3, question.getId());
                statement.setString(4, question.getFolderName());
                statement.setString(5, question.getFileName());
                statement.setDouble(6, question.getMaxScore());
                statement.executeUpdate();
            }
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Removes the saved questions of a run before they are written again.
     *
     * @param connection the open connection
     * @param runId the run id
     * @throws SQLException if the rows cannot be removed
     */
    static void deleteQuestions(Connection connection, String runId)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(DELETE_QUESTIONS);
            statement.setString(1, runId);
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Rebuilds the run, its assessment with its questions, and its
     * section, without the submissions.
     *
     * @param connection the open connection
     * @param runId the run id
     * @return the run, or null if it was never saved
     * @throws SQLException if the rows cannot be read
     */
    static GradingRun readRun(Connection connection, String runId)
            throws SQLException {
        Assessment assessment = null;
        Section section = null;
        LocalDateTime startedAt = null;
        Path scoreSheetFile = null;
        Path testersDirectory = null;
        Path starterDirectory = null;
        String gradingSettings = null;
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_RUN);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            if (!rows.next()) {
                return null;
            }
            startedAt = JdbcSupport.parseTime(rows.getString("started_at"));
            section = new Section(rows.getString("module_code"),
                    rows.getString("section_code"));
            assessment = new Assessment(
                    rows.getString("assessment_name"),
                    rows.getString("assessment_module"));
            addPlaceholders(assessment,
                    rows.getString("placeholder_folders"));
            scoreSheetFile = JdbcSupport.toPath(
                    rows.getString(SqliteSchema.SCORE_SHEET_COLUMN));
            testersDirectory = JdbcSupport.toPath(
                    rows.getString(SqliteSchema.TESTERS_DIR_COLUMN));
            starterDirectory = JdbcSupport.toPath(
                    rows.getString(SqliteSchema.STARTER_DIR_COLUMN));
            gradingSettings = rows.getString(
                    SqliteSchema.GRADING_SETTINGS_COLUMN);
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }

        readQuestions(connection, runId, assessment);
        GradingRun gradingRun = new GradingRun(runId, assessment, section,
                startedAt);
        gradingRun.setScoreSheetFile(scoreSheetFile);
        gradingRun.setTestersDirectory(testersDirectory);
        gradingRun.setStarterDirectory(starterDirectory);
        gradingRun.setGradingSettings(gradingSettings);
        return gradingRun;
    }

    /**
     * Reads the id of every saved run, newest first. A run without a
     * start time comes last.
     *
     * @param connection the open connection
     * @return the run ids
     * @throws SQLException if the rows cannot be read
     */
    static List<String> readRunIds(Connection connection)
            throws SQLException {
        List<String> runIds = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_RUN_IDS);
            rows = statement.executeQuery();
            while (rows.next()) {
                runIds.add(rows.getString("run_id"));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return runIds;
    }

    // Adds the saved questions of a run to its assessment, in order
    private static void readQuestions(Connection connection, String runId,
            Assessment assessment) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_QUESTIONS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                assessment.addQuestion(new Question(
                        rows.getString("question_id"),
                        rows.getString("folder_name"),
                        rows.getString("file_name"),
                        rows.getDouble("max_score")));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
    }

    // Splits the saved placeholder names back into the assessment
    private static void addPlaceholders(Assessment assessment,
            String joined) {
        if (joined == null || joined.isEmpty()) {
            return;
        }
        for (String name : joined.split(LIST_SEPARATOR)) {
            assessment.getPlaceholderFolderNames().add(name);
        }
    }
}
