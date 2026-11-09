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
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class FixRecord {

    // Goes between the fixer's name and the path in a fix key
    private static final String KEY_SEPARATOR = ":";

    // Separates folders in the path of a fix key, on every system
    private static final String KEY_FOLDER_SEPARATOR = "/";

    // Separates folders in a path on Windows
    private static final String WINDOWS_SEPARATOR = "\\";

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

    // What the student had, shown as Before, or null if not recorded
    private final String valueBefore;

    // What the working copy holds after the fix, or null if not recorded
    private final String valueAfter;

    // Names the fix by the uploaded path; set by FixManager
    private String fixKey;

    /**
     * Creates a new fix record with no Before and After values.
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
        this(anomaly, fixerName, description, workingCopy, originalPath,
                fixedPath, backupPath, null, null);
    }

    /**
     * Creates a new fix record that says what changed, before and after.
     *
     * @param anomaly the anomaly that was fixed
     * @param fixerName the name of the fixer that did it
     * @param description what was changed, in plain words
     * @param workingCopy the working copy the fix changed
     * @param originalPath the path before the fix
     * @param fixedPath the path after the fix
     * @param backupPath the backup needed to undo the fix
     * @param valueBefore what the student had, such as a folder name
     * @param valueAfter what the working copy holds after the fix
     */
    public FixRecord(Anomaly anomaly, String fixerName, String description,
            Path workingCopy, Path originalPath, Path fixedPath,
            Path backupPath, String valueBefore, String valueAfter) {
        this.anomaly = anomaly;
        this.fixerName = fixerName;
        this.description = description;
        this.workingCopy = workingCopy;
        this.originalPath = originalPath;
        this.fixedPath = fixedPath;
        this.backupPath = backupPath;
        this.valueBefore = valueBefore;
        this.valueAfter = valueAfter;
        this.fixKey = null;
    }

    /**
     * Makes the key of a fix: the fixer's name, a colon and the path the
     * fix changes, relative to the working copy, with forward slashes.
     *
     * @param fixerName the name of the fixer
     * @param workingCopy the working copy
     * @param path the path the fix changes, as the student uploaded it
     * @return the key, such as Fix letter case:q3
     */
    public static String makeFixKey(String fixerName, Path workingCopy,
            Path path) {
        String relativePath = workingCopy.relativize(path).toString();
        String keyPath = relativePath.replace(WINDOWS_SEPARATOR,
                KEY_FOLDER_SEPARATOR);
        return fixerName + KEY_SEPARATOR + keyPath;
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

    /**
     * Returns what the student had, such as RenameToYourUsername/.
     *
     * @return the value before the fix, or null if not recorded
     */
    public String getValueBefore() {
        return valueBefore;
    }

    /**
     * Returns what the working copy holds after the fix, such as
     * ping.lee.2023/.
     *
     * @return the value after the fix, or null if not recorded
     */
    public String getValueAfter() {
        return valueAfter;
    }

    /**
     * Returns the key that names this fix the same way each time the
     * student is graded, such as Fix letter case:q3. Its path is the one
     * the student uploaded, before any other fix.
     *
     * @return the fix key
     */
    public String getFixKey() {
        if (fixKey != null) {
            return fixKey;
        }
        return makeFixKey(fixerName, workingCopy, originalPath);
    }

    /**
     * Keeps the key FixManager made from the uploaded path.
     *
     * @param fixKey the fix key
     */
    void setFixKey(String fixKey) {
        this.fixKey = fixKey;
    }

    /**
     * Gives the path a file or folder had before this fix, so that a later
     * fix can be named by the path the student uploaded.
     *
     * @param pathAfter a path in the working copy after this fix
     * @return the same file or folder's path before this fix
     */
    public Path findPathBefore(Path pathAfter) {
        if (!pathAfter.startsWith(fixedPath)) {
            return pathAfter;
        }
        Path insideFixed = fixedPath.relativize(pathAfter);
        return originalPath.resolve(insideFixed);
    }
}
