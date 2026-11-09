/*
 * File: AuditTable.java
 *
 * Purpose: Writes and reads the audit log.
 *
 * Where it fits: Called by SqliteResultRepository, which owns the
 * connection. The log belongs to no run; it holds every grading, fix,
 * setting and backup action in the order they happened.
 */
package gradezzy.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.AuditEntry;

/**
 * The SQL for the audit_entries table. Each method works on the
 * connection it is given and never commits, rolls back or closes it.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class AuditTable {

    private static final String INSERT_AUDIT = "INSERT INTO audit_entries "
            + "(logged_at, action, detail) VALUES (?, ?, ?)";

    private static final String SELECT_AUDIT = "SELECT logged_at, action, "
            + "detail FROM audit_entries ORDER BY entry_id";

    /**
     * Not used: every method here is static.
     */
    private AuditTable() {
        // utility class, never instantiated
    }

    /**
     * Writes one row of the audit log.
     *
     * @param connection the open connection
     * @param auditEntry the audit entry
     * @throws SQLException if the row cannot be written
     */
    static void insertAudit(Connection connection, AuditEntry auditEntry)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_AUDIT);
            statement.setString(1,
                    JdbcSupport.textOf(auditEntry.getTimestamp()));
            statement.setString(2, auditEntry.getAction());
            statement.setString(3, auditEntry.getDetail());
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Reads the whole audit log, oldest first.
     *
     * @param connection the open connection
     * @return the audit entries
     * @throws SQLException if the rows cannot be read
     */
    static List<AuditEntry> readAudit(Connection connection)
            throws SQLException {
        List<AuditEntry> entries = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_AUDIT);
            rows = statement.executeQuery();
            while (rows.next()) {
                entries.add(new AuditEntry(
                        JdbcSupport.parseTime(rows.getString("logged_at")),
                        rows.getString("action"),
                        rows.getString("detail")));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return entries;
    }
}
