/*
 * File: ClassFileMatch.java
 *
 * Purpose: Whether a chosen score sheet, tester folder or starter folder
 * belongs with the uploads, and a file nearby that fits them better.
 *
 * Where it fits: Returned by ClassFileMatcher. The Set up step shows the
 * problem on the file's row and offers the better file in a box with a
 * "Use this file instead" button. Grading is never blocked by it.
 */
package gradezzy.service;

import java.nio.file.Path;

/**
 * What a check of one class file against the uploads found.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ClassFileMatch {

    // Why the file does not seem to belong with the uploads, or null
    private final String problem;

    // A file or folder nearby that fits the uploads better, or null
    private final Path suggestion;

    // The words after the suggestion's name, such as ", in the same
    // folder, lists 40 of the 42 uploads."
    private final String suggestionDetail;

    /**
     * Creates a result.
     *
     * <p>Tier: SHOULD.
     *
     * @param problem why the file does not fit, or null when it fits or
     *        the check could not tell
     * @param suggestion a better file or folder, or null when none was
     *        found
     * @param suggestionDetail the words after the suggestion's name, or
     *        an empty string
     */
    public ClassFileMatch(String problem, Path suggestion,
            String suggestionDetail) {
        this.problem = problem;
        this.suggestion = suggestion;
        this.suggestionDetail = suggestionDetail;
    }

    /**
     * Returns a result that has nothing to say.
     *
     * <p>Tier: SHOULD.
     *
     * @return a result with no problem and no suggestion
     */
    public static ClassFileMatch fits() {
        return new ClassFileMatch(null, null, "");
    }

    /**
     * Says whether the file does not seem to belong with the uploads.
     *
     * <p>Tier: SHOULD.
     *
     * @return true when there is a problem to show
     */
    public boolean hasProblem() {
        return problem != null;
    }

    /**
     * Returns why the file does not seem to belong with the uploads.
     *
     * <p>Tier: SHOULD.
     *
     * @return one or two sentences, or null when it fits
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Says whether a better file or folder was found.
     *
     * <p>Tier: SHOULD.
     *
     * @return true when there is a suggestion to show
     */
    public boolean hasSuggestion() {
        return suggestion != null;
    }

    /**
     * Returns the better file or folder.
     *
     * <p>Tier: SHOULD.
     *
     * @return its path, or null when none was found
     */
    public Path getSuggestion() {
        return suggestion;
    }

    /**
     * Returns the name of the better file or folder, shown in bold.
     *
     * <p>Tier: SHOULD.
     *
     * @return the name, or an empty string when there is no suggestion
     */
    public String getSuggestionName() {
        if (suggestion == null || suggestion.getFileName() == null) {
            return "";
        }
        return suggestion.getFileName().toString();
    }

    /**
     * Returns the words shown after the suggestion's name.
     *
     * <p>Tier: SHOULD.
     *
     * @return for example ", in the same folder, lists 40 of the 42
     *         uploads."
     */
    public String getSuggestionDetail() {
        return suggestionDetail;
    }
}
