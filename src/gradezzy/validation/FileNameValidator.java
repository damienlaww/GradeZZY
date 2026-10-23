/*
 * File: FileNameValidator.java
 *
 * Purpose: Checks that source files follow the naming convention.
 *
 * Where it fits: Finds files that exist but are misnamed, for example q1a.java
 * or Q1A.java.
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
 * Checks that source files follow the naming convention. A file counts as
 * misnamed when its name matches the required name only if case is
 * ignored. Java needs the exact name, so such a file would not compile.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class FileNameValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "File names";

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

    // Compares the files in each question folder with the expected name.
    private List<Anomaly> checkNames(Submission submission,
            Assessment assessment) throws IOException {
        List<Anomaly> anomalies = new ArrayList<>();
        Path contentRoot = findContentRoot(submission, assessment);

        if (contentRoot == null) {
            return anomalies;
        }

        for (Question question : assessment.getQuestions()) {
            Path folder = findQuestionFolder(contentRoot, question);
            Path misnamed = null;

            if (folder != null) {
                misnamed = findMisnamed(folder, question);
            }

            if (misnamed != null) {
                anomalies.add(createAnomaly(submission,
                        AnomalyType.WRONG_FILE_NAME, Severity.WARNING,
                        describe(question, misnamed)));
            }
        }

        return anomalies;
    }

    // Returns the file whose name differs from the required one only in
    // case, or null when the right name is there or nothing is close.
    private Path findMisnamed(Path folder,
            Question question) throws IOException {
        List<Path> files = listFiles(folder);

        if (findExact(files, question.getFileName()) != null) {
            return null;
        }

        return findIgnoringCase(files, question.getFileName());
    }

    // Names the wrong and the right file, and where the wrong one is.
    private String describe(Question question, Path misnamed) {
        String folderName = question.getFolderName();

        return folderName + "/" + misnamed.getFileName() + " should be named "
                + folderName + "/" + question.getFileName()
                + ". Working copy file: " + misnamed;
    }
}
