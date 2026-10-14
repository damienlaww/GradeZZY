/*
 * File: WorkingCopyManager.java
 *
 * Purpose: Creates and removes the working copy of each submission.
 *
 * Where it fits: Guarantees that original uploads are never modified: every fix
 * and every tester run happens in the working copy.
 */
package gradezzy.ingest;

import java.nio.file.Path;

import gradezzy.config.AppConfig;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Creates and removes the working copy of each submission.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class WorkingCopyManager {

    // The application settings
    private final AppConfig config;

    // The helper that unzips archives
    private final ZipExtractor zipExtractor;

    /**
     * Creates a new working copy manager.
     *
     * @param config the application settings
     * @param zipExtractor the helper that unzips archives
     */
    public WorkingCopyManager(AppConfig config, ZipExtractor zipExtractor) {
        this.config = config;
        this.zipExtractor = zipExtractor;
    }

    /**
     * Unzips or copies a submission into the working folder.
     *
     * @param submission the submission
     * @return the path of the new working copy
     * @throws IngestException if the operation fails
     */
    public Path createWorkingCopy(
            Submission submission) throws IngestException {
        // TODO(Seth): implement: creating WORKING_DIR/<username> and filling
        //     it from the original zip or folder
        return null;
    }

    /**
     * Deletes compiled files the student submitted, keeping the provided ones
     * listed in config.
     *
     * @param workingCopy the working copy
     * @param assessment the assessment
     */
    public void removeStudentClassFiles(Path workingCopy,
            Assessment assessment) {
        // TODO(Seth): implement: deleting .class files not in
        //     PROVIDED_CLASS_FILES
    }

    /**
     * Removes the working copy of a submission.
     *
     * @param submission the submission
     */
    public void deleteWorkingCopy(Submission submission) {
        // TODO(Seth): implement: deleting the working copy folder
    }
}
