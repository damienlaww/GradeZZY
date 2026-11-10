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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.BackupInfo;
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

    // Every backup file name ends with this
    private static final String BACKUP_EXTENSION = ".db";

    // Year, month, day, then hour, minute, second and millisecond, so
    // sorting the names sorts the backups by age
    private static final String TIMESTAMP_FORMAT =
            "%04d%02d%02d-%02d%02d%02d-%03d";

    // Reads the time back from a backup name, the same pattern as
    // TIMESTAMP_FORMAT
    private static final DateTimeFormatter NAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    // Turns the nanoseconds of a time into milliseconds and back
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
     * database.backup.keep.count are then deleted. Copies made before a
     * restore are not counted and not deleted here.
     *
     * @param databaseFile the database file
     * @return the path of the new backup
     * @throws PersistenceException if the operation fails
     */
    public Path backup(Path databaseFile) throws PersistenceException {
        Path backupFile = copyToNewFile(databaseFile, BACKUP_EXTENSION);
        deleteOldBackups();
        return backupFile;
    }

    /**
     * Copies the database into the backup folder just before a restore,
     * so the restore can be undone.
     *
     * <p>The copy is named like gradezzy-20261022-154250-123-before-
     * restore.db. It does not count towards database.backup.keep.count,
     * so it never pushes out a backup made with Back up now, and nothing
     * is deleted here; see deleteOldBeforeRestoreCopies.
     *
     * @param databaseFile the database file
     * @return the path of the new copy
     * @throws PersistenceException if the operation fails
     */
    public Path backupBeforeRestore(Path databaseFile)
            throws PersistenceException {
        return copyToNewFile(databaseFile, BackupInfo.BEFORE_RESTORE_ENDING);
    }

    /**
     * Deletes the oldest copies made before a restore beyond
     * database.backup.keep.count. Backups made with Back up now are never
     * deleted here, nor is the file just restored.
     *
     * @param inUse the file just restored, which is kept; may be null
     */
    public void deleteOldBeforeRestoreCopies(Path inUse) {
        int keepCount = getKeepCount();
        List<Path> copies = new ArrayList<>();
        for (Path file : listBackups()) {
            boolean restored = isSameFile(file, inUse);
            if (BackupInfo.isBeforeRestore(file) && !restored) {
                copies.add(file);
            }
        }
        for (int index = keepCount; index < copies.size(); index++) {
            deleteQuietly(copies.get(index));
        }
    }

    // Copies the database to a new file in the backup folder, named after
    // the time and ending in the given ending
    private Path copyToNewFile(Path databaseFile,
            String ending) throws PersistenceException {
        boolean noDatabase = databaseFile == null
                || !Files.isRegularFile(databaseFile);
        if (noDatabase) {
            throw new PersistenceException("There is no database to back "
                    + "up yet at " + databaseFile + ". Grade a batch "
                    + "first.");
        }

        Path backupFolder = getBackupFolder();
        LocalDateTime time = LocalDateTime.now();
        Path backupFile = backupFileAt(backupFolder, time, ending);
        // Two backups in one millisecond would share a name, so the later
        // one is named a millisecond on; the names still sort by age
        while (Files.exists(backupFile)) {
            time = time.plusNanos(NANOSECONDS_PER_MILLISECOND);
            backupFile = backupFileAt(backupFolder, time, ending);
        }
        try {
            Files.createDirectories(backupFolder);
            Files.copy(databaseFile, backupFile);
        } catch (IOException exception) {
            throw new PersistenceException("The backup " + backupFile
                    + " could not be written: " + exception.getMessage(),
                    exception);
        }
        return backupFile;
    }

    /**
     * Replaces the database with a backup.
     *
     * <p>The backup is first copied next to the database and checked
     * there: a file that is not a GradeZZY database is refused and the
     * database is not touched. Only then is the copy moved over the
     * database, so a failure halfway leaves the database as it was.
     *
     * @param backupFile the backup file
     * @param databaseFile the database file
     * @throws PersistenceException if the operation fails
     */
    public void restore(Path backupFile,
            Path databaseFile) throws PersistenceException {
        boolean noBackup = backupFile == null
                || !Files.isRegularFile(backupFile, LinkOption.NOFOLLOW_LINKS);
        if (noBackup) {
            throw new PersistenceException("The backup " + backupFile
                    + " does not exist. Choose one from the list.");
        }
        if (databaseFile == null) {
            throw new PersistenceException("No database file is set. "
                    + "Check database.file in the settings.");
        }

        String temporaryName = databaseFile.toString() + RESTORE_SUFFIX;
        Path temporaryFile = Paths.get(temporaryName);
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(backupFile, temporaryFile,
                    StandardCopyOption.REPLACE_EXISTING);
            BackupInfo copy = BackupReader.read(temporaryFile);
            if (!copy.isReadable()) {
                deleteQuietly(temporaryFile);
                throw new PersistenceException(notABackup(backupFile));
            }
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
     * Says why a file cannot be restored, in plain words.
     *
     * @param backupFile the file
     * @return the reason, which says nothing was changed
     */
    public static String notABackup(Path backupFile) {
        Path name = backupFile;
        if (backupFile != null && backupFile.getFileName() != null) {
            name = backupFile.getFileName();
        }
        return name + " is not a GradeZZY backup or it is damaged, so "
                + "nothing was restored and the saved results were not "
                + "changed. Choose a file made by Back up now.";
    }

    /**
     * Lists the available backups, newest first, with the copies made
     * before a restore among them.
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
                String fileName = file.getFileName().toString();
                if (fileName.startsWith(BACKUP_PREFIX)) {
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

    /**
     * Returns the number of backups kept, from the settings. At least
     * one, the newest, is always kept.
     *
     * @return how many backups are kept
     */
    public int getKeepCount() {
        int setting = config.getInt(ConfigKeys.BACKUP_KEEP_COUNT);
        return Math.max(MIN_BACKUPS_KEPT, setting);
    }

    /**
     * Reads when a backup was made from its name, such as
     * gradezzy-20261022-154250-123.db or
     * gradezzy-20261022-154250-123-before-restore.db.
     *
     * @param backupFile the backup file
     * @return the time, or null when the name holds no time
     */
    public static LocalDateTime timeOf(Path backupFile) {
        if (backupFile == null || backupFile.getFileName() == null) {
            return null;
        }
        String fileName = backupFile.getFileName().toString();
        boolean backupName = fileName.startsWith(BACKUP_PREFIX)
                && fileName.endsWith(BACKUP_EXTENSION);
        if (!backupName) {
            return null;
        }
        String ending = BACKUP_EXTENSION;
        if (BackupInfo.isBeforeRestore(backupFile)) {
            ending = BackupInfo.BEFORE_RESTORE_ENDING;
        }
        if (fileName.length() < BACKUP_PREFIX.length() + ending.length()) {
            return null;
        }
        String stamp = fileName.substring(BACKUP_PREFIX.length(),
                fileName.length() - ending.length());
        try {
            return LocalDateTime.parse(stamp, NAME_TIME);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    // Deletes the oldest backups beyond the number to keep. At least one
    // backup, the newest, is always kept. Copies made before a restore
    // are not counted and not deleted.
    private void deleteOldBackups() {
        int keepCount = getKeepCount();
        List<Path> backups = new ArrayList<>();
        for (Path file : listBackups()) {
            if (!BackupInfo.isBeforeRestore(file)) {
                backups.add(file);
            }
        }
        for (int index = keepCount; index < backups.size(); index++) {
            deleteQuietly(backups.get(index));
        }
    }

    // Says whether two paths name the same file, either may be relative
    private boolean isSameFile(Path file, Path other) {
        if (file == null || other == null) {
            return false;
        }
        Path fullPath = file.toAbsolutePath().normalize();
        return fullPath.equals(other.toAbsolutePath().normalize());
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

    // The backup file in the folder for a time and an ending, such as
    // gradezzy-20261022-154250-123.db
    private Path backupFileAt(Path backupFolder, LocalDateTime time,
            String ending) {
        String fileName = BACKUP_PREFIX + timestamp(time) + ending;
        return backupFolder.resolve(fileName);
    }

    // Writes a time such as 20261022-154250-123 for a file name
    private String timestamp(LocalDateTime time) {
        int millisecond = time.getNano() / NANOSECONDS_PER_MILLISECOND;
        return String.format(TIMESTAMP_FORMAT, time.getYear(),
                time.getMonthValue(), time.getDayOfMonth(), time.getHour(),
                time.getMinute(), time.getSecond(), millisecond);
    }
}
