/*
 * File: HistoryService.java
 *
 * Purpose: Lists earlier runs and handles backup and recovery.
 *
 * Where it fits: Used by the history screen.
 */
package gradezzy.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.GradingRun;
import gradezzy.persistence.BackupManager;
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
    }

    /**
     * Lists the saved runs.
     *
     * <p>Tier: SHOULD.
     *
     * @return the saved runs, newest first
     */
    public List<GradingRun> listRuns() {
        // TODO(Jing Yuan): implement: reading the runs from the repository
        return new ArrayList<>();
    }

    /**
     * Makes a backup of the current state.
     *
     * @return the backup file, or null if it failed
     */
    public Path backupNow() {
        // TODO(Jing Yuan): implement: calling backupManager.backup on
        //     DATABASE_FILE
        return null;
    }

    /**
     * Goes back to a previous state.
     *
     * @param backupFile the backup file
     */
    public void restore(Path backupFile) {
        // TODO(Jing Yuan): implement: closing the repository, restoring the
        //     file and opening the repository again
    }
}
