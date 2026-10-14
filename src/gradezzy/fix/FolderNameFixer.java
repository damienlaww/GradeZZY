/*
 * File: FolderNameFixer.java
 *
 * Purpose: Renames a wrongly named top folder to the student's username.
 *
 * Where it fits: Handles the forgotten rename case, for example a folder still
 * called RenameToYourUsername.
 */
package gradezzy.fix;

import gradezzy.model.Anomaly;
import gradezzy.model.Submission;

/**
 * Renames a wrongly named top folder to the student's username.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class FolderNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Rename folder";

    /**
     * Creates a new folder name fixer.
     */
    public FolderNameFixer() {
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
        // TODO(Jun Sheng): implement: a check for AnomalyType.WRONG_FOLDER_NAME
        return false;
    }

    /**
     * Renames a wrongly named top folder to the student's username.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        // TODO(Jun Sheng): implement: renaming the top folder of the working
        //     copy to the username
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
        // TODO(Jun Sheng): implement: renaming the folder back to its original
        //     name
    }
}
