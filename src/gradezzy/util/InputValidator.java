/*
 * File: InputValidator.java
 *
 * Purpose: Checks what the instructor types or picks before it is used.
 *
 * Where it fits: Every screen calls these so bad input gives a clear message
 * instead of an error. The brief penalises missing input validation.
 */
package gradezzy.util;

import java.nio.file.Path;

/**
 * Checks what the instructor types or picks before it is used.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public final class InputValidator {

    /**
     * Not used: every method here is static.
     */
    private InputValidator() {
        // utility class, never instantiated
    }

    /**
     * Checks that a section code looks like G1, G2 and so on.
     *
     * @param sectionCode the section code
     * @return true if the code is valid
     */
    public static boolean isValidSectionCode(String sectionCode) {
        // TODO(Seth): implement: the check: the letter G followed by a number
        return false;
    }

    /**
     * Checks that a username is not blank and has no spaces.
     *
     * @param username the username
     * @return true if the username is valid
     */
    public static boolean isValidUsername(String username) {
        // TODO(Seth): implement: the username check
        return false;
    }

    /**
     * Checks that a path is an existing, readable file.
     *
     * @param file the file
     * @return true if the file can be read
     */
    public static boolean isReadableFile(Path file) {
        // TODO(Seth): implement: the file check
        return false;
    }

    /**
     * Checks that a path is an existing, readable folder.
     *
     * @param directory the directory
     * @return true if the folder can be read
     */
    public static boolean isReadableDirectory(Path directory) {
        // TODO(Seth): implement: the folder check
        return false;
    }

    /**
     * Checks that text is a whole number above zero.
     *
     * @param text the text
     * @return true if the text is a positive whole number
     */
    public static boolean isPositiveInteger(String text) {
        // TODO(Seth): implement: the number check without throwing an
        //     exception
        return false;
    }
}
