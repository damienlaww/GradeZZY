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

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Flags files that are identical to the starter file.
 *
 * <p>Tier: SHOULD. Owner: Jun Sheng.
 */
public class UntouchedStarterDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Untouched starter file";

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
        // TODO(Jun Sheng): implement: a comparison of each source file with the
        //     starter file, ignoring the header and blank lines
        return new ArrayList<>();
    }
}
