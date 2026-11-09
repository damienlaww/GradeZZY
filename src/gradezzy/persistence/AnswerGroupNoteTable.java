/*
 * File: AnswerGroupNoteTable.java
 *
 * Purpose: The SQL of the answer group notes: the name the marker gave a
 * mistake and the students who were in the group when it was checked,
 * one row per run, question and pattern.
 *
 * Where it fits: SqliteResultRepository calls these methods inside its
 * own error handling, as it does for AdjustmentTable. The table itself
 * is created by SqliteSchema.
 */
package gradezzy.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.AnswerGroupNote;
import gradezzy.util.TextUtils;

/**
 * Reads and writes the answer_group_notes table.
 *
 * <p>Tier: COULD.
 */
final class AnswerGroupNoteTable {

    // Saves a note, replacing the one saved before for the same group
    private static final String UPSERT_GROUP_NOTE = "INSERT OR REPLACE "
            + "INTO answer_group_notes (run_id, question_id, pattern, "
            + "label, checked_usernames) VALUES (?, ?, ?, ?, ?)";

    // Removes the note of one group
    private static final String DELETE_GROUP_NOTE = "DELETE FROM "
            + "answer_group_notes WHERE run_id = ? AND question_id = ? "
            + "AND pattern = ?";

    // Lists the notes of a run
    private static final String SELECT_GROUP_NOTES = "SELECT question_id, "
            + "pattern, label, checked_usernames FROM answer_group_notes "
            + "WHERE run_id = ?";

    // Separates the checked usernames saved in one column
    private static final String NAME_SEPARATOR = "\n";

    /**
     * Not used: every method here is static.
     */
    private AnswerGroupNoteTable() {
        // utility class, never instantiated
    }

    /**
     * Writes one group's note, or removes it when it holds no name and
     * no check.
     *
     * @param connection the open connection
     * @param runId the run id
     * @param note the note
     * @throws SQLException if the row cannot be written
     */
    static void writeNote(Connection connection, String runId,
            AnswerGroupNote note) throws SQLException {
        PreparedStatement statement = null;
        try {
            if (note.isEmpty()) {
                statement = connection.prepareStatement(DELETE_GROUP_NOTE);
            } else {
                statement = connection.prepareStatement(UPSERT_GROUP_NOTE);
                String checked = TextUtils.join(NAME_SEPARATOR,
                        note.getCheckedUsernames());
                statement.setString(4, note.getLabel());
                statement.setString(5, checked);
            }
            statement.setString(1, runId);
            statement.setString(2, note.getQuestionId());
            statement.setString(3, note.getPattern());
            statement.executeUpdate();
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    /**
     * Reads the notes of a run.
     *
     * @param connection the open connection
     * @param runId the run id
     * @return the notes, in no particular order
     * @throws SQLException if the rows cannot be read
     */
    static List<AnswerGroupNote> readNotes(Connection connection,
            String runId) throws SQLException {
        List<AnswerGroupNote> notes = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_GROUP_NOTES);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                notes.add(readNote(rows));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return notes;
    }

    // Reads the answer group note on the current row.
    private static AnswerGroupNote readNote(ResultSet rows)
            throws SQLException {
        String questionId = rows.getString("question_id");
        String pattern = rows.getString("pattern");
        String label = rows.getString("label");
        String saved = rows.getString("checked_usernames");
        List<String> checked = splitNames(saved);
        return new AnswerGroupNote(questionId, pattern, label, checked);
    }

    // Splits the saved checked usernames; none when empty or null.
    private static List<String> splitNames(String saved) {
        List<String> names = new ArrayList<>();
        if (saved == null) {
            return names;
        }
        if (saved.isEmpty()) {
            return names;
        }
        String[] parts = saved.split(NAME_SEPARATOR);
        for (String part : parts) {
            if (!part.isEmpty()) {
                names.add(part);
            }
        }
        return names;
    }
}
