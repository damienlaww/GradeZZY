/*
 * File: HardCodedAnswerDetector.java
 *
 * Purpose: Flags answers that look hard-coded to the sample cases.
 *
 * Where it fits: Never changes a mark: it adds a HARD_CODED_ANSWER anomaly for
 * the instructor to review.
 */
package gradezzy.detection;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Flags answers that look hard-coded to the sample cases.
 *
 * <p>Tier: SHOULD. Owner: Jun Sheng.
 */
public class HardCodedAnswerDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Hard-coded answer";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new hard coded answer detector.
     *
     * @param config the application settings
     */
    public HardCodedAnswerDetector(AppConfig config) {
        this.config = config;
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
     * Flags answers that look hard-coded to the sample cases.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the flags raised, empty if there are none
     */
    @Override
    public List<Anomaly> detect(Submission submission, Assessment assessment) {
        // TODO(Jun Sheng): implement: heuristics such as literal expected
        //     outputs in the source, or passing only the sample cases from the
        //     question paper
        return new ArrayList<>();
    }
}
