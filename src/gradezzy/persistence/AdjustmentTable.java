/*
 * File: AdjustmentTable.java
 *
 * Purpose: Writes, removes and reads the marks changed by hand.
 *
 * Where it fits: Called by SqliteResultRepository, which owns the
 * connection. The changes are kept apart from the submissions, so saving
 * a run again does not forget them.
 */
package gradezzy.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.ScoreAdjustment;

/**
 * The SQL for the score_adjustments table. Each method works on the
 * connection it is given and never commits, rolls back or closes it.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class AdjustmentTable {

    // Saves one mark changed by hand
    private static final String INSERT_ADJUSTMENT = "INSERT INTO "
            + "score_adjustments (run_id, username, question_id, "
            + "previous_score, new_score, reason, saved_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    // Removes one row only, so two changes saved at the same time with
    // the same marks are taken back one at a time
    private static final String DELETE_ADJUSTMENT = "DELETE FROM "
            + "score_adjustments WHERE rowid = (SELECT rowid FROM "
            + "score_adjustments WHERE run_id = ? AND username = ? "
            + "AND question_id = ? AND saved_at = ? "
            + "AND previous_score = ? AND new_score = ? LIMIT 1)";

    // Lists the marks changed by hand in a run, oldest first
    private static final String SELECT_ADJUSTMENTS = "SELECT username, "
            + "question_id, previous_score, new_score, reason, saved_at "
            + "FROM score_adjustments WHERE run_id = ? ORDER BY saved_at";

    /**
     * Not used: every method here is static.
     */
    private AdjustmentTable() {
        // utility class, never instantiated
    }

    /**
     * Writes one mark changed by hand.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param adjustment the change
     * @throws SQLException if the row cannot be written
     */
    static void insertAdjustment(Connection connection, String runId,
            ScoreAdjustment adjustment) throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_ADJUSTMENT);
            statement.setString(1, runId);
            statement.setString(2, adjustment.getUsername());
            statement.setString(3, adjustment.getQuestionId());
            statement.setDouble(4, adjustment.getPreviousScore());
            statement.setDouble(5, adjustment.getNewScore());
            statement.setString(6, adjustment.getReason());
            statement.setString(7,
                    JdbcSupport.textOf(adjustment.getSavedAt()));
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Removes one saved row that matches the change. A change with no
     * row removes nothing.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param adjustment the change
     * @throws SQLException if the row cannot be removed
     */
    static void deleteAdjustment(Connection connection, String runId,
            ScoreAdjustment adjustment) throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(DELETE_ADJUSTMENT);
            statement.setString(1, runId);
            statement.setString(2, adjustment.getUsername());
            statement.setString(3, adjustment.getQuestionId());
            statement.setString(4,
                    JdbcSupport.textOf(adjustment.getSavedAt()));
            statement.setDouble(5, adjustment.getPreviousScore());
            statement.setDouble(6, adjustment.getNewScore());
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Reads the marks changed by hand in a run, oldest first.
     *
     * @param connection the open connection
     * @param runId the run id
     * @return the changes
     * @throws SQLException if the rows cannot be read
     */
    static List<ScoreAdjustment> readAdjustments(Connection connection,
            String runId) throws SQLException {
        List<ScoreAdjustment> changes = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_ADJUSTMENTS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                changes.add(new ScoreAdjustment(
                        rows.getString("username"),
                        rows.getString("question_id"),
                        rows.getDouble("previous_score"),
                        rows.getDouble("new_score"),
                        rows.getString("reason"),
                        JdbcSupport.parseTime(rows.getString("saved_at"))));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return changes;
    }
}
