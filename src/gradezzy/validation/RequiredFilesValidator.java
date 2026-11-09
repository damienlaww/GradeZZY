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

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Checks that every required Java source file is present. A file that is
 * there under a name differing only in case, such as q1a.java, is left to
 * FileNameValidator, so each problem is reported once. A question whose
 * folder is missing is left to StructureValidator for the same reason.
 * When the file is there with another ending, such as Q1b.java.txt, the
 * message names it; the mark is not changed.
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

            if (folder != null && isMissing(folder, question)) {
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_FILE, Severity.ERROR,
                        describeMissing(question,
                        findOtherEnding(folder, question))));
            }
        }

        return anomalies;
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
        String message = folderName + "/" + question.getFileName()
                + " is missing";

        if (otherEnding != null) {
            message += ", but " + folderName + "/"
                    + otherEnding.getFileName() + " is there. It may need "
                    + "renaming";
        }
        return message + ". GradeZZY never writes a missing answer for a "
                + "student.";
    }

    // Says whether no file in the folder has the question's file name,
    // even when case is ignored.
    private boolean isMissing(Path folder,
            Question question) throws IOException {
        return findIgnoringCase(listFiles(folder),
                question.getFileName()) == null;
    }
}
