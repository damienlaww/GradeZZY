/*
 * File: JdbcSupport.java
 *
 * Purpose: Closing helpers and value conversions shared by the SQLite
 * classes.
 *
 * Where it fits: Used only inside this package, by the classes that hold
 * SQL, so each of them closes statements and stores values the same way.
 */
package gradezzy.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Closes JDBC statements and rows from finally blocks, and turns enums,
 * paths and times into text for the database and back.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class JdbcSupport {

    /**
     * Not used: every method here is static.
     */
    private JdbcSupport() {
        // utility class, never instantiated
    }

    /**
     * Closes a statement opened inside a try block, from its finally
     * block. Null means the statement was never opened.
     *
     * @param statement the statement, or null
     * @throws SQLException if closing fails
     */
    static void closeStatement(Statement statement) throws SQLException {
        if (statement != null) {
            statement.close();
        }
    }

    /**
     * Closes the rows of a query, then the statement that made them. The
     * statement is closed even if closing the rows fails. Null means that
     * one was never opened.
     *
     * @param rows the rows, or null
     * @param statement the statement, or null
     * @throws SQLException if closing fails
     */
    static void closeQuery(ResultSet rows, Statement statement)
            throws SQLException {
        try {
            if (rows != null) {
                rows.close();
            }
        } finally {
            closeStatement(statement);
        }
    }

    /**
     * Closes two statements in the order given. The second is closed
     * even if closing the first fails.
     *
     * @param first the statement to close first, or null
     * @param second the statement to close second, or null
     * @throws SQLException if closing fails
     */
    static void closeStatements(Statement first, Statement second)
            throws SQLException {
        try {
            closeStatement(first);
        } finally {
            closeStatement(second);
        }
    }

    /**
     * Stores enums, paths and times as text; null stays null.
     *
     * @param value the value, or null
     * @return the text, or null
     */
    static String textOf(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * Reads a time saved by textOf; null stays null.
     *
     * @param text the saved text, or null
     * @return the time, or null
     */
    static LocalDateTime parseTime(String text) {
        if (text == null) {
            return null;
        }
        return LocalDateTime.parse(text);
    }

    /**
     * Reads a path saved by textOf; null stays null.
     *
     * @param text the saved text, or null
     * @return the path, or null
     */
    static Path toPath(String text) {
        if (text == null) {
            return null;
        }
        return Paths.get(text);
    }
}
