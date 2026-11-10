/*
 * File: BackupInfo.java
 *
 * Purpose: One kept backup file: when it was made, how big it is and the
 * saved runs it holds, or why it could not be read.
 *
 * Where it fits: HistoryService lists one per backup file, and the
 * Backups view on History shows them, newest first.
 */
package gradezzy.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A kept backup file and what it holds.
 *
 * <p>Tier: COULD.
 */
public class BackupInfo {

    /**
     * The end of the name of a copy made just before a restore, such as
     * gradezzy-20261022-154250-123-before-restore.db. These copies are
     * kept apart from the backups made with Back up now.
     */
    public static final String BEFORE_RESTORE_ENDING = "-before-restore.db";

    // The backup file
    private final Path file;

    // When the backup was made, or null when not known
    private final LocalDateTime madeAt;

    // The size of the file in bytes
    private final long sizeBytes;

    // The saved runs in the file, newest first
    private final List<BackupRun> runs = new ArrayList<>();

    // Why the file could not be read, or null when it was read
    private final String problem;

    /**
     * Creates a backup that was read.
     *
     * <p>Tier: COULD.
     *
     * @param file the backup file
     * @param madeAt when it was made, or null
     * @param sizeBytes the size of the file in bytes
     * @param runs the saved runs in it, newest first
     */
    public BackupInfo(Path file, LocalDateTime madeAt, long sizeBytes,
            List<BackupRun> runs) {
        this.file = file;
        this.madeAt = madeAt;
        this.sizeBytes = sizeBytes;
        if (runs != null) {
            this.runs.addAll(runs);
        }
        this.problem = null;
    }

    /**
     * Creates a backup that could not be read.
     *
     * <p>Tier: COULD.
     *
     * @param file the backup file
     * @param madeAt when it was made, or null
     * @param sizeBytes the size of the file in bytes
     * @param problem why it could not be read
     */
    public BackupInfo(Path file, LocalDateTime madeAt, long sizeBytes,
            String problem) {
        this.file = file;
        this.madeAt = madeAt;
        this.sizeBytes = sizeBytes;
        this.problem = problem;
    }

    /**
     * Returns the backup file.
     *
     * <p>Tier: COULD.
     *
     * @return the file
     */
    public Path getFile() {
        return file;
    }

    /**
     * Returns when the backup was made.
     *
     * <p>Tier: COULD.
     *
     * @return the time, or null when not known
     */
    public LocalDateTime getMadeAt() {
        return madeAt;
    }

    /**
     * Returns the size of the file.
     *
     * <p>Tier: COULD.
     *
     * @return the size in bytes
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Returns the saved runs in the file.
     *
     * <p>Tier: COULD.
     *
     * @return a copy of the runs, newest first; empty when unreadable
     */
    public List<BackupRun> getRuns() {
        return new ArrayList<>(runs);
    }

    /**
     * Says whether the file could be read.
     *
     * <p>Tier: COULD.
     *
     * @return true when the runs were read
     */
    public boolean isReadable() {
        return problem == null;
    }

    /**
     * Returns why the file could not be read.
     *
     * <p>Tier: COULD.
     *
     * @return the reason, or null when it was read
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Says whether this is the copy of the saved results made just
     * before a restore, rather than a backup made with Back up now.
     *
     * <p>Tier: COULD.
     *
     * @return true for a copy made before a restore
     */
    public boolean isBeforeRestore() {
        return isBeforeRestore(file);
    }

    /**
     * Says whether a file is a copy of the saved results made just before
     * a restore, from its name.
     *
     * <p>Tier: COULD.
     *
     * @param file the file
     * @return true for a copy made before a restore
     */
    public static boolean isBeforeRestore(Path file) {
        return file != null && file.getFileName() != null
                && file.getFileName().toString().endsWith(
                BEFORE_RESTORE_ENDING);
    }
}
