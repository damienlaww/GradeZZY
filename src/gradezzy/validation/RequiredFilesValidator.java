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
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class RequiredFilesValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Required files";

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
                        question.getFolderName() + "/"
                        + question.getFileName() + " is missing. GradeZZY "
                        + "never writes a missing answer for a student."));
            }
        }

        return anomalies;
    }

    // Says whether no file in the folder has the question's file name,
    // even when case is ignored.
    private boolean isMissing(Path folder,
            Question question) throws IOException {
        return findIgnoringCase(listFiles(folder),
                question.getFileName()) == null;
    }
}
