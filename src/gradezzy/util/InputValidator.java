/*
 * File: InputValidator.java
 *
 * Purpose: Checks what the instructor picks before it is used.
 *
 * Where it fits: The Set up step calls these through LiveBackend, so a
 * missing or unreadable file gives a clear message instead of an error.
 * The brief penalises missing input validation.
 */
package gradezzy.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Checks what the instructor picks before it is used. No method throws
 * an exception, whatever it is given; null is always invalid.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public final class InputValidator {

    // A mark as typed: an optional sign, then digits with at most one
    // decimal point or comma. Double.parseDouble alone also takes 2f,
    // 1e0, NaN and Infinity.
    private static final Pattern MARK_PATTERN =
            Pattern.compile("[+-]?(\\d+([.,]\\d*)?|[.,]\\d+)");

    // The decimal comma some keyboards type, and the point Java reads
    private static final char DECIMAL_COMMA = ',';
    private static final char DECIMAL_POINT = '.';

    /**
     * Not used: every method here is static.
     */
    private InputValidator() {
        // utility class, never instantiated
    }

    /**
     * Checks that a path is an existing, readable file.
     *
     * @param file the file
     * @return true if the file can be read
     */
    public static boolean isReadableFile(Path file) {
        return file != null && Files.isRegularFile(file)
                && Files.isReadable(file);
    }

    /**
     * Checks that a path is an existing, readable folder.
     *
     * @param directory the directory
     * @return true if the folder can be read
     */
    public static boolean isReadableDirectory(Path directory) {
        return directory != null && Files.isDirectory(directory)
                && Files.isReadable(directory);
    }

    /**
     * Reads a typed mark: an optional sign, then digits with at most one
     * decimal point or comma, such as 2, 2.5 or 2,5. Anything else, such
     * as 2f or 1e0, is not a mark.
     *
     * @param markText the mark as typed
     * @return the mark, or NaN when the text is not a mark
     */
    public static double parseMark(String markText) {
        if (markText == null) {
            return Double.NaN;
        }
        String trimmed = markText.trim();
        if (!MARK_PATTERN.matcher(trimmed).matches()) {
            return Double.NaN;
        }
        return Double.parseDouble(trimmed.replace(DECIMAL_COMMA,
                DECIMAL_POINT));
    }
}
