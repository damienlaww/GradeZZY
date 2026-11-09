/*
 * File: UntouchedStarterDetector.java
 *
 * Purpose: Flags files that are identical to the starter file.
 *
 * Where it fits: Catches students who submitted the original resource folder.
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
 * Flags files that are identical to the starter file.
 *
 * <p>Tier: COULD. Owner: Jun Sheng.
 */
public class UntouchedStarterDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Untouched starter file";

    // The text in front of the id of every flag a detector raises
    private static final String ID_PREFIX = "detection-";

    // What the instructor can do about a whole unchanged submission
    private static final String WHOLE_SUBMISSION_ACTION =
            "Ask the student whether the wrong folder was submitted.";

    // What the instructor can do about one unchanged file
    private static final String ONE_FILE_ACTION =
            "Check whether the student attempted this question.";

    // The folder holding the starter files
    private final Path starterDirectory;

    /**
     * Creates a new untouched starter detector.
     *
     * @param starterDirectory the folder holding the starter files
     */
    public UntouchedStarterDetector(Path starterDirectory) {
        this.starterDirectory = starterDirectory;
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
     * Flags files that are identical to the starter file.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the flags raised, empty if there are none
     */
    @Override
    public List<Anomaly> detect(Submission submission, Assessment assessment) {
        List<String> unchangedFiles = new ArrayList<>();

        for (Question question : assessment.getQuestions()) {
            String studentText = JavaSourceText.readStudentFile(submission,
                    assessment, question);
            String starterText = JavaSourceText.readStarterFile(
                    starterDirectory, question);
            boolean bothRead = studentText != null && starterText != null;
            boolean unchanged = bothRead
                    && JavaSourceText.isUnchanged(studentText, starterText);

            if (unchanged) {
                unchangedFiles.add(JavaSourceText.describeFile(question));
            }
        }

        int questionCount = assessment.getQuestions().size();
        return createFlags(submission, unchangedFiles, questionCount);
    }

    // Raises one flag if every file is unchanged, otherwise one per file.
    private List<Anomaly> createFlags(Submission submission,
            List<String> unchangedFiles, int questionCount) {
        List<Anomaly> flags = new ArrayList<>();

        boolean allUnchanged = !unchangedFiles.isEmpty()
                && unchangedFiles.size() == questionCount;
        if (allUnchanged) {
            flags.add(createFlag(submission, "The whole submission matches "
                    + "the starter folder: all " + questionCount
                    + " question files are unchanged. The student probably "
                    + "submitted the original resource folder.",
                    WHOLE_SUBMISSION_ACTION));
        } else {
            for (String fileName : unchangedFiles) {
                flags.add(createFlag(submission, fileName + " is unchanged "
                        + "from the starter file.", ONE_FILE_ACTION));
            }
        }

        return flags;
    }

    // Builds one flag for the instructor. It never changes a score.
    private Anomaly createFlag(Submission submission, String message,
            String suggestedAction) {
        String id = ID_PREFIX + UUID.randomUUID();

        return new Anomaly(id, AnomalyType.UNTOUCHED_STARTER,
                Severity.WARNING, submission.getUsername(), message,
                suggestedAction);
    }
}
