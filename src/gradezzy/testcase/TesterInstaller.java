/*
 * File: TesterInstaller.java
 *
 * Purpose: Copies tester files and their data files into a working copy.
 *
 * Where it fits: Does automatically what the readme of the provided testers
 * asks the instructor to do by hand.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Copies tester files and their data files into a working copy.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterInstaller {

    // Start of the id of each anomaly this class reports
    private static final String ID_PREFIX = "tester-data-";

    // What the instructor can do about a missing data file
    private static final String MISSING_DATA_ACTION =
            "Add the data file to the tester folder, then grade again.";

    // The application settings
    private final AppConfig config;

    // The bytes of each file of the tester folder, kept from the first
    // read, so code run for one student cannot change them for the next
    private final HashMap<Path, byte[]> originals = new HashMap<>();

    /**
     * Creates a new tester installer.
     *
     * @param config the application settings
     */
    public TesterInstaller(AppConfig config) {
        this.config = config;
    }

    /**
     * Copies each tester into the matching question folder. A question
     * whose folder is missing is skipped: the validators already reported
     * it. A tester or data file the student included is replaced, so a
     * student cannot ship a tester that always passes.
     *
     * @param testers the testers
     * @param submission the submission
     * @param assessment the assessment
     * @throws TestCaseException if the operation fails
     */
    public void install(List<TesterFile> testers, Submission submission,
            Assessment assessment) throws TestCaseException {
        try {
            for (TesterFile tester : testers) {
                Question question = assessment.findQuestion(
                        tester.getQuestionId());
                Path questionFolder = null;
                if (question != null) {
                    questionFolder = QuestionFolderFinder.find(submission,
                            assessment, question);
                }

                if (questionFolder != null) {
                    Path sourceFile = tester.getSourceFile();
                    copyReplacing(sourceFile, questionFolder);
                    copyDataFiles(sourceFile.getParent(), questionFolder);
                    flagMissingDataFiles(sourceFile, submission);
                }
            }
        } catch (IOException exception) {
            throw new TestCaseException("Could not install the testers for "
                    + submission.getUsername() + ": "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Copies the data files listed in config next to a tester. The
     * student's own copy of each file is deleted first, so a student
     * cannot change the data the tester checks against. Only the tester
     * folder's copy is used.
     *
     * @param sourceDirectory the source directory
     * @param questionFolder the question folder
     * @throws TestCaseException if the operation fails
     */
    public void copyDataFiles(Path sourceDirectory,
            Path questionFolder) throws TestCaseException {
        List<String> dataFileNames = config.getList(
                ConfigKeys.TESTER_DATA_FILES);

        try {
            for (String dataFileName : dataFileNames) {
                Files.deleteIfExists(questionFolder.resolve(dataFileName));
                Path dataFile = sourceDirectory.resolve(dataFileName);
                if (hasOriginal(dataFile)) {
                    copyReplacing(dataFile, questionFolder);
                }
            }
        } catch (IOException exception) {
            throw new TestCaseException("Could not copy the tester data "
                    + "files: " + exception.getMessage(), exception);
        }
    }

    /**
     * Lists the data files a tester reads that are not in its folder.
     * Tier: SHOULD.
     *
     * @param testerFile the tester source file
     * @return the names of the missing data files
     * @throws IOException if the tester cannot be read
     */
    public List<String> findMissingDataFiles(Path testerFile)
            throws IOException {
        // ISO-8859-1 reads any bytes, so a tester saved in another
        // encoding cannot stop grading; the names searched are ASCII
        String testerSource = new String(readOriginal(testerFile),
                StandardCharsets.ISO_8859_1);
        List<String> dataFileNames = config.getList(
                ConfigKeys.TESTER_DATA_FILES);
        List<String> missing = new ArrayList<>();

        for (String dataFileName : dataFileNames) {
            boolean needed = testerSource.contains(dataFileName);
            boolean present = hasOriginal(
                    testerFile.resolveSibling(dataFileName));
            if (needed && !present) {
                missing.add(dataFileName);
            }
        }
        return missing;
    }

    // Adds an anomaly for each data file the tester reads that is not in
    // the tester folder, since the tester would then fail those cases
    // with no warning. The score is not changed.
    private void flagMissingDataFiles(Path testerFile,
            Submission submission) throws IOException {
        for (String dataFileName : findMissingDataFiles(testerFile)) {
            submission.addAnomaly(new Anomaly(
                    ID_PREFIX + UUID.randomUUID(),
                    AnomalyType.MISSING_FILE, Severity.ERROR,
                    submission.getUsername(),
                    testerFile.getFileName() + " reads " + dataFileName
                    + ", which is not in the tester folder, so its "
                    + "cases will fail.",
                    MISSING_DATA_ACTION));
        }
    }

    /**
     * Copies a file of the tester folder into a folder, replacing a file
     * with the same name.
     *
     * @param file the file to copy
     * @param folder the folder to copy it into
     * @throws IOException if the copy fails
     */
    private void copyReplacing(Path file, Path folder) throws IOException {
        Path target = folder.resolve(file.getFileName());
        Files.deleteIfExists(target);
        Files.write(target, readOriginal(file));
    }

    /**
     * Returns the bytes of a file of the tester folder as first read. Every
     * file is read while the first student is installed, before any tester
     * of the run can change it. The worker threads share this object, so
     * only one of them reads at a time.
     *
     * @param file the file
     * @return its bytes
     * @throws IOException if it cannot be read
     */
    private synchronized byte[] readOriginal(Path file) throws IOException {
        if (!originals.containsKey(file)) {
            originals.put(file, Files.readAllBytes(file));
        }
        return originals.get(file);
    }

    /**
     * Checks whether the tester folder has a file, or had it when first
     * read.
     *
     * @param file the file
     * @return true if the file can be used
     */
    private synchronized boolean hasOriginal(Path file) {
        return originals.containsKey(file) || Files.isRegularFile(file);
    }
}
