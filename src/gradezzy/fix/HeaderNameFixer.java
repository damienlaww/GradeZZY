/*
 * File: HeaderNameFixer.java
 *
 * Purpose: Writes the student's name and email into a blank file header.
 *
 * Where it fits: Handles the missing name case, using the roster entry matched
 * from the zip file name.
 */
package gradezzy.fix;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.util.TextUtils;

/**
 * Writes the student's name and email into a blank file header.
 */
public class HeaderNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fill in header";

    // Ends one line of the file
    private static final char LINE_FEED = '\n';

    // Comes before the line feed in a Windows line ending
    private static final String CARRIAGE_RETURN = "\r";

    // Goes between a label and the value written after it
    private static final String SPACE = " ";

    // Returned when a header line with a label is not found
    private static final int NOT_FOUND = -1;

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new header name fixer.
     *
     * @param config the application settings
     */
    public HeaderNameFixer(AppConfig config) {
        super(NAME);
        this.config = config;
    }

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.MISSING_HEADER_NAME;
    }

    /**
     * Backs up the file, then fills its blank header from the roster.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Student student = submission.getStudent();
        if (student == null) {
            throw new FixException(submission.getUsername() + " is not on "
                    + "the roster, so the header was left for you to fill "
                    + "in.");
        }

        String questionId = findQuestionId(config, anomaly);
        if (questionId == null) {
            throw new FixException("Could not tell which file this header "
                    + "problem is about, so nothing was changed.");
        }

        String folderName = readFolderName(config, questionId);
        String fileName = readFileName(config, questionId);
        String relativePath = folderName + FOLDER_SEPARATOR + fileName;
        Path file = findFile(submission.getWorkingCopyPath(),
                submission.getUsername(), folderName, fileName);
        String name = student.getFullName();
        String email = student.getEmail();

        String nameLabel = config.getString(ConfigKeys.HEADER_NAME_LABEL);
        String emailLabel = config.getString(ConfigKeys.HEADER_EMAIL_LABEL);
        byte[] originalBytes = readBytes(file);
        Charset charset = chooseCharset(originalBytes);
        String original = new String(originalBytes, charset);
        String withName = fillValue(original, nameLabel, name);
        String filled = fillValue(withName, emailLabel, email);
        if (filled.equals(original) || !hasValue(filled, nameLabel)
                || !hasValue(filled, emailLabel)) {
            throw new FixException("The header of " + relativePath
                    + " could not be completed from the roster, so it was "
                    + "left for you to fill in.");
        }

        String written = name + " and " + email;
        if (withName.equals(original)) {
            written = email;
        } else if (filled.equals(withName)) {
            written = name;
        }

        Path backupPath = backup(submission, file);
        writeFile(file, filled, charset);

        return new FixRecord(anomaly, NAME, "Filled in the header of "
                + relativePath + " with " + written + ".",
                submission.getWorkingCopyPath(), file, file, backupPath);
    }

    /**
     * Restores the file from its backup, wherever the file is now.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        Path backupPath = fixRecord.getBackupPath();
        Path fixedPath = fixRecord.getFixedPath();
        Path file = findFile(fixRecord.getWorkingCopy(),
                fixRecord.getAnomaly().getUsername(),
                fixedPath.getParent().getFileName().toString(),
                fixedPath.getFileName().toString());

        try {
            Files.copy(backupPath, file,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new FixException("Could not restore " + file.getFileName()
                    + " from its backup: " + exception.getMessage(),
                    exception);
        }
        discardBackup(backupPath);
    }

    // Finds a required file in whichever layout the working copy now has.
    private Path findFile(Path workingCopy, String username,
            String folderName, String fileName) throws FixException {
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        Path folder = findQuestionFolderNow(workingCopy, username,
                readFolderNames(config), folderName);
        Path file = null;
        if (folder != null) {
            file = findByName(listFiles(folder), fileName);
        }
        if (file == null) {
            throw new FixException("Could not find " + folderName
                    + FOLDER_SEPARATOR + fileName + " in the working copy.");
        }
        return file;
    }

    // Writes the value after the label if nothing follows it yet.
    private String fillValue(String text, String label, String value) {
        int valueStart = findValueStart(text, label);
        if (valueStart < 0 || TextUtils.isBlank(value)) {
            return text;
        }

        int lineEnd = findLineEnd(text, valueStart);
        String rest = text.substring(valueStart, lineEnd);
        if (!TextUtils.isBlank(rest)) {
            return text;
        }

        String ending = "";
        if (rest.endsWith(CARRIAGE_RETURN)) {
            ending = CARRIAGE_RETURN;
        }
        return text.substring(0, valueStart) + SPACE + value.trim() + ending
                + text.substring(lineEnd);
    }

    // Says whether something follows the label in the header.
    private boolean hasValue(String text, String label) {
        int valueStart = findValueStart(text, label);
        if (valueStart < 0) {
            return false;
        }
        return !TextUtils.isBlank(text.substring(valueStart,
                findLineEnd(text, valueStart)));
    }

    // Returns where the value after the label starts, or -1 if none.
    private int findValueStart(String text, String label) {
        int scanLines = config.getInt(ConfigKeys.HEADER_SCAN_LINES);
        int lineStart = 0;
        for (int lineNumber = 0; lineNumber < scanLines
                && lineStart < text.length(); lineNumber++) {
            int lineEnd = findLineEnd(text, lineStart);
            int labelPosition = text.substring(lineStart, lineEnd)
                    .indexOf(label);
            if (labelPosition >= 0) {
                return lineStart + labelPosition + label.length();
            }
            lineStart = lineEnd + 1;
        }
        return NOT_FOUND;
    }

    // Returns where the line holding a position ends.
    private int findLineEnd(String text, int position) {
        int lineEnd = text.indexOf(LINE_FEED, position);
        if (lineEnd < 0) {
            return text.length();
        }
        return lineEnd;
    }

    // Picks UTF-8 if the bytes are valid UTF-8, else byte-safe ISO-8859-1.
    private Charset chooseCharset(byte[] bytes) {
        String asUtf8 = new String(bytes, StandardCharsets.UTF_8);
        byte[] backAgain = asUtf8.getBytes(StandardCharsets.UTF_8);
        if (Arrays.equals(backAgain, bytes)) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.ISO_8859_1;
    }

    // Reads a source file, turning a failure into a FixException.
    private byte[] readBytes(Path file) throws FixException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new FixException("Could not read " + file.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }

    // Writes a source file in the character set it was read with.
    private void writeFile(Path file, String text,
            Charset charset) throws FixException {
        try {
            Files.write(file, text.getBytes(charset));
        } catch (IOException exception) {
            throw new FixException("Could not write " + file.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }
}
