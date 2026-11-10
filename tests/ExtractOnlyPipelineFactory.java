/*
 * File: ExtractOnlyPipelineFactory.java
 *
 * Purpose: A pipeline factory whose pipeline only unpacks the uploads.
 *
 * Where it fits: Used by IngestRegressionTest, so ingestion can be graded
 * through GradingService without compiling or running student code.
 */
import gradezzy.config.AppConfig;
import gradezzy.grading.CancellationToken;
import gradezzy.grading.ExtractStage;
import gradezzy.grading.GradingPipeline;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.ingest.ZipExtractor;
import gradezzy.service.PipelineFactory;

/**
 * Builds pipelines that hold only the extraction stage.
 */
public class ExtractOnlyPipelineFactory extends PipelineFactory {

    // The settings the extraction stage reads
    private final AppConfig config;

    /**
     * Creates a factory with no fix manager and no result store.
     *
     * @param config the application settings
     */
    public ExtractOnlyPipelineFactory(AppConfig config) {
        super(config, null, null);
        this.config = config;
    }

    /**
     * Builds a pipeline that only unpacks the uploads.
     *
     * @param token the flag that stops the batch when set
     * @return the pipeline, ready to run
     */
    @Override
    public GradingPipeline createPipeline(CancellationToken token) {
        GradingPipeline pipeline = new GradingPipeline(token);
        pipeline.addStage(new ExtractStage(new WorkingCopyManager(config,
                new ZipExtractor(config)), new RosterReader(config)));
        return pipeline;
    }
}
