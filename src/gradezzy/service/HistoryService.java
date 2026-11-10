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
import gradezzy.model.GradingRun;
import gradezzy.persistence.BackupManager;
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
     * Creates a new history service.
     *
     * @param config the application settings
     * @param repository the store results are saved to
     * @param backupManager the helper that copies the database
     */
    public HistoryService(AppConfig config, ResultRepository repository,
            BackupManager backupManager) {
        this(config, repository, backupManager,
                new AuditService(config, repository));
    }

    /**
     * Creates a new history service that writes backups and restores to
     * the given audit log.
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
     * Makes a backup of the current state.
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
     * Goes back to a previous state: closes the database, copies the
     * backup over it and opens it again. The database is opened again
     * even when the copy fails, so saving keeps working.
     *
     * <p>The database is backed up first, so a restore can itself be
     * undone by restoring that new backup. If that backup cannot be made,
     * nothing is restored. The chosen backup is copied aside before, so
     * clearing old backups cannot delete it on the way.
     *
     * <p>Other threads that use the repository wait until the restore is
     * finished. A grading run still going on saves its next results into
     * the restored database, so restore when no run is being graded.
     *
     * @param backupFile the backup file
     * @throws IllegalStateException if the database is switched off, the
     *         current database could not be backed up first, or the backup
     *         could not be restored or the database not opened again; the
     *         message says which
     */
    public void restore(Path backupFile) {
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
                safetyBackup = backUpBeforeRestore();
            } catch (IOException exception) {
                deleteQuietly(heldCopy);
                throw new IllegalStateException("The backup " + backupFile
                        + " could not be read, so nothing was restored: "
                        + exception.getMessage());
            } catch (PersistenceException exception) {
                deleteQuietly(heldCopy);
                throw new IllegalStateException("The current database "
                        + "could not be backed up first, so nothing was "
                        + "restored: " + exception.getMessage());
            }

            problem = replaceDatabase(heldCopy);

            if (problem.isEmpty()) {
                auditService.record(BACKUP_RESTORED, describeRestore(
                        backupFile, safetyBackup));
            }
        }

        if (!problem.isEmpty()) {
            throw new IllegalStateException(problem.trim());
        }
    }

    // Closes the database, copies the held copy of the backup over it,
    // deletes the held copy and opens the database again, even when the
    // copy failed. Returns what went wrong, or "" when nothing did.
    // The caller holds the repository's lock.
    private String replaceDatabase(Path heldCopy) {
        String problem = "";
        repository.close();

        try {
            backupManager.restore(heldCopy, databaseFile);
        } catch (PersistenceException exception) {
            problem = exception.getMessage();
        }
        deleteQuietly(heldCopy);

        try {
            repository.open();
        } catch (PersistenceException exception) {
            problem = problem + " The database could not be opened "
                    + "again: " + exception.getMessage();
        }
        return problem;
    }

    // Backs up the database before a restore. Returns null when there is
    // no database file yet, as then there is nothing to lose.
    private Path backUpBeforeRestore() throws PersistenceException {
        if (!Files.isRegularFile(databaseFile)) {
            return null;
        }
        return backupManager.backup(databaseFile);
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
