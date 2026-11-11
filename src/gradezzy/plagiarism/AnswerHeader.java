/*
 * File: AnswerHeader.java
 *
 * Purpose: What one answer file's header and code say, for comparing
 * two files that give the same Email ID.
 *
 * Where it fits: Made and compared by IdenticalCopyFinder only.
 */
package gradezzy.plagiarism;

import java.util.Set;

/**
 * What one answer file's header and code say: the student, the Email
 * ID, the Name, the code and its fingerprints.
 *
 * <p>Tier: COULD.
 */
class AnswerHeader {

    // The student who handed the file in
    private final String username;

    // The Email ID, trimmed and in lower case
    private final String email;

    // The Name, in lower case with single spaces, or ""
    private final String name;

    // The tokens of the code, spaces and comments left out
    private final String code;

    // The fingerprints of the code, names set aside
    private final Set<Integer> fingerprints;

    /**
     * Creates what one answer file says.
     *
     * @param username the student who handed the file in
     * @param email the Email ID, trimmed and in lower case
     * @param name the Name, in lower case with single spaces, or ""
     * @param code the tokens of the code, spaces and comments left out
     * @param fingerprints the fingerprints of the code, names set aside
     */
    AnswerHeader(String username, String email, String name, String code,
            Set<Integer> fingerprints) {
        this.username = username;
        this.email = email;
        this.name = name;
        this.code = code;
        this.fingerprints = fingerprints;
    }

    /**
     * Returns the student who handed the file in.
     *
     * @return the username
     */
    String getUsername() {
        return username;
    }

    /**
     * Returns the Email ID, trimmed and in lower case.
     *
     * @return the Email ID
     */
    String getEmail() {
        return email;
    }

    /**
     * Returns the Name, in lower case with single spaces.
     *
     * @return the Name, or "" when the header gives none
     */
    String getName() {
        return name;
    }

    /**
     * Returns the tokens of the code, spaces and comments left out.
     *
     * @return the code
     */
    String getCode() {
        return code;
    }

    /**
     * Returns the fingerprints of the code, names set aside.
     *
     * @return the hashes of the fingerprints
     */
    Set<Integer> getFingerprints() {
        return fingerprints;
    }
}
