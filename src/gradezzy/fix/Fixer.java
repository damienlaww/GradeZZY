/*
 * File: Fixer.java
 *
 * Purpose: One automatic correction of a common submission error.
 *
 * Where it fits: Implemented once per kind of error. FixManager picks the Fixer
 * that can handle an anomaly, so a new correction is one new class.
 */
package gradezzy.fix;

import gradezzy.model.Anomaly;
import gradezzy.model.Submission;

/**
 * One automatic correction of a common submission error.
 */
public interface Fixer {

    /**
     * Returns the name shown in the anomaly log.
     *
     * @return the name of this fixer
     */
    String getName();

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    boolean canFix(Anomaly anomaly);

    /**
     * Applies the fix to the working copy only. The original upload must never
     * be touched.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone
     * @throws FixException if the operation fails
     */
    FixRecord apply(Submission submission, Anomaly anomaly) throws FixException;

    /**
     * Reverses a fix that was applied earlier.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    void undo(FixRecord fixRecord) throws FixException;
}
