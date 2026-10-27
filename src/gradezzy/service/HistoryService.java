/*
 * File: HistoryService.java
 *
 * Purpose: Lists earlier runs and handles backup and recovery.
 *
 * Where it fits: Used by the history screen.
 */
package gradezzy.service;

import java.nio.file.Path;
import java.nio.file.Paths;
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

    // The application settings
    private final AppConfig config;

    // The store results are saved to
    private final ResultRepository repository;

    // The helper that copies the database
    private final BackupManager backupManager;

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
        this.config = config;
        this.repository = repository;
        this.backupManager = backupManager;
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
                return backupManager.backup(databaseFile);
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
     * <p>Other threads that use the repository wait until the restore is
     * finished. A grading run still going on saves its next results into
     * the restored database, so restore when no run is being graded.
     *
     * @param backupFile the backup file
     * @throws IllegalStateException if the database is switched off, or
     *         the backup could not be restored or the database not opened
     *         again; the message says which
     */
    public void restore(Path backupFile) {
        if (!isDatabaseUsed()) {
            throw new IllegalStateException("The database is switched "
                    + "off, so there is nothing to restore into. Set "
                    + ConfigKeys.DATABASE_ENABLED + " to true.");
        }

        String problem = "";

        // Every repository method locks the repository, so holding the
        // same lock here stops other threads from using it while closed
        synchronized (repository) {
            repository.close();

            try {
                backupManager.restore(backupFile, databaseFile);
            } catch (PersistenceException exception) {
                problem = exception.getMessage();
            }

            try {
                repository.open();
            } catch (PersistenceException exception) {
                problem = problem + " The database could not be opened "
                        + "again: " + exception.getMessage();
            }
        }

        if (!problem.isEmpty()) {
            throw new IllegalStateException(problem.trim());
        }
    }

    // Says whether runs are saved to the database at all.
    private boolean isDatabaseUsed() {
        return repository != null
                && config.getBoolean(ConfigKeys.DATABASE_ENABLED);
    }
}
