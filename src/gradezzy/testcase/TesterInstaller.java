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
import java.nio.file.StandardCopyOption;
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

    private static final String ID_PREFIX = "tester-data-";
    private static final String MISSING_DATA_ACTION =
            "Add the data file to the tester folder, then grade again.";

    // The application settings
    private final AppConfig config;

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
                    flagMissingDataFiles(sourceFile, questionFolder,
                            submission);
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
                if (Files.isRegularFile(dataFile)) {
                    copyReplacing(dataFile, questionFolder);
                }
            }
        } catch (IOException exception) {
            throw new TestCaseException("Could not copy the tester data "
                    + "files: " + exception.getMessage(), exception);
        }
    }

    // Adds an anomaly for each data file the tester reads that is not in
    // the tester folder, since the tester would then fail those cases
    // with no warning. The score is not changed.
    private void flagMissingDataFiles(Path testerFile, Path questionFolder,
            Submission submission) throws IOException {
        // ISO-8859-1 reads any bytes, so a tester saved in another
        // encoding cannot stop grading; the names searched are ASCII
        String testerSource = Files.readString(testerFile,
                StandardCharsets.ISO_8859_1);
        List<String> dataFileNames = config.getList(
                ConfigKeys.TESTER_DATA_FILES);

        for (String dataFileName : dataFileNames) {
            boolean needed = testerSource.contains(dataFileName);
            boolean present = Files.isRegularFile(
                    questionFolder.resolve(dataFileName));
            if (needed && !present) {
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
    }

    /**
     * Copies a file into a folder, replacing a file with the same name.
     *
     * @param file the file to copy
     * @param folder the folder to copy it into
     * @throws IOException if the copy fails
     */
    private void copyReplacing(Path file, Path folder) throws IOException {
        Files.copy(file, folder.resolve(file.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
