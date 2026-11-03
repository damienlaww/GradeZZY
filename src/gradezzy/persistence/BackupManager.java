/*
 * File: BackupManager.java
 *
 * Purpose: Copies the database file to a backup and restores it.
 *
 * Where it fits: Gives the instructor a way back to a previous grading state.
 */
package gradezzy.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.util.FileUtils;

/**
 * Copies the database file to a backup and restores it.
 *
 * <p>Close the repository before a restore, so the database file is not in
 * use while it is replaced.
 *
 * <p>Tier: COULD. Owner: Huxley.
 */
public class BackupManager {

    // Every backup file name starts with this
    private static final String BACKUP_PREFIX = "gradezzy-";

    private static final String BACKUP_EXTENSION = ".db";

    // Year, month, day, then hour, minute, second and millisecond, so
    // sorting the names sorts the backups by age
    private static final String TIMESTAMP_FORMAT =
            "%04d%02d%02d-%02d%02d%02d-%03d";

    private static final int NANOSECONDS_PER_MILLISECOND = 1000000;

    // The newest backup is never deleted, whatever the setting says
    private static final int MIN_BACKUPS_KEPT = 1;

    // Added to the database name while a restore copies it, so a
    // half-written file never replaces the database
    private static final String RESTORE_SUFFIX = ".restoring";

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
     * <p>The copy is named after the time, such as
     * gradezzy-20261022-154250-123.db. The oldest backups beyond
     * database.backup.keep.count are then deleted.
     *
     * @param databaseFile the database file
     * @return the path of the new backup
     * @throws PersistenceException if the operation fails
     */
    public Path backup(Path databaseFile) throws PersistenceException {
        if (databaseFile == null || !Files.isRegularFile(databaseFile)) {
            throw new PersistenceException("There is no database to back "
                    + "up yet at " + databaseFile + ". Grade a batch "
                    + "first.");
        }

        Path backupFolder = getBackupFolder();
        Path backupFile = backupFolder.resolve(BACKUP_PREFIX
                + timestamp(LocalDateTime.now()) + BACKUP_EXTENSION);
        try {
            Files.createDirectories(backupFolder);
            Files.copy(databaseFile, backupFile);
        } catch (IOException exception) {
            throw new PersistenceException("The backup " + backupFile
                    + " could not be written: " + exception.getMessage(),
                    exception);
        }

        deleteOldBackups();
        return backupFile;
    }

    /**
     * Replaces the database with a backup.
     *
     * <p>The backup is first copied next to the database and then moved
     * over it, so a failure halfway leaves the database as it was.
     *
     * @param backupFile the backup file
     * @param databaseFile the database file
     * @throws PersistenceException if the operation fails
     */
    public void restore(Path backupFile,
            Path databaseFile) throws PersistenceException {
        if (backupFile == null || !Files.isRegularFile(backupFile,
                LinkOption.NOFOLLOW_LINKS)) {
            throw new PersistenceException("The backup " + backupFile
                    + " does not exist. Choose one from the list.");
        }
        if (databaseFile == null) {
            throw new PersistenceException("No database file is set. "
                    + "Check database.file in the settings.");
        }

        Path temporaryFile = Paths.get(databaseFile.toString()
                + RESTORE_SUFFIX);
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(backupFile, temporaryFile,
                    StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporaryFile, databaseFile,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            deleteQuietly(temporaryFile);
            throw new PersistenceException("The backup " + backupFile
                    + " could not be restored: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Lists the available backups, newest first.
     *
     * @return the backup files; empty when there are none
     */
    public List<Path> listBackups() {
        List<Path> backups = new ArrayList<>();
        Path backupFolder = getBackupFolder();
        if (!Files.isDirectory(backupFolder)) {
            return backups;
        }

        try {
            for (Path file : FileUtils.listFiles(backupFolder,
                    BACKUP_EXTENSION)) {
                if (file.getFileName().toString()
                        .startsWith(BACKUP_PREFIX)) {
                    backups.add(file);
                }
            }
        } catch (IOException exception) {
            // A folder that cannot be read has no backups to offer
            return new ArrayList<>();
        }

        // The names hold the time, so name order is age order
        Collections.sort(backups);
        Collections.reverse(backups);
        return backups;
    }

    // Deletes the oldest backups beyond the number to keep. At least one
    // backup, the newest, is always kept.
    private void deleteOldBackups() {
        int keepCount = Math.max(MIN_BACKUPS_KEPT,
                config.getInt(ConfigKeys.BACKUP_KEEP_COUNT));
        List<Path> backups = listBackups();
        for (int index = keepCount; index < backups.size(); index++) {
            deleteQuietly(backups.get(index));
        }
    }

    // A file that cannot be deleted is tried again after the next backup
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            // Nothing else to do; see the comment above
        }
    }

    // The folder that holds the backups, from the settings
    private Path getBackupFolder() {
        return Paths.get(config.getString(ConfigKeys.BACKUP_DIR));
    }

    // Writes a time such as 20261022-154250-123 for a file name
    private String timestamp(LocalDateTime time) {
        return String.format(TIMESTAMP_FORMAT, time.getYear(),
                time.getMonthValue(), time.getDayOfMonth(), time.getHour(),
                time.getMinute(), time.getSecond(),
                time.getNano() / NANOSECONDS_PER_MILLISECOND);
    }
}
