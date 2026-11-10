/*
 * File: HistoryService.java
 *
 * Purpose: Lists earlier runs and handles backup and recovery.
 *
 * Where it fits: Used by the Runs tab.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.BackupInfo;
import gradezzy.model.GradingRun;
import gradezzy.persistence.BackupManager;
import gradezzy.persistence.BackupReader;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;

/**
 * Lists earlier runs and handles backup and recovery.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class HistoryService {

    // Added to the name of the copy of a backup taken before a restore.
    // It does not end in .db, so it is never listed as a backup and never
    // deleted when old backups are cleared.
    private static final String HELD_COPY_SUFFIX = ".held";

    // The audit log actions this service records
    private static final String BACKUP_MADE = "Backup made";
    private static final String BACKUP_RESTORED = "Backup restored";

    // The application settings
    private final AppConfig config;

    // The store results are saved to
    private final ResultRepository repository;

    // The helper that copies the database
    private final BackupManager backupManager;

    // The log of instructor actions
    private final AuditService auditService;

    // The database file the repository was opened with. Read once, like
    // the repository does, so a later change on the settings screen
    // cannot make a restore overwrite another file.
    private final Path databaseFile;

    /**
     * Creates a new history service that writes backups and restores to
     * the given audit log.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     * @param repository the store results are saved to
     * @param backupManager the helper that copies the database
     * @param auditService the log of instructor actions
     */
    public HistoryService(AppConfig config, ResultRepository repository,
            BackupManager backupManager, AuditService auditService) {
        this.config = config;
        this.repository = repository;
        this.backupManager = backupManager;
        this.auditService = auditService;
        this.databaseFile = Paths.get(config.getString(
                ConfigKeys.DATABASE_FILE));
    }

    /**
     * Lists the saved runs.
     *
     * <p>Tier: SHOULD.
     *
     * @return the saved runs, newest first, or an empty list when the
     *         database is switched off or cannot be read
     */
    public List<GradingRun> listRuns() {
        if (!isDatabaseUsed()) {
            return new ArrayList<>();
        }

        try {
            return repository.listRuns();
        } catch (PersistenceException exception) {
            return new ArrayList<>();
        }
    }

    /**
     * Lists the kept backups, newest first, with the saved runs each one
     * holds. Each file is only read, never changed; one that cannot be
     * read is listed with the reason.
     *
     * <p>Tier: COULD.
     *
     * @return the backups; empty when there are none
     */
    public List<BackupInfo> listBackups() {
        List<BackupInfo> backups = new ArrayList<>();
        for (Path backupFile : backupManager.listBackups()) {
            backups.add(BackupReader.read(backupFile));
        }
        return backups;
    }

    /**
     * Returns how many backups are kept before the oldest is deleted.
     *
     * <p>Tier: COULD.
     *
     * @return the number kept, at least one
     */
    public int getBackupKeepCount() {
        return backupManager.getKeepCount();
    }

    /**
     * Makes a backup of the current state.
     *
     * <p>Tier: COULD.
     *
     * @return the backup file, or null if it failed or the database is
     *         switched off
     */
    public Path backupNow() {
        if (!isDatabaseUsed()) {
            return null;
        }

        // Held so no run is half written while the file is copied
        synchronized (repository) {
            try {
                Path backupFile = backupManager.backup(databaseFile);
                auditService.record(BACKUP_MADE, "Saved a copy of the "
                        + "database as " + backupFile.getFileName() + ".");
                return backupFile;
            } catch (PersistenceException exception) {
                return null;
            }
        }
    }

    /**
     * Says whether a file can be restored: it must be a GradeZZY
     * database that can be read. The file is only read, never changed.
     *
     * <p>Tier: COULD.
     *
     * @param backupFile the file to check
     * @return why it cannot be restored, or "" when it can
     */
    public String checkBackup(Path backupFile) {
        if (backupFile == null || !Files.isRegularFile(backupFile)) {
            return "The backup " + backupFile + " does not exist. Choose "
                    + "one from the list.";
        }
        if (!BackupReader.read(backupFile).isReadable()) {
            return BackupManager.notABackup(backupFile);
        }
        return "";
    }

    /**
     * Goes back to a previous state: closes the database, copies the
     * backup over it and opens it again. The database is opened again
     * even when the copy fails, so saving keeps working.
     *
     * <p>A file that is not a GradeZZY database is refused before
     * anything is changed. The database is backed up first, as a copy
     * made before a restore that does not count towards the backups
     * kept, so a restore can itself be undone by restoring that copy. If
     * that copy cannot be made, nothing is restored. The chosen backup is
     * copied aside before, so clearing old copies cannot delete it on the
     * way. If the restored database cannot be opened, the copy is put
     * back by itself.
     *
     * <p>Other threads that use the repository wait until the restore is
     * finished. A grading run still going on saves its next results into
     * the restored database, so restore when no run is being graded.
     *
     * <p>Tier: COULD.
     *
     * @param backupFile the backup file
     * @return the backup of the database made just before the restore,
     *         which puts it back; null when there was no database yet
     * @throws IllegalStateException if the database is switched off, the
     *         file is not a GradeZZY backup, the current database could
     *         not be backed up first, or the backup could not be restored
     *         or the database not opened again; the message says which
     */
    public Path restore(Path backupFile) {
        if (!isDatabaseUsed()) {
            throw new IllegalStateException("The database is switched "
                    + "off, so there is nothing to restore into. Set "
                    + ConfigKeys.DATABASE_ENABLED + " to true.");
        }

        if (backupFile == null || !Files.isRegularFile(backupFile)) {
            throw new IllegalStateException("The backup " + backupFile
                    + " does not exist. Choose one from the list.");
        }

        String problem = "";
        Path safetyBackup = null;
        String heldName = backupFile.getFileName() + HELD_COPY_SUFFIX;
        Path heldCopy = backupFile.resolveSibling(heldName);

        // Every repository method locks the repository, so holding the
        // same lock here stops other threads from using it while closed
        synchronized (repository) {
            try {
                Files.copy(backupFile, heldCopy,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                deleteQuietly(heldCopy);
                throw new IllegalStateException("The backup " + backupFile
                        + " could not be read, so nothing was restored: "
                        + exception.getMessage());
            }
            // Checked before anything is changed, so a wrong file never
            // makes a copy before a restore or touches the database
            if (!BackupReader.read(heldCopy).isReadable()) {
                deleteQuietly(heldCopy);
                throw new IllegalStateException(
                        BackupManager.notABackup(backupFile));
            }
            try {
                safetyBackup = backUpBeforeRestore();
            } catch (PersistenceException exception) {
                deleteQuietly(heldCopy);
                throw new IllegalStateException("The current database "
                        + "could not be backed up first, so nothing was "
                        + "restored: " + exception.getMessage());
            }

            problem = replaceDatabase(heldCopy, safetyBackup);

            if (problem.isEmpty()) {
                safetyBackup = dropIfUnreadable(safetyBackup);
                backupManager.deleteOldBeforeRestoreCopies(backupFile);
                auditService.record(BACKUP_RESTORED, describeRestore(
                        backupFile, safetyBackup));
            }
        }

        if (!problem.isEmpty()) {
            throw new IllegalStateException(problem.trim());
        }
        return safetyBackup;
    }

    // Closes the database, copies the held copy of the backup over it,
    // deletes the held copy and opens the database again, even when the
    // copy failed. If the restored database cannot be opened, the copy
    // made before the restore is put back. Returns what went wrong, or ""
    // when nothing did. The caller holds the repository's lock.
    private String replaceDatabase(Path heldCopy, Path safetyBackup) {
        String problem = "";
        boolean replaced = false;
        repository.close();

        try {
            backupManager.restore(heldCopy, databaseFile);
            replaced = true;
        } catch (PersistenceException exception) {
            problem = exception.getMessage();
        }
        deleteQuietly(heldCopy);

        try {
            repository.open();
        } catch (PersistenceException exception) {
            problem = problem + " The database could not be opened "
                    + "again: " + exception.getMessage();
            if (replaced) {
                problem = putBack(safetyBackup, problem);
            }
        }
        return problem;
    }

    // Puts back the copy made before a restore that left a database that
    // cannot be opened, and opens it again. Returns what went wrong, with
    // whether the saved results were put back. The caller holds the
    // repository's lock.
    private String putBack(Path safetyBackup, String problem) {
        if (safetyBackup == null) {
            return problem;
        }
        repository.close();
        try {
            backupManager.restore(safetyBackup, databaseFile);
            repository.open();
        } catch (PersistenceException exception) {
            return problem + " The results saved before could not be put "
                    + "back either (" + exception.getMessage() + "). "
                    + "Restore " + safetyBackup.getFileName() + " from "
                    + "Kept backups.";
        }
        // The database is the copy again, so the copy is not needed
        deleteQuietly(safetyBackup);
        return problem + " The results saved before were put back, so "
                + "nothing was changed.";
    }

    // Backs up the database before a restore. Returns null when there is
    // no database file yet, as then there is nothing to lose.
    private Path backUpBeforeRestore() throws PersistenceException {
        if (!Files.isRegularFile(databaseFile)) {
            return null;
        }
        return backupManager.backupBeforeRestore(databaseFile);
    }

    // Deletes the copy made before a restore when it cannot be read, as it
    // could never be restored. Returns the copy, or null when deleted.
    private Path dropIfUnreadable(Path safetyBackup) {
        if (safetyBackup == null) {
            return null;
        }
        if (BackupReader.read(safetyBackup).isReadable()) {
            return safetyBackup;
        }
        deleteQuietly(safetyBackup);
        return null;
    }

    // Says which backup was restored and where the replaced database went.
    private String describeRestore(Path backupFile, Path safetyBackup) {
        String detail = "Restored " + backupFile.getFileName() + ".";
        if (safetyBackup != null) {
            detail += " The database before the restore was saved as "
                    + safetyBackup.getFileName() + ".";
        }
        return detail;
    }

    // Deletes a file if it is there; a leftover copy is harmless.
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            // Nothing else to do; see the comment above
        }
    }

    // Says whether runs are saved to the database at all.
    private boolean isDatabaseUsed() {
        return repository != null
                && config.getBoolean(ConfigKeys.DATABASE_ENABLED);
    }
}
