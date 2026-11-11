/*
 * File: FixOnlyPipelineFactory.java
 *
 * Purpose: A pipeline factory whose pipeline unpacks the uploads, checks
 * them and fixes them, and runs no code.
 *
 * Where it fits: Used by ServiceTest, so fixes can be made, approved and
 * undone through GradingService without compiling or running student
 * code.
 */
import java.nio.file.Path;

import gradezzy.config.AppConfig;
import gradezzy.fix.FixManager;
import gradezzy.grading.CancellationToken;
import gradezzy.grading.ExtractStage;
import gradezzy.grading.FixStage;
import gradezzy.grading.GradingPipeline;
import gradezzy.grading.ValidateStage;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.ingest.ZipExtractor;
import gradezzy.service.PipelineFactory;
import gradezzy.validation.FileNameValidator;
import gradezzy.validation.HeaderNameValidator;
import gradezzy.validation.RequiredFilesValidator;
import gradezzy.validation.StructureValidator;
import gradezzy.validation.ValidationRunner;

/**
 * Builds pipelines that unpack, check and fix, and run no code.
 */
public class FixOnlyPipelineFactory extends PipelineFactory {

    // The settings the stages read
    private final AppConfig config;

    // The fixes the fix stage makes
    private final FixManager fixManager;

    /**
     * Creates a factory for these settings and fixes, with no result
     * store.
     *
     * @param config the application settings
     * @param fixManager the fixes the fix stage makes
     */
    public FixOnlyPipelineFactory(AppConfig config, FixManager fixManager) {
        super(config, fixManager, null);
        this.config = config;
        this.fixManager = fixManager;
    }

    /**
     * Makes the pipeline with no starter folder.
     *
     * @param token stops the pipeline when cancelled
     * @return the pipeline
     */
    @Override
    public GradingPipeline createPipeline(CancellationToken token) {
        return createPipeline(token, null);
    }

    /**
     * Makes the pipeline: unpack, check, then fix.
     *
     * @param token stops the pipeline when cancelled
     * @param starterDirectory the starter code folder, or null
     * @return the pipeline
     */
    @Override
    public GradingPipeline createPipeline(CancellationToken token,
            Path starterDirectory) {
        GradingPipeline pipeline = new GradingPipeline(token);
        WorkingCopyManager copies = new WorkingCopyManager(config,
                new ZipExtractor(config));
        pipeline.addStage(new ExtractStage(copies,
                new RosterReader(config)));
        ValidationRunner all = new ValidationRunner();
        all.addValidator(new StructureValidator());
        all.addValidator(new FileNameValidator());
        all.addValidator(new RequiredFilesValidator());
        all.addValidator(new HeaderNameValidator(config));
        pipeline.addStage(new ValidateStage(all));
        ValidationRunner files = new ValidationRunner();
        files.addValidator(new FileNameValidator());
        files.addValidator(new RequiredFilesValidator());
        files.addValidator(new HeaderNameValidator(config));
        pipeline.addStage(new FixStage(fixManager, files, copies,
                starterDirectory));
        return pipeline;
    }
}
