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
import gradezzy.validation.HeaderNameValidator;

/**
 * Writes the student's name and email into a blank file header.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
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

    // Shown in Before for a header line with nothing after its label
    private static final String BLANK_VALUE = "(blank)";

    // Goes between the name and email lines in Before and After
    private static final String VALUE_SEPARATOR = ", ";

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
     * Finds the file whose header this fix would fill in.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the file
     * @throws FixException if the student is not on the roster or the
     *         file cannot be found
     */
    @Override
    public Path findPathToChange(Submission submission,
            Anomaly anomaly) throws FixException {
        if (submission.getStudent() == null) {
            throw new FixException(submission.getUsername() + " is not on "
                    + "the roster, so the header was left for you to fill "
                    + "in.");
        }

        String questionId = findQuestionId(config, anomaly);
        if (questionId == null) {
            throw new FixException("Could not tell which file this header "
                    + "problem is about, so nothing was changed.");
        }
        return findFile(submission.getWorkingCopyPath(),
                submission.getUsername(), readFolderName(config, questionId),
                readFileName(config, questionId));
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
        Path file = findPathToChange(submission, anomaly);
        Student student = submission.getStudent();
        String questionId = findQuestionId(config, anomaly);
        String relativePath = readFolderName(config, questionId)
                + FOLDER_SEPARATOR + readFileName(config, questionId);
        String name = student.getFullName();
        String email = student.getEmail();

        String nameLabel = config.getString(ConfigKeys.HEADER_NAME_LABEL);
        String emailLabel = config.getString(ConfigKeys.HEADER_EMAIL_LABEL);
        byte[] originalBytes = readBytes(file);
        Charset charset = chooseCharset(originalBytes);
        String original = new String(originalBytes, charset);
        String withName = fillValue(original, nameLabel, name);
        String filled = fillValue(withName, emailLabel, email);
        boolean notCompleted = filled.equals(original)
                || !hasValue(filled, nameLabel)
                || !hasValue(filled, emailLabel);
        if (notCompleted) {
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
        String before = showChanged(original, original, withName, filled);
        String after = showChanged(filled, original, withName, filled);

        Path backupPath = backup(submission, file);
        writeFile(file, filled, charset);

        return new FixRecord(anomaly, NAME, "Filled in the header of "
                + relativePath + " with " + written + ".",
                submission.getWorkingCopyPath(), file, file, backupPath,
                before, after);
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
        String folderName = fixedPath.getParent().getFileName().toString();
        String fileName = fixedPath.getFileName().toString();
        Path file = findFile(fixRecord.getWorkingCopy(),
                fixRecord.getAnomaly().getUsername(), folderName, fileName);

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

    // Shows in one text the header lines the fix changed: the name line,
    // the email line or both, as they read in the given text.
    private String showChanged(String text, String original,
            String withName, String filled) {
        String nameLabel = config.getString(ConfigKeys.HEADER_NAME_LABEL);
        String emailLabel = config.getString(ConfigKeys.HEADER_EMAIL_LABEL);
        if (withName.equals(original)) {
            return showValue(text, emailLabel);
        } else if (filled.equals(withName)) {
            return showValue(text, nameLabel);
        }
        return showValue(text, nameLabel) + VALUE_SEPARATOR
                + showValue(text, emailLabel);
    }

    // Shows one header line from its label on, such as Name: (blank).
    private String showValue(String text, String label) {
        int valueStart = findValueStart(text, label);
        if (valueStart < 0) {
            return label + SPACE + BLANK_VALUE;
        }

        int lineEnd = findLineEnd(text, valueStart);
        String value = text.substring(valueStart, lineEnd).trim();
        if (value.isEmpty()) {
            value = BLANK_VALUE;
        }
        return label + SPACE + value;
    }

    // Says whether something follows the label in the header.
    private boolean hasValue(String text, String label) {
        int valueStart = findValueStart(text, label);
        if (valueStart < 0) {
            return false;
        }
        int lineEnd = findLineEnd(text, valueStart);
        String value = text.substring(valueStart, lineEnd);
        return !TextUtils.isBlank(value);
    }

    // Returns where the value after the label starts, or -1 if none. The
    // label matches as the validator reads it, so Name : counts too.
    private int findValueStart(String text, String label) {
        int scanLines = config.getInt(ConfigKeys.HEADER_SCAN_LINES);
        int lineStart = 0;
        for (int lineNumber = 0; lineNumber < scanLines
                && lineStart < text.length(); lineNumber++) {
            int lineEnd = findLineEnd(text, lineStart);
            String line = text.substring(lineStart, lineEnd);
            int valueStart = HeaderNameValidator.findValueStart(line, label);
            if (valueStart >= 0) {
                return lineStart + valueStart;
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
