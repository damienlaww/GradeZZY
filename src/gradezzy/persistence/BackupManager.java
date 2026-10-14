/*
 * File: BackupManager.java
 *
 * Purpose: Copies the database file to a backup and restores it.
 *
 * Where it fits: Gives the instructor a way back to a previous grading state.
 */
package gradezzy.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;

/**
 * Copies the database file to a backup and restores it.
 *
 * <p>Tier: COULD. Owner: Huxley.
 */
public class BackupManager {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new backup manager.
     *
     * @param config the application settings
     */
    public BackupManager(AppConfig config) {
        this.config = config;
    }

    /**
     * Copies the database into the backup folder.
     *
     * @param databaseFile the database file
     * @return the path of the new backup
     * @throws PersistenceException if the operation fails
     */
    public Path backup(Path databaseFile) throws PersistenceException {
        // TODO(Huxley): implement: a timestamped copy in BACKUP_DIR, then
        //     removing the oldest copies beyond BACKUP_KEEP_COUNT
        return null;
    }

    /**
     * Replaces the database with a backup.
     *
     * @param backupFile the backup file
     * @param databaseFile the database file
     * @throws PersistenceException if the operation fails
     */
    public void restore(Path backupFile,
            Path databaseFile) throws PersistenceException {
        // TODO(Huxley): implement: copying the backup over the database file
    }

    /**
     * Lists the available backups, newest first.
     *
     * @return the backup files
     */
    public List<Path> listBackups() {
        // TODO(Huxley): implement: listing the files in BACKUP_DIR
        return new ArrayList<>();
    }
}
