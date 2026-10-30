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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Writes the student's name and email into a blank file header.
 */
public class HeaderNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fill in header";

    // The roster's value for a part of a name that is empty
    private static final String EMPTY_NAME_PART = "_";

    // Ends the file path at the start of the header anomaly's message
    private static final String PATH_END = " ";

    // Separates the question folder and the file in that path
    private static final String FOLDER_SEPARATOR = "/";

    // The ending of every source file the header check looks at
    private static final String JAVA_EXTENSION = ".java";

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

        String relativePath = readRelativePath(anomaly);
        Path file = findFile(submission.getWorkingCopyPath(),
                submission.getUsername(), relativePath);
        String name = buildFullName(student);
        String email = student.getEmail();

        String nameLabel = config.getString(ConfigKeys.HEADER_NAME_LABEL);
        String emailLabel = config.getString(ConfigKeys.HEADER_EMAIL_LABEL);
        String original = readFile(file);
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
        writeFile(file, filled);

        return new FixRecord(anomaly, NAME, "Filled in the header of "
                + relativePath + " with " + written + ".", file, file,
                backupPath);
    }

    /**
     * Restores the file from its backup.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        Path backupPath = fixRecord.getBackupPath();
        Path file = fixRecord.getFixedPath();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            Anomaly anomaly = fixRecord.getAnomaly();
            file = findFile(findWorkingCopyOf(backupPath),
                    anomaly.getUsername(), readRelativePath(anomaly));
        }

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

    // Reads the file, such as Q1/Q1a.java, named at the start of the message.
    private String readRelativePath(Anomaly anomaly) throws FixException {
        String message = anomaly.getMessage();
        int end = message.indexOf(PATH_END);
        String path = "";
        if (end > 0) {
            path = message.substring(0, end);
        }

        if (path.contains(FOLDER_SEPARATOR) && path.endsWith(JAVA_EXTENSION)) {
            return path;
        }
        throw new FixException("Could not tell which file this header "
                + "problem is about, so nothing was changed.");
    }

    // Finds the file in whichever layout the working copy now has.
    private Path findFile(Path workingCopy, String username,
            String relativePath) throws FixException {
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        Path inUsernameFolder = workingCopy.resolve(username)
                .resolve(relativePath);
        if (isFile(inUsernameFolder)) {
            return inUsernameFolder;
        }

        Path atTop = workingCopy.resolve(relativePath);
        if (isFile(atTop)) {
            return atTop;
        }

        Path onlyFolder = findOnlyFolder(workingCopy);
        if (onlyFolder != null && isFile(onlyFolder.resolve(relativePath))) {
            return onlyFolder.resolve(relativePath);
        }
        throw new FixException("Could not find " + relativePath
                + " in the working copy.");
    }

    // Returns the only visible top folder, or null.
    private Path findOnlyFolder(Path workingCopy) throws FixException {
        Path onlyFolder = null;
        int folderCount = 0;

        try {
            DirectoryStream<Path> children =
                    Files.newDirectoryStream(workingCopy);
            try {
                for (Path child : children) {
                    if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                            && !isHidden(child)) {
                        onlyFolder = child;
                        folderCount++;
                    }
                }
            } finally {
                children.close();
            }
        } catch (IOException exception) {
            throw new FixException("Could not list the working copy: "
                    + exception.getMessage(), exception);
        }

        if (folderCount != 1) {
            return null;
        }
        return onlyFolder;
    }

    // Says whether a path is an ordinary file.
    private boolean isFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    // Joins the roster's first and last name, skipping an empty part.
    private String buildFullName(Student student) {
        String firstName = student.getFirstName();
        String lastName = student.getLastName();
        boolean hasFirst = !TextUtils.isBlank(firstName)
                && !firstName.equals(EMPTY_NAME_PART);
        boolean hasLast = !TextUtils.isBlank(lastName)
                && !lastName.equals(EMPTY_NAME_PART);

        if (hasFirst && hasLast) {
            return firstName.trim() + SPACE + lastName.trim();
        } else if (hasFirst) {
            return firstName.trim();
        } else if (hasLast) {
            return lastName.trim();
        }
        return "";
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

    // Reads a source file, turning a failure into a FixException.
    private String readFile(Path file) throws FixException {
        try {
            return FileUtils.readText(file);
        } catch (IOException exception) {
            throw new FixException("Could not read " + file.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }

    // Writes a source file, turning a failure into a FixException.
    private void writeFile(Path file, String text) throws FixException {
        try {
            FileUtils.writeText(file, text);
        } catch (IOException exception) {
            throw new FixException("Could not write " + file.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }
}
