/*
 * File: ChosenSimilarityDetector.java
 *
 * Purpose: Compares submissions with the algorithm plagiarism.algorithm
 * names at the time of each check, so a change in Settings takes effect
 * at the next check without a restart.
 *
 * Where it fits: ApplicationContext gives it to PlagiarismService. It
 * passes each check on to TokenSimilarityDetector or
 * WinnowingSimilarityDetector.
 */
package gradezzy.plagiarism;

import java.nio.file.Path;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Compares submissions with the algorithm the settings name now.
 *
 * <p>To add an algorithm, write a class that implements
 * SimilarityDetector, add its value to SettingsService and the Settings
 * tab, and pick it in choose.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class ChosenSimilarityDetector implements SimilarityDetector {

    /**
     * The plagiarism.algorithm value that picks the token trigrams; any
     * other value picks winnowing, the default.
     */
    public static final String TRIGRAM_ALGORITHM = "trigrams";

    // The application settings
    private final AppConfig config;

    // The first version: token trigrams as written
    private final SimilarityDetector trigramDetector;

    // The default: winnowing fingerprints
    private final SimilarityDetector winnowingDetector;

    /**
     * Creates a detector that follows plagiarism.algorithm.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     */
    public ChosenSimilarityDetector(AppConfig config) {
        this.config = config;
        this.trigramDetector = new TokenSimilarityDetector(config);
        this.winnowingDetector = new WinnowingSimilarityDetector(config);
    }

    /**
     * Returns the name of the algorithm the settings name now.
     *
     * <p>Tier: COULD.
     *
     * @return the name of the algorithm
     */
    @Override
    public String getName() {
        return choose().getName();
    }

    /**
     * Compares every pair of submissions with the algorithm the settings
     * name now, leaving out the code in paths.starter.dir.
     *
     * <p>Tier: COULD.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @return the pairs at or above the configured threshold
     */
    @Override
    public List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment) {
        return choose().findSimilar(submissions, assessment);
    }

    /**
     * Compares every pair of submissions with the algorithm the settings
     * name now, leaving out the code in the run's starter folder.
     *
     * <p>Tier: COULD.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @param starterDirectory the run's starter folder, or null to use
     *        paths.starter.dir
     * @return the pairs at or above the configured threshold
     */
    @Override
    public List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment, Path starterDirectory) {
        return choose().findSimilar(submissions, assessment,
                starterDirectory);
    }

    // Picks the algorithm plagiarism.algorithm names, read each time.
    private SimilarityDetector choose() {
        String algorithm = config.getString(ConfigKeys.PLAGIARISM_ALGORITHM);
        if (algorithm.trim().equals(TRIGRAM_ALGORITHM)) {
            return trigramDetector;
        }
        return winnowingDetector;
    }
}
