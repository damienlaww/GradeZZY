/*
 * File: InputValidator.java
 *
 * Purpose: Checks what the instructor types or picks before it is used.
 *
 * Where it fits: Every screen calls these so bad input gives a clear message
 * instead of an error. The brief penalises missing input validation.
 */
package gradezzy.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks what the instructor types or picks before it is used. No method
 * throws an exception, whatever it is given; null is always invalid.
 */
public final class InputValidator {

    // The letter every section code starts with, in either case
    private static final char SECTION_LETTER = 'G';

    // The shortest section code: the letter and one digit
    private static final int MIN_SECTION_LENGTH = 2;

    // The smallest and largest digit characters
    private static final char FIRST_DIGIT = '0';
    private static final char LAST_DIGIT = '9';

    /**
     * Not used: every method here is static.
     */
    private InputValidator() {
        // utility class, never instantiated
    }

    /**
     * Checks that a section code looks like G1, G2 and so on. The letter
     * may be lowercase (g1), as agreed by the team. The number must not
     * start with 0, so G0 and G01 are rejected.
     *
     * @param sectionCode the section code
     * @return true if the code is valid
     */
    public static boolean isValidSectionCode(String sectionCode) {
        if (sectionCode == null
                || sectionCode.length() < MIN_SECTION_LENGTH) {
            return false;
        }

        char letter = Character.toUpperCase(sectionCode.charAt(0));
        String number = sectionCode.substring(1);

        return letter == SECTION_LETTER && number.charAt(0) != FIRST_DIGIT
                && isAllDigits(number);
    }

    /**
     * Checks that a username is not blank and has no spaces.
     *
     * @param username the username
     * @return true if the username is valid
     */
    public static boolean isValidUsername(String username) {
        if (TextUtils.isBlank(username)) {
            return false;
        }

        for (int i = 0; i < username.length(); i++) {
            if (Character.isWhitespace(username.charAt(i))) {
                return false;
            }
        }

        return true;
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
     * Checks that text is a whole number above zero that fits in an int.
     * Only the digits 0 to 9 are accepted: no sign, no spaces and no
     * decimal point, so a true answer means Integer.parseInt(text) works.
     *
     * @param text the text
     * @return true if the text is a positive whole number
     */
    public static boolean isPositiveInteger(String text) {
        if (!isAllDigits(text)) {
            return false;
        }

        // Only a number too large for an int can still fail here.
        try {
            return Integer.parseInt(text) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    // Says whether text is not empty and holds only the digits 0 to 9.
    private static boolean isAllDigits(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (character < FIRST_DIGIT || character > LAST_DIGIT) {
                return false;
            }
        }

        return true;
    }
}
