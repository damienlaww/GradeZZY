/*
 * File: RosterReader.java
 *
 * Purpose: Reads the provided score sheet CSV into Student objects.
 *
 * Where it fits: Gives the pipeline the list of expected students so
 * submissions can be matched to a roster entry.
 */
package gradezzy.ingest;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Student;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Reads the provided score sheet CSV into Student objects.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class RosterReader {

    // Column labels are a file-format contract, not configurable settings.
    private static final String ID_COLUMN = "OrgDefinedId";
    private static final String USERNAME_COLUMN = "Username";
    private static final String LAST_NAME_COLUMN = "Last Name";
    private static final String FIRST_NAME_COLUMN = "First Name";
    private static final String EMAIL_COLUMN = "Email";

    // The last column of a score sheet from eLearn, which a sheet saved
    // again by another tool may leave out of each student row
    private static final String END_OF_LINE_COLUMN = "End-of-Line Indicator";

    // The column that holds the most marks a student can get
    private static final String DENOMINATOR_COLUMN =
            "Calculated Final Grade Denominator";

    /**
     * Returned by readDenominator when the score sheet gives no single
     * denominator.
     */
    public static final double UNKNOWN_DENOMINATOR = -1.0;

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new roster reader.
     *
     * @param config the application settings
     */
    public RosterReader(AppConfig config) {
        this.config = config;
    }

    /**
     * Reads every student row of the score sheet.
     *
     * @param scoreSheetFile the score sheet file
     * @return the students in file order
     * @throws IngestException if the operation fails
     */
    public List<Student> readRoster(
            Path scoreSheetFile) throws IngestException {
        try {
            List<String> records = readRecords(scoreSheetFile);
            if (records.isEmpty()) {
                throw new IllegalArgumentException("The roster is empty.");
            }
            List<String> header = TextUtils.splitCsvLine(records.get(0));
            requireColumn(header, ID_COLUMN);
            requireColumn(header, USERNAME_COLUMN);
            requireColumn(header, LAST_NAME_COLUMN);
            requireColumn(header, FIRST_NAME_COLUMN);
            requireColumn(header, EMAIL_COLUMN);

            List<Student> students = new ArrayList<>();
            Set<String> usernames = new HashSet<>();
            for (int index = 1; index < records.size(); index++) {
                List<String> fields = TextUtils.splitCsvLine(
                        records.get(index));
                checkColumnCount(header, fields, index + 1);
                String id = removePrefix(fields.get(header.indexOf(
                        ID_COLUMN)));
                String username = removePrefix(fields.get(header.indexOf(
                        USERNAME_COLUMN)));
                if (TextUtils.isBlank(id) || TextUtils.isBlank(username)
                        || !usernames.add(username.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Missing student ID "
                            + "or missing/duplicate username in record "
                            + (index + 1) + ".");
                }
                students.add(new Student(id, username,
                        fields.get(header.indexOf(LAST_NAME_COLUMN)),
                        fields.get(header.indexOf(FIRST_NAME_COLUMN)),
                        fields.get(header.indexOf(EMAIL_COLUMN))));
            }
            return students;
        } catch (NoSuchFileException exception) {
            throw new IngestException("The score sheet "
                    + scoreSheetFile.toAbsolutePath().normalize()
                    + " does not exist. Choose the CSV file exported from "
                    + "eLearn: on Home, under Check the score sheet, or in "
                    + "config.properties (" + ConfigKeys.SCORE_SHEET_FILE
                    + ") for the console.", exception);
        } catch (IOException exception) {
            throw rosterFailure(scoreSheetFile, exception);
        } catch (IllegalArgumentException exception) {
            throw rosterFailure(scoreSheetFile, exception);
        }
    }

    /**
     * Finds the roster entry for a username.
     *
     * @param roster the roster
     * @param username the username
     * @return the student, or null if not on the roster
     */
    public Student findByUsername(List<Student> roster, String username) {
        if (roster == null || username == null) {
            return null;
        }
        for (Student student : roster) {
            if (student != null
                    && username.equalsIgnoreCase(student.getUsername())) {
                return student;
            }
        }
        return null;
    }

    /**
     * Reads the denominator of the score sheet: the value of Calculated
     * Final Grade Denominator, which the provided sheet gives as 20 on
     * every row. The question maximums should add up to it.
     *
     * @param scoreSheetFile the score sheet file
     * @return the denominator, or UNKNOWN_DENOMINATOR when the sheet
     *         cannot be read, has no such column, or its rows disagree
     */
    public double readDenominator(Path scoreSheetFile) {
        List<String> values = new ArrayList<>();
        try {
            List<String> records = readRecords(scoreSheetFile);
            if (records.isEmpty()) {
                return UNKNOWN_DENOMINATOR;
            }

            List<String> header = TextUtils.splitCsvLine(records.get(0));
            int column = header.indexOf(DENOMINATOR_COLUMN);
            if (column < 0) {
                return UNKNOWN_DENOMINATOR;
            }
            for (int index = 1; index < records.size(); index++) {
                List<String> fields = TextUtils.splitCsvLine(
                        records.get(index));
                if (column < fields.size()) {
                    values.add(fields.get(column).trim());
                }
            }
        } catch (IOException exception) {
            return UNKNOWN_DENOMINATOR;
        } catch (IllegalArgumentException exception) {
            return UNKNOWN_DENOMINATOR;
        }

        return findSingleNumber(values);
    }

    // Reads the score sheet and splits it into records, without a byte
    // order mark. Shared by readRoster and readDenominator.
    private List<String> readRecords(Path scoreSheetFile)
            throws IOException {
        String text = FileUtils.readText(scoreSheetFile);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return splitRecords(TextUtils.normaliseLineEndings(text));
    }

    // The one number every value holds, or UNKNOWN_DENOMINATOR if there is
    // none, one is not a number, or two differ
    private double findSingleNumber(List<String> values) {
        if (values.isEmpty()) {
            return UNKNOWN_DENOMINATOR;
        }

        double first = UNKNOWN_DENOMINATOR;
        try {
            first = Double.parseDouble(values.get(0));
            for (String value : values) {
                if (Double.parseDouble(value) != first) {
                    return UNKNOWN_DENOMINATOR;
                }
            }
        } catch (NumberFormatException exception) {
            return UNKNOWN_DENOMINATOR;
        }
        return first;
    }

    // Builds the error shown when the roster cannot be read or is malformed.
    private IngestException rosterFailure(Path scoreSheetFile,
            Exception exception) {
        String reason = String.valueOf(exception.getMessage()).trim();
        if (!reason.endsWith(".")) {
            reason += ".";
        }
        return new IngestException("Could not read roster "
                + scoreSheetFile + ": " + reason
                + " Choose the score sheet CSV with student columns.",
                exception);
    }

    // A row must have one value per column, except that a row short only
    // by the trailing End-of-Line column is accepted. Otherwise the
    // message names the first column the row has no value for.
    private void checkColumnCount(List<String> header, List<String> fields,
            int recordNumber) {
        int lastColumn = header.size() - 1;
        boolean onlyEndOfLineMissing = fields.size() == lastColumn
                && header.get(lastColumn).equals(END_OF_LINE_COLUMN);
        if (fields.size() < header.size() && !onlyEndOfLineMissing) {
            throw new IllegalArgumentException("Roster record "
                    + recordNumber + " has no value for the column \""
                    + header.get(fields.size()) + "\".");
        }
        if (fields.size() > header.size()) {
            throw new IllegalArgumentException("Roster record "
                    + recordNumber + " has more values than the header "
                    + "has columns.");
        }
    }

    // Requires a column to appear in the header exactly once.
    private void requireColumn(List<String> header, String name) {
        if (header.indexOf(name) < 0
                || header.indexOf(name) != header.lastIndexOf(name)) {
            throw new IllegalArgumentException("Missing or repeated column: "
                    + name);
        }
    }

    // Remove exactly one configured prefix; preserve leading zeroes.
    private String removePrefix(String value) {
        String prefix = config.getString(ConfigKeys.CSV_ID_PREFIX);
        if (!prefix.isEmpty() && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    // A quoted name can contain a newline, so physical lines alone are
    // not records. The field parser later rejects malformed quotes. A
    // record is the text from just after one splitting newline up to the
    // next one.
    private List<String> splitRecords(String text) {
        List<String> records = new ArrayList<>();
        int recordStart = 0;
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') {
                quoted = !quoted;
            }
            if (current == '\n' && !quoted) {
                addRecord(records, text.substring(recordStart, index));
                recordStart = index + 1;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unclosed roster CSV quote.");
        }
        addRecord(records, text.substring(recordStart));
        return records;
    }

    // Keeps a record unless it is blank.
    private void addRecord(List<String> records, String record) {
        if (!TextUtils.isBlank(record)) {
            records.add(record);
        }
    }
}
