/*
 * File: RequiredFilesValidator.java
 *
 * Purpose: Checks that every required Java source file is present.
 *
 * Where it fits: Finds missing files so later stages can skip that question and
 * award zero without failing.
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
 * Checks that every required Java source file is present, and each
 * question's other source files from the starter, such as GoldMember.java.
 * A file that is there under a name differing only in case, such as
 * q1a.java, with an extension added, such as Q1a.java.txt, or with a copy
 * number, such as Q1a (1).java, is left to FileNameValidator, so each
 * problem is reported once. A question whose folder is missing, or sits
 * inside a folder of its own name, is left to StructureValidator for the
 * same reason.
 * When the file is there with another ending, such as Q1b.java.txt, the
 * message names it; the mark is not changed.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class RequiredFilesValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Required files";

    // Comes between a file's name and its ending, as in Q1b.java
    private static final String ENDING_SEPARATOR = ".";

    // The ending of a compiled file, which renaming cannot turn into source
    private static final String CLASS_ENDING = ".class";

    // Separates the folder and the file in a message
    private static final String FOLDER_SEPARATOR = "/";

    /**
     * Creates a new required files validator.
     */
    public RequiredFilesValidator() {
        super(NAME);
    }

    /**
     * Checks that every required Java source file is present.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        try {
            return checkFiles(submission, assessment);
        } catch (IOException exception) {
            return reportUnreadable(submission, exception);
        }
    }

    // Looks for each question's file in its question folder.
    private List<Anomaly> checkFiles(Submission submission,
            Assessment assessment) throws IOException {
        List<Anomaly> anomalies = new ArrayList<>();
        Path contentRoot = findContentRoot(submission, assessment);

        if (contentRoot == null) {
            return anomalies;
        }

        for (Question question : assessment.getQuestions()) {
            Path folder = findQuestionFolder(contentRoot, question);
            boolean insideItself = findNestedQuestionFolder(folder,
                    question.getFolderName()) != null;

            // A folder dragged into itself, such as Q1/Q1, is reported once
            // by StructureValidator
            if (folder != null && !insideItself) {
                checkFolder(submission, question, folder, anomalies);
            }
        }

        return anomalies;
    }

    // Adds a problem for the question's file and for each of the
    // starter's other files that is missing from its question folder.
    private void checkFolder(Submission submission, Question question,
            Path folder, List<Anomaly> anomalies) throws IOException {
        if (isMissing(folder, question.getFileName())) {
            Path otherEnding = findOtherEnding(folder, question);
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_FILE, Severity.ERROR,
                    describeMissing(question, otherEnding)));
        }
        // A second class the student writes, such as GoldMember.java
        for (String extraFile : question.getExtraFileNames()) {
            if (isMissing(folder, extraFile)) {
                String message = question.getFolderName()
                        + FOLDER_SEPARATOR + extraFile + " is missing, "
                        + "so " + question.getId() + " cannot compile. "
                        + "The starter has it.";
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_FILE, Severity.ERROR,
                        message));
            }
        }
    }

    // Returns a file named like the question's file but with another
    // ending, such as Q1b.java.txt, or null. Case is ignored, and a
    // compiled .class file is skipped.
    private Path findOtherEnding(Path folder,
            Question question) throws IOException {
        String fileName = question.getFileName();
        int endingStart = fileName.lastIndexOf(ENDING_SEPARATOR);
        String prefix = fileName + ENDING_SEPARATOR;

        if (endingStart > 0) {
            prefix = fileName.substring(0, endingStart) + ENDING_SEPARATOR;
        }
        prefix = prefix.toLowerCase();

        for (Path file : listFiles(folder)) {
            String name = file.getFileName().toString().toLowerCase();

            if (name.startsWith(prefix) && !name.endsWith(CLASS_ENDING)) {
                return file;
            }
        }
        return null;
    }

    // Says the file is missing, and names the file with another ending
    // when there is one.
    private String describeMissing(Question question, Path otherEnding) {
        String folderName = question.getFolderName();
        String message = folderName + FOLDER_SEPARATOR
                + question.getFileName() + " is missing";

        if (otherEnding != null) {
            message += ", but " + folderName + FOLDER_SEPARATOR
                    + otherEnding.getFileName() + " is there. It may need "
                    + "renaming";
        }
        return message + ". GradeZZY never writes a missing answer for a "
                + "student.";
    }

    // Says whether no file in the folder has the file name, even when
    // case is ignored, an extension was added, as Notepad does with
    // Q1a.java.txt, or a copy number, as a browser does with
    // Q1a (1).java.
    private boolean isMissing(Path folder, String fileName)
            throws IOException {
        String wanted = fileName.toLowerCase(Locale.ROOT);
        String wantedWithEnding = wanted + ENDING_SEPARATOR;
        for (Path file : listFiles(folder)) {
            String name = file.getFileName().toString().toLowerCase(
                    Locale.ROOT);
            boolean sameName = name.equals(wanted)
                    || name.startsWith(wantedWithEnding)
                    || isCopyNumbered(name, fileName);
            if (sameName) {
                return false;
            }
        }
        return true;
    }
}
