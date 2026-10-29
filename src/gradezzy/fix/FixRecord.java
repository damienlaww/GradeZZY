/*
 * File: FixRecord.java
 *
 * Purpose: A record of one automatic correction and how to reverse it.
 *
 * Where it fits: Kept for every applied fix so the instructor can approve or
 * undo it.
 */
package gradezzy.fix;

import java.nio.file.Path;

import gradezzy.model.Anomaly;

/**
 * A record of one automatic correction and how to reverse it.
 */
public class FixRecord {

    // The anomaly that was fixed
    private final Anomaly anomaly;

    // The name of the fixer that did it
    private final String fixerName;

    // What was changed, in plain words
    private final String description;

    // The working copy the fix changed
    private final Path workingCopy;

    // The path before the fix
    private final Path originalPath;

    // The path after the fix
    private final Path fixedPath;

    // The backup needed to undo the fix
    private final Path backupPath;

    /**
     * Creates a new fix record.
     *
     * @param anomaly the anomaly that was fixed
     * @param fixerName the name of the fixer that did it
     * @param description what was changed, in plain words
     * @param workingCopy the working copy the fix changed
     * @param originalPath the path before the fix
     * @param fixedPath the path after the fix
     * @param backupPath the backup needed to undo the fix
     */
    public FixRecord(Anomaly anomaly, String fixerName, String description,
            Path workingCopy, Path originalPath, Path fixedPath,
            Path backupPath) {
        this.anomaly = anomaly;
        this.fixerName = fixerName;
        this.description = description;
        this.workingCopy = workingCopy;
        this.originalPath = originalPath;
        this.fixedPath = fixedPath;
        this.backupPath = backupPath;
    }

    /**
     * Returns the anomaly that was fixed.
     *
     * @return the anomaly that was fixed
     */
    public Anomaly getAnomaly() {
        return anomaly;
    }

    /**
     * Returns the name of the fixer that did it.
     *
     * @return the name of the fixer that did it
     */
    public String getFixerName() {
        return fixerName;
    }

    /**
     * Returns what was changed, in plain words.
     *
     * @return what was changed, in plain words
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the working copy the fix changed.
     *
     * @return the working copy the fix changed
     */
    public Path getWorkingCopy() {
        return workingCopy;
    }

    /**
     * Returns the path before the fix.
     *
     * @return the path before the fix
     */
    public Path getOriginalPath() {
        return originalPath;
    }

    /**
     * Returns the path after the fix.
     *
     * @return the path after the fix
     */
    public Path getFixedPath() {
        return fixedPath;
    }

    /**
     * Returns the backup needed to undo the fix.
     *
     * @return the backup needed to undo the fix
     */
    public Path getBackupPath() {
        return backupPath;
    }
}
