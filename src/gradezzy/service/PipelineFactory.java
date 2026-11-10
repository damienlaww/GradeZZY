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
import gradezzy.detection.CopiedHeaderDetector;
import gradezzy.detection.Detector;
import gradezzy.detection.EmptyAnswerDetector;
import gradezzy.detection.HardCodedAnswerDetector;
import gradezzy.detection.RosterCheck;
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
import gradezzy.modelanswer.ModelAnswerCopy;
import gradezzy.modelanswer.RandomInputChecker;
import gradezzy.modelanswer.RandomInputStage;
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
     * <p>Tier: MUST.
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
     * <p>Tier: MUST.
     *
     * @param cancellationToken the flag that stops the batch when set
     * @return the pipeline, ready to run
     */
    public GradingPipeline createPipeline(
            CancellationToken cancellationToken) {
        return createPipeline(cancellationToken, null);
    }

    /**
     * Creates a pipeline whose checks compare answers with the starter
     * folder chosen for the batch.
     *
     * <p>Tier: MUST.
     *
     * @param cancellationToken the flag that stops the batch when set
     * @param starterDirectory the starter folder, or null to use
     *        paths.starter.dir
     * @return the pipeline, ready to run
     */
    public GradingPipeline createPipeline(
            CancellationToken cancellationToken, Path starterDirectory) {
        GradingPipeline pipeline = new GradingPipeline(cancellationToken);
        ProcessRunner processRunner = new ProcessRunner(config);
        WorkingCopyManager workingCopyManager = new WorkingCopyManager(
                config, new ZipExtractor(config));

        // To add a stage, write a class that implements GradingStage and
        // add it here, in the position it should run.
        pipeline.addStage(new ExtractStage(workingCopyManager,
                new RosterReader(config), starterDirectory));
        pipeline.addStage(new ValidateStage(createValidationRunner()));
        pipeline.addStage(new FixStage(fixManager,
                createFileCheckRunner(), workingCopyManager,
                starterDirectory));
        List<Detector> detectors = createDetectors(starterDirectory);
        pipeline.addStage(new DetectStage(detectors, createRosterChecks()));
        addRunStages(pipeline, processRunner, starterDirectory);
        pipeline.addStage(new ScoreStage(new ScoreCalculator()));
        pipeline.addStage(new PersistStage(repository));

        return pipeline;
    }

    /**
     * Creates the shorter pipeline that grades one question of a student
     * again in the working copy they already have: it adds the tester,
     * compiles and runs it, as the full pipeline does. Extracting,
     * checking, fixing and the code checks are left out, so the fixes and
     * flags of the student stay as they are. Scoring and saving are left
     * to GradingService.regradeQuestion, because ScoreStage would give
     * every other question a result too.
     *
     * <p>Tier: SHOULD.
     *
     * @param cancellationToken the flag that stops the grading when set
     * @param starterDirectory the starter folder, or null to use
     *        paths.starter.dir
     * @return the pipeline, ready to run
     */
    public GradingPipeline createQuestionPipeline(
            CancellationToken cancellationToken, Path starterDirectory) {
        GradingPipeline pipeline = new GradingPipeline(cancellationToken);
        ProcessRunner processRunner = new ProcessRunner(config);

        addRunStages(pipeline, processRunner, starterDirectory);

        return pipeline;
    }

    // Adds the stages that put the testers in place, compile, run the
    // testers and call methods with random inputs. They share one
    // compiler and one tester runner.
    private void addRunStages(GradingPipeline pipeline,
            ProcessRunner processRunner, Path starterDirectory) {
        pipeline.addStage(new InstallTestersStage(
                new TesterInstaller(config)));
        SourceCompiler compiler = new SourceCompiler(config, processRunner);
        // One runner, so the note about a missing sandbox prints once
        TesterRunner testerRunner = new TesterRunner(config, processRunner);
        pipeline.addStage(new CompileStage(compiler, starterDirectory));
        pipeline.addStage(new ExecuteStage(testerRunner,
                new TesterOutputScoreParser(config)));
        addRandomInputStage(pipeline, compiler, testerRunner,
                starterDirectory);
    }

    // Adds the check that calls students' methods with random inputs,
    // when random.inputs.enabled is true and there are model answers to
    // compare with. Each pipeline gets new inputs, from a new seed.
    private void addRandomInputStage(GradingPipeline pipeline,
            SourceCompiler compiler, TesterRunner testerRunner,
            Path chosenStarter) {
        Path modelAnswers = ModelAnswerCopy.findFolder(config);
        boolean enabled = config.getBoolean(ConfigKeys.RANDOM_INPUTS_ENABLED);
        if (modelAnswers == null || !enabled) {
            return;
        }

        Path starterDirectory = chosenStarter;
        if (starterDirectory == null) {
            starterDirectory = Paths.get(
                    config.getString(ConfigKeys.STARTER_DIR));
        }
        long seed = System.nanoTime();
        RandomInputChecker checker = new RandomInputChecker(config,
                compiler, testerRunner, modelAnswers, starterDirectory, seed);
        pipeline.addStage(new RandomInputStage(checker));
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

    // The per-file checks that FixStage runs again after a move or rename.
    private ValidationRunner createFileCheckRunner() {
        ValidationRunner runner = new ValidationRunner();

        runner.addValidator(new FileNameValidator());
        runner.addValidator(new RequiredFilesValidator());
        runner.addValidator(new HeaderNameValidator(config));

        return runner;
    }

    // Lists the checks that also read the run's roster. To add one,
    // write a class that implements RosterCheck and add it here.
    private List<RosterCheck> createRosterChecks() {
        List<RosterCheck> rosterChecks = new ArrayList<>();
        rosterChecks.add(new CopiedHeaderDetector(config));
        return rosterChecks;
    }

    // Lists the detectors; a null starter means the one in the settings.
    private List<Detector> createDetectors(Path chosenStarter) {
        List<Detector> detectors = new ArrayList<>();
        Path starterDirectory = chosenStarter;
        if (starterDirectory == null) {
            starterDirectory = Paths.get(
                    config.getString(ConfigKeys.STARTER_DIR));
        }
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
