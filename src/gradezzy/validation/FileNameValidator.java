/*
 * File: FileNameValidator.java
 *
 * Purpose: Checks that source files follow the naming convention.
 *
 * Where it fits: Finds files that exist but are misnamed, for example q1a.java,
 * Q1A.java, Q1a.java.txt or Q1a (1).java, so CaseNameFixer can rename them.
 */
package gradezzy.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Checks that source files follow the naming convention. A file counts as
 * misnamed when its name matches the required name only if case is
 * ignored, when it is the required name with an ending added, such as
 * Q1a.java.txt from Notepad, or with a browser's copy number, such as
 * Q1a (1).java. Java needs the exact name, so such a file
 * would not compile. The answer file of each question is checked, and so
 * are the other files the starter has for it, such as GoldMember.java.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class FileNameValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "File names";

    // Starts the ending a program such as Notepad adds to a file name
    private static final String EXTENSION_START = ".";

    // Separates the folder and the file in a message
    private static final String FOLDER_SEPARATOR = "/";

    /**
     * Creates a new file name validator.
     */
    public FileNameValidator() {
        super(NAME);
    }

    /**
     * Checks that source files follow the naming convention.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        try {
            return checkNames(submission, assessment);
        } catch (IOException exception) {
            return reportUnreadable(submission, exception);
        }
    }

    // Compares the files in each question folder with the expected names:
    // the answer file first, then the starter's other files.
    private List<Anomaly> checkNames(Submission submission,
            Assessment assessment) throws IOException {
        List<Anomaly> anomalies = new ArrayList<>();
        Path contentRoot = findContentRoot(submission, assessment);

        if (contentRoot == null) {
            return anomalies;
        }

        for (Question question : assessment.getQuestions()) {
            Path folder = findQuestionFolder(contentRoot, question);

            if (folder != null) {
                checkFolderNames(submission, question, folder, anomalies);
            }
        }

        return anomalies;
    }

    // Compares the files in one question folder with the expected names
    // and adds a problem for each misnamed file.
    private void checkFolderNames(Submission submission, Question question,
            Path folder, List<Anomaly> anomalies) throws IOException {
        List<Path> files = listFiles(folder);
        List<String> wantedNames = new ArrayList<>();
        wantedNames.add(question.getFileName());
        wantedNames.addAll(question.getExtraFileNames());

        for (String wantedName : wantedNames) {
            Path misnamed = findMisnamed(files, wantedName);

            if (misnamed != null) {
                anomalies.add(createAnomaly(submission,
                        AnomalyType.WRONG_FILE_NAME, Severity.WARNING,
                        describe(question, misnamed, wantedName)));
            }
        }
    }

    // Returns the file whose name differs from the wanted one only in case,
    // else one named like it with an ending added, such as .txt, else one
    // with a copy number, such as Q1a (1).java. Returns null when the
    // right name is there or nothing is close.
    private Path findMisnamed(List<Path> files, String wantedName) {
        if (findExact(files, wantedName) != null) {
            return null;
        }

        Path wrongCase = findIgnoringCase(files, wantedName);
        if (wrongCase != null) {
            return wrongCase;
        }

        Path withEnding = findWithEnding(files, wantedName);
        if (withEnding != null) {
            return withEnding;
        }

        return findCopyNumbered(files, wantedName);
    }

    // Returns the file named like the wanted one with a browser's copy
    // number, such as Q1a (1).java, or null.
    private Path findCopyNumbered(List<Path> files, String wantedName) {
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            if (isCopyNumbered(fileName, wantedName)) {
                return file;
            }
        }

        return null;
    }

    // Returns the file whose name is the wanted name, in any case, with an
    // ending added after a dot, such as Q1a.java.txt, or null.
    private Path findWithEnding(List<Path> files, String wantedName) {
        String start = wantedName.toLowerCase(Locale.ROOT) + EXTENSION_START;

        for (Path file : files) {
            String fileName = file.getFileName().toString().toLowerCase(
                    Locale.ROOT);

            boolean hasEnding = fileName.startsWith(start)
                    && fileName.length() > start.length();
            if (hasEnding) {
                return file;
            }
        }

        return null;
    }

    // Names the wrong and the right file, from the student's folder.
    // CaseNameFixer reads the right name back from this message.
    private String describe(Question question, Path misnamed,
            String wantedName) {
        String folderName = question.getFolderName();

        return folderName + FOLDER_SEPARATOR + misnamed.getFileName()
                + " should be named "
                + folderName + FOLDER_SEPARATOR + wantedName + ".";
    }
}
