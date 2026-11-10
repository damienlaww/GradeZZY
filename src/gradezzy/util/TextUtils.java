/*
 * File: TextUtils.java
 *
 * Purpose: Small text helpers shared by several packages.
 *
 * Where it fits: Holds the CSV line handling used by the roster reader and the
 * exporters, and the reading of UTF-16 source files shared by the header
 * check and the encoding fix.
 */
package gradezzy.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small text helpers shared by several packages.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public final class TextUtils {

    // The two bytes at the start of a little-endian UTF-16 file, as
    // Notepad saves "Unicode"
    private static final byte[] UTF16_LE_MARK = {
        (byte) 0xFF, (byte) 0xFE
    };

    // The character a UTF-8 byte order mark is read as, at the start of a
    // file Notepad saved as "UTF-8 with BOM"
    private static final String UTF8_MARK = "\uFEFF";

    // The two bytes at the start of a big-endian UTF-16 file, as Notepad
    // saves "Unicode big endian"
    private static final byte[] UTF16_BE_MARK = {
        (byte) 0xFE, (byte) 0xFF
    };

    // The quote around a CSV field that holds a separator or a quote
    private static final char QUOTE = '"';

    // The quote as text, and the two quotes that stand for one inside a
    // quoted field
    private static final String QUOTE_TEXT = "\"";
    private static final String DOUBLED_QUOTE = "\"\"";

    // The character between two CSV fields, and the same as text
    private static final char FIELD_SEPARATOR = ',';
    private static final String FIELD_SEPARATOR_TEXT = ",";

    // The two characters a line ending is made of, alone or together
    private static final char CARRIAGE_RETURN = '\r';
    private static final char LINE_FEED = '\n';

    // The same characters as text, and the Windows line ending
    private static final String CARRIAGE_RETURN_TEXT = "\r";
    private static final String LINE_FEED_TEXT = "\n";
    private static final String WINDOWS_LINE_END = "\r\n";

    /**
     * Not used: every method here is static.
     */
    private TextUtils() {
        // utility class, never instantiated
    }

    /**
     * Says whether text is null, empty or only spaces.
     *
     * @param text the text
     * @return true if there is no real text
     */
    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Splits one CSV record into values, retaining empty fields. Doubled
     * quotes escape quotes inside a quoted field. Line endings are allowed
     * only inside quotes; spaces outside closing quotes are rejected.
     *
     * @param line the line
     * @return the values of the line
     * @throws IllegalArgumentException if quotes are malformed or a field
     *         contains an unquoted line ending
     */
    public static List<String> splitCsvLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("CSV line must not be null.");
        }

        List<String> values = new ArrayList<>();
        String value = "";
        boolean quoted = false;
        boolean closedQuote = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            int nextIndex = index + 1;
            boolean quoteFollows = nextIndex < line.length()
                    && line.charAt(nextIndex) == QUOTE;
            boolean lineEnd = current == CARRIAGE_RETURN
                    || current == LINE_FEED;
            if (quoted) {
                if (current != QUOTE) {
                    value += current;
                } else if (quoteFollows) {
                    value += QUOTE;
                    index++;
                } else {
                    quoted = false;
                    closedQuote = true;
                }
            } else if (current == FIELD_SEPARATOR) {
                values.add(value);
                value = "";
                closedQuote = false;
            } else if (closedQuote || lineEnd) {
                throw new IllegalArgumentException("Malformed CSV field.");
            } else if (current == QUOTE) {
                if (!value.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A CSV quote must start a field.");
                }
                quoted = true;
            } else {
                value += current;
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Unclosed CSV quote.");
        }
        values.add(value);
        return values;
    }

    /**
     * Joins values into one CSV line.
     *
     * @param values the values
     * @return the CSV line, without a line ending
     * @throws IllegalArgumentException if a value is null
     */
    public static String joinCsvLine(List<String> values) {
        List<String> fields = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(
                        "CSV values must not be null.");
            }
            boolean needsQuotes = value.contains(FIELD_SEPARATOR_TEXT)
                    || value.contains(QUOTE_TEXT)
                    || value.contains(CARRIAGE_RETURN_TEXT)
                    || value.contains(LINE_FEED_TEXT);
            if (needsQuotes) {
                String escaped = value.replace(QUOTE_TEXT, DOUBLED_QUOTE);
                fields.add(QUOTE_TEXT + escaped + QUOTE_TEXT);
            } else {
                fields.add(value);
            }
        }
        return join(FIELD_SEPARATOR_TEXT, fields);
    }

    /**
     * Joins values into one CSV line, for a row that is written as a fixed
     * array, such as a header.
     *
     * @param values the values
     * @return the CSV line, without a line ending
     * @throws IllegalArgumentException if a value is null
     */
    public static String joinCsvLine(String[] values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value);
        }
        return joinCsvLine(list);
    }

    /**
     * Joins values into one text, with the separator between each pair.
     * An empty list gives empty text and a null value is written as null.
     *
     * <p>Tier: MUST.
     *
     * @param separator the text put between two values
     * @param values the values, in order
     * @return the values joined together
     */
    public static String join(String separator, List<String> values) {
        String[] texts = new String[values.size()];
        for (int index = 0; index < values.size(); index++) {
            texts[index] = values.get(index);
        }
        return join(separator, texts);
    }

    /**
     * Joins values into one text, with the separator between each pair.
     * An empty array gives empty text and a null value is written as null.
     * The characters are copied into one array of the final length, so
     * joining many long values (a whole JSON export) stays quick.
     *
     * <p>Tier: MUST.
     *
     * @param separator the text put between two values
     * @param values the values, in order
     * @return the values joined together
     */
    public static String join(String separator, String[] values) {
        if (values.length == 0) {
            return "";
        }

        String first = String.valueOf(values[0]);
        int length = first.length();
        for (int index = 1; index < values.length; index++) {
            length += separator.length();
            length += String.valueOf(values[index]).length();
        }

        char[] joined = new char[length];
        int count = copyInto(first, joined, 0);
        for (int index = 1; index < values.length; index++) {
            count = copyInto(separator, joined, count);
            count = copyInto(String.valueOf(values[index]), joined, count);
        }
        return new String(joined);
    }

    /**
     * Converts all line endings to a single newline character.
     *
     * @param text the text
     * @return the text with uniform line endings
     */
    public static String normaliseLineEndings(String text) {
        return text.replace(WINDOWS_LINE_END, LINE_FEED_TEXT)
                .replace(CARRIAGE_RETURN, LINE_FEED);
    }

    /**
     * Says whether the bytes start with a UTF-16 byte order mark, little
     * or big endian.
     *
     * @param bytes the content of a file
     * @return true if they start with FF FE or FE FF
     */
    public static boolean isUtf16(byte[] bytes) {
        return startsWith(bytes, UTF16_LE_MARK)
                || startsWith(bytes, UTF16_BE_MARK);
    }

    /**
     * Reads the content of a source file as text. A file that starts with
     * a UTF-16 byte order mark is read as UTF-16 in that byte order,
     * leaving the mark out. Any other file is read as UTF-8, leaving out
     * a UTF-8 byte order mark, and a character that is not valid UTF-8 is
     * replaced.
     *
     * @param bytes the content of the file
     * @return the text
     */
    public static String decodeSource(byte[] bytes) {
        if (!isUtf16(bytes)) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith(UTF8_MARK)) {
                return text.substring(UTF8_MARK.length());
            }
            return text;
        }

        byte[] withoutMark = Arrays.copyOfRange(bytes, UTF16_LE_MARK.length,
                bytes.length);
        if (startsWith(bytes, UTF16_LE_MARK)) {
            return new String(withoutMark, StandardCharsets.UTF_16LE);
        }
        return new String(withoutMark, StandardCharsets.UTF_16BE);
    }

    // Says whether the bytes start with the given bytes.
    private static boolean startsWith(byte[] bytes, byte[] start) {
        if (bytes.length < start.length) {
            return false;
        }

        for (int i = 0; i < start.length; i++) {
            if (bytes[i] != start[i]) {
                return false;
            }
        }
        return true;
    }

    // Copies the characters of text into target, starting at start, and
    // returns the position just after the last one copied.
    private static int copyInto(String text, char[] target, int start) {
        for (int index = 0; index < text.length(); index++) {
            target[start + index] = text.charAt(index);
        }
        return start + text.length();
    }
}
