/*
 * File: ReviewTables.java
 *
 * Purpose: Writes and reads what the instructor marks while reviewing: the
 * fix status and review decision of each anomaly, and the checked 0s.
 *
 * Where it fits: Called by SqliteResultRepository, which owns the
 * connection and the transaction. Saving a run or a student again reads
 * the decisions here first and writes them back afterwards.
 */
package gradezzy.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.FixStatus;

/**
 * The SQL for the fix_status and decision columns of the anomalies table,
 * and for the zero_checks table. Each method works on the connection it
 * is given and never commits, rolls back or closes it.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class ReviewTables {

    private static final String UPDATE_FIX_STATUS = "UPDATE anomalies "
            + "SET fix_status = ? WHERE anomaly_id = ?";

    private static final String UPDATE_DECISION = "UPDATE anomalies "
            + "SET decision = ? WHERE anomaly_id = ?";

    private static final String SELECT_DECISIONS = "SELECT anomaly_id, "
            + "decision FROM anomalies WHERE run_id = ? "
            + "AND decision IS NOT NULL";

    // Saves, removes and lists the 0s marked as checked
    private static final String INSERT_ZERO_CHECK = "INSERT OR IGNORE "
            + "INTO zero_checks (run_id, username) VALUES (?, ?)";

    private static final String DELETE_ZERO_CHECK = "DELETE FROM "
            + "zero_checks WHERE run_id = ? AND username = ?";

    private static final String SELECT_ZERO_CHECKS = "SELECT username "
            + "FROM zero_checks WHERE run_id = ? ORDER BY username";

    /**
     * Not used: every method here is static.
     */
    private ReviewTables() {
        // utility class, never instantiated
    }

    /**
     * Changes the saved fix status of one anomaly. An anomaly with no row
     * changes nothing.
     *
     * @param connection the open connection
     * @param anomalyId the anomaly id
     * @param fixStatus the fix status
     * @throws SQLException if the row cannot be changed
     */
    static void writeFixStatus(Connection connection, String anomalyId,
            FixStatus fixStatus) throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(UPDATE_FIX_STATUS);
            statement.setString(1, fixStatus.name());
            statement.setString(2, anomalyId);
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Changes the saved review decision of one anomaly. An anomaly with
     * no row changes nothing.
     *
     * @param connection the open connection
     * @param anomalyId the anomaly id
     * @param decision the decision name, or null to clear it
     * @throws SQLException if the row cannot be changed
     */
    static void writeDecision(Connection connection, String anomalyId,
            String decision) throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(UPDATE_DECISION);
            statement.setString(1, decision);
            statement.setString(2, anomalyId);
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Writes back decisions read before a run or student was saved
     * again. A flag that is gone has no row, so its decision is dropped.
     *
     * @param connection the open connection
     * @param decisions the decision names by anomaly id
     * @throws SQLException if a row cannot be changed
     */
    static void writeDecisions(Connection connection,
            Map<String, String> decisions) throws SQLException {
        for (String anomalyId : decisions.keySet()) {
            writeDecision(connection, anomalyId, decisions.get(anomalyId));
        }
    }

    /**
     * Reads the saved review decisions of a run.
     *
     * @param connection the open connection
     * @param runId the run id
     * @return the decision names by anomaly id; anomalies with no
     *         decision are left out
     * @throws SQLException if the rows cannot be read
     */
    static Map<String, String> readDecisions(Connection connection,
            String runId) throws SQLException {
        Map<String, String> decisions = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_DECISIONS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                decisions.put(rows.getString("anomaly_id"),
                        rows.getString(SqliteSchema.DECISION_COLUMN));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return decisions;
    }

    /**
     * Saves or removes one checked 0. Saving one twice keeps one row.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param username the student
     * @param checked true to save the check, false to remove it
     * @throws SQLException if the row cannot be written
     */
    static void writeZeroCheck(Connection connection, String runId,
            String username, boolean checked) throws SQLException {
        String sql = DELETE_ZERO_CHECK;
        if (checked) {
            sql = INSERT_ZERO_CHECK;
        }
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

    /**
     * Reads the usernames of a run whose 0 was checked.
     *
     * @param connection the open connection
     * @param runId the run id
     * @return the usernames, in alphabetical order
     * @throws SQLException if the rows cannot be read
     */
    static List<String> readZeroChecks(Connection connection, String runId)
            throws SQLException {
        List<String> usernames = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_ZERO_CHECKS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                usernames.add(rows.getString("username"));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return usernames;
    }
}
