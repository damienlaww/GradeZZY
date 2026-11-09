/*
 * File: EncodingFixer.java
 *
 * Purpose: Saves a Java file again as plain UTF-8, so javac can read it.
 *
 * Where it fits: Handles the wrong encoding case that FixStage finds: a file
 * that starts with a byte order mark, one saved as UTF-16, or one saved as
 * Windows-1252.
 */
package gradezzy.fix;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Saves a Java file again as plain UTF-8, so javac can read it.
 */
public class EncodingFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Save as UTF-8";

    // Follows the file's path in the message for a byte order mark
    private static final String MARK_PROBLEM = " starts with a byte order "
            + "mark (saved as UTF-8 with BOM), so it does not compile.";

    // Follows the file's path in the message for a UTF-16 file
    private static final String UTF16_PROBLEM = " is saved as UTF-16 "
            + "(Notepad's \"Unicode\"), so it does not compile.";

    // Follows the file's path in the message for a file not in UTF-8
    private static final String CHARSET_PROBLEM = " is not saved as UTF-8 "
            + "(it may be Windows-1252), so it does not compile.";

    // The three bytes Notepad writes at the start of "UTF-8 with BOM"
    private static final byte[] BYTE_ORDER_MARK = {
        (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };

    // The usual Windows encoding, used to read a file that is not UTF-8
    private static final String WINDOWS_CHARSET = "windows-1252";

    // The ending of a Java source file name
    private static final String JAVA_SUFFIX = ".java";

    // The start of a hidden name, such as .gradezzy-backup
    private static final String HIDDEN_NAME_PREFIX = ".";

    /**
     * Creates a new encoding fixer.
     */
    public EncodingFixer() {
        super(NAME);
    }

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.WRONG_ENCODING;
    }

    /**
     * Finds the file this fix would save again as UTF-8.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the file
     * @throws FixException if there is no working copy or the file cannot
     *         be found
     */
    @Override
    public Path findPathToChange(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        Path root = workingCopy.toAbsolutePath().normalize();
        Path file = findFileInMessage(root, anomaly);
        if (file == null || !file.startsWith(root)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("Could not find the file this encoding "
                    + "problem is about, so nothing was changed.");
        }
        return file;
    }

    /**
     * Backs up the file, then writes it again as UTF-8 with no byte order
     * mark.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone, or null when the
     *         file is already plain UTF-8
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path file = findPathToChange(submission, anomaly);
        byte[] original = readBytes(file);
        byte[] converted = convertToUtf8(original);
        if (Arrays.equals(converted, original)) {
            return null;
        }

        String fileName = file.getFileName().toString();
        String change = "Saved " + fileName + " again as UTF-8, "
                + "reading it as Windows-1252.";
        String before = fileName + " saved as Windows-1252";
        if (hasByteOrderMark(original)) {
            change = "Removed the byte order mark from the start of "
                    + fileName + ".";
            before = fileName + " saved as UTF-8 with BOM";
        } else if (TextUtils.isUtf16(original)) {
            change = "Saved " + fileName + " again as UTF-8, reading it as "
                    + "UTF-16.";
            before = fileName + " saved as UTF-16";
        }
        String after = fileName + " saved as UTF-8";

        Path backupPath = backup(submission, file);
        writeBytes(file, converted);

        return new FixRecord(anomaly, NAME, change,
                submission.getWorkingCopyPath(), file, file, backupPath,
                before, after);
    }

    /**
     * Puts back the exact bytes the file had, wherever the file is now.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        Path backupPath = fixRecord.getBackupPath();
        byte[] written = convertToUtf8(readBytes(backupPath));
        Path file = findFileNow(fixRecord, written);

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

    /**
     * Writes the anomaly message for a file javac cannot read as UTF-8.
     *
     * @param shownPath the file's path in the working copy, with forward
     *        slashes, such as ping.lee.2023/Q1/Q1a.java
     * @param bytes the content of the file
     * @return the message, which starts with the path
     */
    public static String describeProblem(String shownPath, byte[] bytes) {
        if (hasByteOrderMark(bytes)) {
            return shownPath + MARK_PROBLEM;
        } else if (TextUtils.isUtf16(bytes)) {
            return shownPath + UTF16_PROBLEM;
        }
        return shownPath + CHARSET_PROBLEM;
    }

    /**
     * Says whether javac would fail to read these bytes as UTF-8.
     *
     * @param bytes the content of a Java file
     * @return true if they start with a byte order mark, are UTF-16 or
     *         are not UTF-8
     */
    public static boolean needsConverting(byte[] bytes) {
        return hasByteOrderMark(bytes) || TextUtils.isUtf16(bytes)
                || !isValidUtf8(bytes);
    }

    /**
     * Says whether the bytes start with the UTF-8 byte order mark.
     *
     * @param bytes the content of a file
     * @return true if they start with the byte order mark
     */
    public static boolean hasByteOrderMark(byte[] bytes) {
        if (bytes.length < BYTE_ORDER_MARK.length) {
            return false;
        }

        for (int i = 0; i < BYTE_ORDER_MARK.length; i++) {
            if (bytes[i] != BYTE_ORDER_MARK[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lists every Java file in a folder and the folders inside it, leaving
     * out hidden files and folders such as .gradezzy-backup.
     *
     * @param folder the folder to search
     * @return the Java files found, in name order within each folder
     * @throws IOException if a folder cannot be listed
     */
    public static List<Path> findJavaFiles(Path folder) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        List<Path> children = new ArrayList<>();
        DirectoryStream<Path> listing = null;

        try {
            listing = Files.newDirectoryStream(folder);
            for (Path child : listing) {
                children.add(child);
            }
        } finally {
            FileUtils.closeIfOpen(listing);
        }
        Collections.sort(children);

        for (Path child : children) {
            String childName = child.getFileName().toString();
            boolean visible = !childName.startsWith(HIDDEN_NAME_PREFIX);

            if (visible
                    && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                javaFiles.addAll(findJavaFiles(child));
            } else if (visible
                    && Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
                    && childName.endsWith(JAVA_SUFFIX)) {
                javaFiles.add(child);
            }
        }
        return javaFiles;
    }

    // Removes a byte order mark, reads UTF-16 by its byte order mark, and
    // reads other non-UTF-8 bytes as Windows-1252.
    private static byte[] convertToUtf8(byte[] bytes) {
        if (TextUtils.isUtf16(bytes)) {
            String text = TextUtils.decodeSource(bytes);
            return text.getBytes(StandardCharsets.UTF_8);
        }

        byte[] withoutMark = bytes;
        if (hasByteOrderMark(bytes)) {
            withoutMark = Arrays.copyOfRange(bytes, BYTE_ORDER_MARK.length,
                    bytes.length);
        }

        if (isValidUtf8(withoutMark)) {
            return withoutMark;
        }
        String text = new String(withoutMark,
                Charset.forName(WINDOWS_CHARSET));
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Says whether the bytes are valid UTF-8; the decoder throws if not.
    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    // Finds the file the message names, or returns null.
    private Path findFileInMessage(Path root, Anomaly anomaly) {
        String shownPath = findShownPath(anomaly.getMessage());
        if (shownPath == null || shownPath.isEmpty()) {
            return null;
        }

        try {
            return root.resolve(shownPath).normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    // Returns the file path that starts the message, or null.
    private String findShownPath(String message) {
        if (message.endsWith(MARK_PROBLEM)) {
            return message.substring(0,
                    message.length() - MARK_PROBLEM.length());
        } else if (message.endsWith(UTF16_PROBLEM)) {
            return message.substring(0,
                    message.length() - UTF16_PROBLEM.length());
        } else if (message.endsWith(CHARSET_PROBLEM)) {
            return message.substring(0,
                    message.length() - CHARSET_PROBLEM.length());
        }
        return null;
    }

    // Finds the fixed file, even if a later fix moved it.
    private Path findFileNow(FixRecord fixRecord,
            byte[] written) throws FixException {
        Path fixedPath = fixRecord.getFixedPath();
        if (holdsBytes(fixedPath, written)) {
            return fixedPath;
        }

        // Else the one file of that name still holding what the fix wrote
        List<Path> matches = new ArrayList<>();
        for (Path candidate : listJavaFiles(fixRecord.getWorkingCopy())) {
            String candidateName = candidate.getFileName().toString();
            if (candidateName.equalsIgnoreCase(
                    fixedPath.getFileName().toString())
                    && holdsBytes(candidate, written)) {
                matches.add(candidate);
            }
        }

        if (matches.size() != 1) {
            throw new FixException("Could not find " + fixedPath.getFileName()
                    + " as this fix left it. If another fix changed the "
                    + "file since, undo that fix first.");
        }
        return matches.get(0);
    }

    // Says whether a file exists and holds exactly these bytes.
    private boolean holdsBytes(Path file, byte[] bytes) throws FixException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return Arrays.equals(readBytes(file), bytes);
    }

    // Lists the working copy's Java files, or throws a FixException.
    private List<Path> listJavaFiles(Path workingCopy) throws FixException {
        try {
            return findJavaFiles(workingCopy);
        } catch (IOException exception) {
            throw new FixException("Could not list the working copy: "
                    + exception.getMessage(), exception);
        }
    }

    // Reads a file, turning a failure into a FixException.
    private byte[] readBytes(Path file) throws FixException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new FixException("Could not read " + file.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }

    // Writes a file, turning a failure into a FixException.
    private void writeBytes(Path file, byte[] bytes) throws FixException {
        try {
            Files.write(file, bytes);
        } catch (IOException exception) {
            throw new FixException("Could not write " + file.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }
}
