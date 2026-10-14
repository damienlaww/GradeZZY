/*
 * File: FolderHierarchyFixer.java
 *
 * Purpose: Moves Q folders that sit at the top of the zip into a new folder
 * named after the username.
 *
 * Where it fits: Handles the improper hierarchy case from the brief.
 */
package gradezzy.fix;

import gradezzy.model.Anomaly;
import gradezzy.model.Submission;

/**
 * Moves Q folders that sit at the top of the zip into a new folder named after
 * the username.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class FolderHierarchyFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Add username folder";

    /**
     * Creates a new folder hierarchy fixer.
     */
    public FolderHierarchyFixer() {
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
        //     AnomalyType.MISSING_WRAPPER_FOLDER
        return false;
    }

    /**
     * Moves Q folders that sit at the top of the zip into a new folder named
     * after the username.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        // TODO(Jun Sheng): implement: creating the username folder and moving
        //     the Q folders into it
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
        // TODO(Jun Sheng): implement: moving the Q folders back and removing
        //     the folder
    }
}
