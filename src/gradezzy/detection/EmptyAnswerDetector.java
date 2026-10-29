/*
 * File: EmptyAnswerDetector.java
 *
 * Purpose: Flags methods whose body was left empty.
 *
 * Where it fits: Tells the instructor a zero is due to no attempt.
 */
package gradezzy.detection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Flags methods whose body was left empty.
 */
public class EmptyAnswerDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Empty answer";

    // The text in front of the id of every flag a detector raises
    private static final String ID_PREFIX = "detection-";

    // What the instructor can do about an empty answer
    private static final String SUGGESTED_ACTION =
            "Confirm that the student made no attempt at this method.";

    // The folder holding the starter files
    private final Path starterDirectory;

    // Whether the untouched starter check already reports unchanged files
    private final boolean untouchedCheckOn;

    /**
     * Creates a new empty answer detector.
     *
     * @param starterDirectory the folder holding the starter files
     * @param untouchedCheckOn true if the untouched check also runs
     */
    public EmptyAnswerDetector(Path starterDirectory,
            boolean untouchedCheckOn) {
        this.starterDirectory = starterDirectory;
        this.untouchedCheckOn = untouchedCheckOn;
    }

    /**
     * Returns the name of this detector.
     *
     * @return the name of this detector
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Flags methods whose body was left empty.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the flags raised, empty if there are none
     */
    @Override
    public List<Anomaly> detect(Submission submission, Assessment assessment) {
        List<Anomaly> flags = new ArrayList<>();

        for (Question question : assessment.getQuestions()) {
            String studentText = JavaSourceText.readStudentFile(submission,
                    assessment, question);
            String starterText = JavaSourceText.readStarterFile(
                    starterDirectory, question);
            boolean bothRead = studentText != null && starterText != null;
            boolean leftToUntouched = untouchedCheckOn && bothRead
                    && JavaSourceText.isUnchanged(studentText, starterText);

            if (bothRead && !leftToUntouched) {
                checkMethods(flags, submission, question, studentText,
                        starterText);
            }
        }

        return flags;
    }

    // Flags each answer method whose body is still the starter body.
    private void checkMethods(List<Anomaly> flags, Submission submission,
            Question question, String studentText, String starterText) {
        String studentCode = JavaSourceText.removeComments(studentText);
        String starterCode = JavaSourceText.removeComments(starterText);
        List<String> methodNames =
                JavaSourceText.findAnswerMethodNames(starterCode);

        for (String methodName : methodNames) {
            String starterBody = JavaSourceText.findMethodBody(starterCode,
                    methodName);

            if (holdsStarterBody(studentCode, methodName, starterBody)) {
                flags.add(createFlag(submission,
                        JavaSourceText.describeFile(question) + ": "
                        + methodName + " still holds only the starter code."));
            }
        }
    }

    // Says whether any method with that name still holds the starter body.
    private boolean holdsStarterBody(String studentCode, String methodName,
            String starterBody) {
        for (String studentBody : JavaSourceText.findMethodBodies(
                studentCode, methodName)) {
            if (JavaSourceText.sameIgnoringSpaces(studentBody, starterBody)) {
                return true;
            }
        }

        return false;
    }

    // Builds one flag for the instructor. It never changes a score.
    private Anomaly createFlag(Submission submission, String message) {
        String id = ID_PREFIX + UUID.randomUUID();

        return new Anomaly(id, AnomalyType.EMPTY_ANSWER, Severity.WARNING,
                submission.getUsername(), message, SUGGESTED_ACTION);
    }
}
