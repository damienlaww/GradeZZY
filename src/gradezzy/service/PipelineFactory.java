/*
 * File: PipelineFactory.java
 *
 * Purpose: Creates the grading pipeline and decides which stages,
 * validators and detectors it contains, and in what order.
 *
 * Where it fits: The one place to edit when a new GradingStage, Validator
 * or Detector class is added. Nothing else needs to change.
 */
package gradezzy.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.detection.Detector;
import gradezzy.detection.EmptyAnswerDetector;
import gradezzy.detection.HardCodedAnswerDetector;
import gradezzy.detection.UntouchedStarterDetector;
import gradezzy.exec.ProcessRunner;
import gradezzy.exec.SourceCompiler;
import gradezzy.exec.TesterRunner;
import gradezzy.fix.FixManager;
import gradezzy.grading.CancellationToken;
import gradezzy.grading.CompileStage;
import gradezzy.grading.DetectStage;
import gradezzy.grading.ExecuteStage;
import gradezzy.grading.ExtractStage;
import gradezzy.grading.FixStage;
import gradezzy.grading.GradingPipeline;
import gradezzy.grading.InstallTestersStage;
import gradezzy.grading.PersistStage;
import gradezzy.grading.ScoreCalculator;
import gradezzy.grading.ScoreStage;
import gradezzy.grading.TesterOutputScoreParser;
import gradezzy.grading.ValidateStage;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.ingest.ZipExtractor;
import gradezzy.persistence.ResultRepository;
import gradezzy.testcase.TesterInstaller;
import gradezzy.validation.FileNameValidator;
import gradezzy.validation.HeaderNameValidator;
import gradezzy.validation.RequiredFilesValidator;
import gradezzy.validation.StructureValidator;
import gradezzy.validation.ValidationRunner;

/**
 * Builds the grading pipeline from its stages.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class PipelineFactory {

    // The application settings
    private final AppConfig config;

    // The fix manager shared with the anomaly log
    private final FixManager fixManager;

    // The store results are saved to
    private final ResultRepository repository;

    /**
     * Creates a new pipeline factory.
     *
     * @param config the application settings
     * @param fixManager the fix manager shared with the anomaly log
     * @param repository the store results are saved to
     */
    public PipelineFactory(AppConfig config, FixManager fixManager,
            ResultRepository repository) {
        this.config = config;
        this.fixManager = fixManager;
        this.repository = repository;
    }

    /**
     * Creates a pipeline with every stage in grading order.
     *
     * @param cancellationToken the flag that stops the batch when set
     * @return the pipeline, ready to run
     */
    public GradingPipeline createPipeline(
            CancellationToken cancellationToken) {
        GradingPipeline pipeline = new GradingPipeline(cancellationToken);
        ProcessRunner processRunner = new ProcessRunner(config);
        WorkingCopyManager workingCopyManager = new WorkingCopyManager(
                config, new ZipExtractor(config));

        // To add a stage, write a class that implements GradingStage and
        // add it here, in the position it should run.
        pipeline.addStage(new ExtractStage(workingCopyManager,
                new RosterReader(config)));
        pipeline.addStage(new ValidateStage(createValidationRunner()));
        pipeline.addStage(new FixStage(fixManager));
        pipeline.addStage(new DetectStage(createDetectors()));
        pipeline.addStage(new InstallTestersStage(
                new TesterInstaller(config)));
        pipeline.addStage(new CompileStage(new SourceCompiler(config,
                processRunner)));
        pipeline.addStage(new ExecuteStage(new TesterRunner(config,
                processRunner), new TesterOutputScoreParser(config)));
        pipeline.addStage(new ScoreStage(new ScoreCalculator()));
        pipeline.addStage(new PersistStage(repository));

        return pipeline;
    }

    // To add a validator, write a class that extends AbstractValidator
    // and add it here.
    private ValidationRunner createValidationRunner() {
        ValidationRunner runner = new ValidationRunner();

        runner.addValidator(new StructureValidator());
        runner.addValidator(new FileNameValidator());
        runner.addValidator(new RequiredFilesValidator());
        runner.addValidator(new HeaderNameValidator(config));

        return runner;
    }

    // To add a detector, write a class that implements Detector and add
    // it here.
    private List<Detector> createDetectors() {
        List<Detector> detectors = new ArrayList<>();
        Path starterDirectory = Paths.get(
                config.getString(ConfigKeys.STARTER_DIR));
        boolean untouchedOn = config.getBoolean(
                ConfigKeys.DETECT_UNTOUCHED_ENABLED);

        if (untouchedOn) {
            detectors.add(new UntouchedStarterDetector(starterDirectory));
        }

        if (config.getBoolean(ConfigKeys.DETECT_EMPTY_ENABLED)) {
            detectors.add(new EmptyAnswerDetector(starterDirectory,
                    untouchedOn));
        }

        if (config.getBoolean(ConfigKeys.DETECT_HARD_CODED_ENABLED)) {
            detectors.add(new HardCodedAnswerDetector());
        }

        return detectors;
    }
}
