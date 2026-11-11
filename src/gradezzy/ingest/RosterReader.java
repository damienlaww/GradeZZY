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

    // The mark some editors put at the very start of a UTF-8 file
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    // Opens and closes a quoted CSV field
    private static final char QUOTE = '"';

    // Ends a record once the line endings are normalised
    private static final char LINE_END = '\n';

    // Ends the sentence of an error message
    private static final String SENTENCE_END = ".";

    // The separator some spreadsheet programs use in place of a comma
    private static final String SEMICOLON = ";";

    // Told when the score sheet uses ; between its values
    private static final String SEMICOLON_PROBLEM = "No OrgDefinedId "
            + "column. The file seems to use ; between values. Save it "
            + "again as CSV with commas.";

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
     * Reads every student row of the score sheet. A row whose cells are
     * all empty, as Excel leaves at the end of a sheet, is skipped. The
     * id and username lose their prefix and the spaces around them. A
     * sheet with no student row is refused, like an empty file.
     *
     * @param scoreSheetFile the score sheet file
     * @return the students in file order, at least one
     * @throws IngestException if the operation fails
     */
    public List<Student> readRoster(
            Path scoreSheetFile) throws IngestException {
        // A new assessment has no score sheet until one is chosen
        boolean noSheet = scoreSheetFile == null
                || scoreSheetFile.toString().trim().isEmpty();
        if (noSheet) {
            throw new IngestException("No score sheet is chosen for this "
                    + "section. Choose the CSV file exported from eLearn on "
                    + "Set up, or in config.properties ("
                    + ConfigKeys.SCORE_SHEET_FILE + ") for the console.");
        }
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
                int recordNumber = index + 1;
                String record = records.get(index);
                // Excel keeps rows of empty cells, such as ,,,,,,, at the
                // end of a sheet; they hold no student
                if (!isEmptyRow(record)) {
                    students.add(readStudent(header, record, recordNumber,
                            usernames));
                }
            }
            // A header with no student row would mark every upload as not
            // on the roster and export an empty sheet
            if (students.isEmpty()) {
                throw new IllegalArgumentException("The roster has a header "
                        + "but no student rows.");
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
     * Reads the score sheet as grading would, and says in plain words why
     * it cannot be used, so Set up can stop grading from starting with a
     * score sheet read wrongly. A sheet that uses ; between its values
     * gets its own reason.
     *
     * @param scoreSheetFile the score sheet file
     * @return why the score sheet cannot be used, or "" when it can
     */
    public String findProblem(Path scoreSheetFile) {
        try {
            readRoster(scoreSheetFile);
        } catch (IngestException exception) {
            return describeProblem(scoreSheetFile, exception);
        }
        return "";
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
                String record = records.get(index);
                List<String> fields = TextUtils.splitCsvLine(record);
                boolean hasValue = column < fields.size()
                        && !isEmptyRow(record);
                if (hasValue) {
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

    // Reads one student row; usernames holds the lower-case usernames of
    // the rows before it, and gets this row's username added.
    private Student readStudent(List<String> header, String record,
            int recordNumber, Set<String> usernames) {
        List<String> fields = TextUtils.splitCsvLine(record);
        checkColumnCount(header, fields, recordNumber);
        String id = readId(fields.get(header.indexOf(ID_COLUMN)));
        String username = readId(fields.get(header.indexOf(
                USERNAME_COLUMN)));
        boolean badRow = TextUtils.isBlank(id)
                || TextUtils.isBlank(username)
                || !usernames.add(username.toLowerCase(Locale.ROOT));
        if (badRow) {
            throw new IllegalArgumentException("Missing student ID "
                    + "or missing/duplicate username in record "
                    + recordNumber + ".");
        }
        return new Student(id, username,
                fields.get(header.indexOf(LAST_NAME_COLUMN)),
                fields.get(header.indexOf(FIRST_NAME_COLUMN)),
                fields.get(header.indexOf(EMAIL_COLUMN)));
    }

    // Reads the score sheet and splits it into records, without a byte
    // order mark. Shared by readRoster and readDenominator.
    private List<String> readRecords(Path scoreSheetFile)
            throws IOException {
        String text = FileUtils.readText(scoreSheetFile);
        if (text.startsWith(BYTE_ORDER_MARK)) {
            text = text.substring(BYTE_ORDER_MARK.length());
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

    // The plain reason a score sheet cannot be used: the semicolon
    // reason, else the reason readRoster found, without the file's path
    private String describeProblem(Path scoreSheetFile,
            IngestException exception) {
        if (usesSemicolons(scoreSheetFile)) {
            return SEMICOLON_PROBLEM;
        }
        Throwable cause = exception.getCause();
        if (cause instanceof IllegalArgumentException) {
            String reason = String.valueOf(cause.getMessage()).trim();
            if (!reason.endsWith(SENTENCE_END)) {
                reason += SENTENCE_END;
            }
            return reason;
        }
        return exception.getMessage();
    }

    // Says whether the header has no comma-separated columns but has ;
    // between them, as a sheet saved by Excel in some countries has.
    private boolean usesSemicolons(Path scoreSheetFile) {
        try {
            List<String> records = readRecords(scoreSheetFile);
            if (records.isEmpty()) {
                return false;
            }
            String header = records.get(0);
            boolean oneColumn = TextUtils.splitCsvLine(header).size() == 1;
            return oneColumn && header.contains(SEMICOLON);
        } catch (IOException exception) {
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    // Builds the error shown when the roster cannot be read or is malformed.
    private IngestException rosterFailure(Path scoreSheetFile,
            Exception exception) {
        String reason = String.valueOf(exception.getMessage()).trim();
        if (!reason.endsWith(SENTENCE_END)) {
            reason += SENTENCE_END;
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
        if (header.indexOf(name) < 0) {
            throw new IllegalArgumentException("No " + name + " column.");
        }
        if (header.indexOf(name) != header.lastIndexOf(name)) {
            throw new IllegalArgumentException("The " + name
                    + " column appears more than once.");
        }
    }

    // Says whether every field of a record is blank, as in ,,,,,,, from
    // Excel.
    private boolean isEmptyRow(String record) {
        for (String field : TextUtils.splitCsvLine(record)) {
            if (!TextUtils.isBlank(field)) {
                return false;
            }
        }
        return true;
    }

    // Reads an id or username: without the spaces around it and without
    // its prefix, so "#ping.lee.2023 " and "# ping.lee.2023" both give
    // ping.lee.2023.
    private String readId(String value) {
        return removePrefix(value.trim()).trim();
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
            if (current == QUOTE) {
                quoted = !quoted;
            } else if (current == LINE_END && !quoted) {
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
