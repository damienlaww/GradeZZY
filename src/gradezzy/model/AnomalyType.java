/*
 * File: AnomalyType.java
 *
 * Purpose: Every kind of problem the system can report about a submission.
 *
 * Where it fits: Validators, fixers, detectors and the pipeline tag each
 * Anomaly with one of these so the log can be filtered and counted.
 */
package gradezzy.model;

/**
 * Every kind of problem the system can report about a submission.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public enum AnomalyType {
    /**
     * The zip holds Q folders directly, with no username folder.
     */
    MISSING_WRAPPER_FOLDER,

    /**
     * The top folder is not named after the student's username.
     */
    WRONG_FOLDER_NAME,

    /**
     * A Qx folder is missing.
     */
    MISSING_QUESTION_FOLDER,

    /**
     * A required Java source file is missing.
     */
    MISSING_FILE,

    /**
     * A file is present but wrongly named.
     */
    WRONG_FILE_NAME,

    /**
     * The name or email in a file header is blank.
     */
    MISSING_HEADER_NAME,

    /**
     * A Java file starts with a byte order mark or is not valid UTF-8.
     */
    WRONG_ENCODING,

    /**
     * The submission cannot be matched to anyone on the roster.
     */
    UNKNOWN_STUDENT,

    /**
     * The upload is not a readable zip file.
     */
    INVALID_ARCHIVE,

    /**
     * Another file with the same username was chosen and not graded.
     */
    DUPLICATE_SUBMISSION,

    /**
     * The student's code does not compile.
     */
    COMPILE_ERROR,

    /**
     * A tester was stopped at the time limit.
     */
    TIMEOUT,

    /**
     * A tester ended with an error.
     */
    RUNTIME_ERROR,

    /**
     * A file is identical to the starter file.
     */
    UNTOUCHED_STARTER,

    /**
     * A method body was left empty.
     */
    EMPTY_ANSWER,

    /**
     * The answer looks hard-coded. Flag only, never penalise.
     */
    HARD_CODED_ANSWER,

    /**
     * A file header names another student, by email or by name. Flag
     * only, never penalise.
     */
    COPIED_HEADER,

    /**
     * The code is very similar to another student's.
     */
    SIMILAR_CODE,

    /**
     * Called with random inputs, the student's method gives a different
     * answer from the model answer's for some of them. Flag only, never
     * penalise.
     */
    DIFFERENT_FROM_MODEL_ANSWER,

    /**
     * The grader itself failed on this submission.
     */
    INTERNAL_ERROR
}
