/*
 * File: HeaderNameFixer.java
 *
 * Purpose: Writes the student's name and email into a blank file header.
 *
 * Where it fits: Handles the missing name case, using the roster entry matched
 * from the zip file name.
 */
package gradezzy.fix;

import gradezzy.model.Anomaly;
import gradezzy.model.Submission;

/**
 * Writes the student's name and email into a blank file header.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class HeaderNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fill in header";

    /**
     * Creates a new header name fixer.
     */
    public HeaderNameFixer() {
        super(NAME);
    }

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        // TODO(Jun Sheng): implement: a check for
        //     AnomalyType.MISSING_HEADER_NAME
        return false;
    }

    /**
     * Writes the student's name and email into a blank file header.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        // TODO(Jun Sheng): implement: writing the name and email after the
        //     header labels, after calling backup on the file
        return null;
    }

    /**
     * Reverses this fix.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        // TODO(Jun Sheng): implement: restoring the file from the backup
    }
}
