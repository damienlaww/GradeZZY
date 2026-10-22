/*
 * File: ApplicationContext.java
 *
 * Purpose: Creates every long-lived object of the application once and
 * connects them to each other.
 *
 * Where it fits: Used by both entry points (ConsoleMain and the JavaFX
 * MainApp), so the console and the window run exactly the same grading
 * code. It is the one place to edit when a new Fixer or Exporter class is
 * added.
 */
package gradezzy.app;

import java.nio.file.Paths;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.fix.FixManager;
import gradezzy.fix.FolderHierarchyFixer;
import gradezzy.fix.FolderNameFixer;
import gradezzy.fix.HeaderNameFixer;
import gradezzy.persistence.BackupManager;
import gradezzy.persistence.ResultRepository;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.plagiarism.TokenSimilarityDetector;
import gradezzy.report.AnomalyReportExporter;
import gradezzy.report.CsvScoreSheetExporter;
import gradezzy.report.JsonExporter;
import gradezzy.report.JsonWriter;
import gradezzy.service.AnomalyService;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.AuditService;
import gradezzy.service.ExportService;
import gradezzy.service.GradingService;
import gradezzy.service.HistoryService;
import gradezzy.service.PipelineFactory;
import gradezzy.service.PlagiarismService;
import gradezzy.service.SearchService;
import gradezzy.service.ServiceRegistry;
import gradezzy.service.SettingsService;
import gradezzy.service.StatisticsService;
import gradezzy.stats.StatisticsCalculator;

/**
 * Creates and connects the services of the application.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ApplicationContext {

    // The application settings
    private final AppConfig config;

    // The store results are saved to
    private final ResultRepository repository;

    // Every service, ready for the user interface
    private final ServiceRegistry services;

    /**
     * Builds every service from the configuration.
     *
     * @param config the application settings
     */
    public ApplicationContext(AppConfig config) {
        this.config = config;
        this.repository = new SqliteResultRepository(
                config.getString(ConfigKeys.DATABASE_FILE));

        // TODO(Huxley): implement: call repository.open() here when
        //     DATABASE_ENABLED is true, and report a failure to the user
        FixManager fixManager = createFixManager();
        PipelineFactory pipelineFactory = new PipelineFactory(config,
                fixManager, repository);

        this.services = new ServiceRegistry(config,
                new GradingService(config, pipelineFactory,
                        new AssessmentFactory(config)),
                new AnomalyService(fixManager, repository),
                createExportService(),
                new StatisticsService(new StatisticsCalculator(config),
                        repository),
                new SearchService(config),
                new AuditService(config, repository),
                new SettingsService(config),
                new HistoryService(config, repository,
                        new BackupManager(config)),
                new PlagiarismService(
                        new TokenSimilarityDetector(config)));
    }

    /**
     * Returns the application settings.
     *
     * @return the application settings
     */
    public AppConfig getConfig() {
        return config;
    }

    /**
     * Returns every service of the application.
     *
     * @return the service registry
     */
    public ServiceRegistry getServices() {
        return services;
    }

    /**
     * Releases what the application holds open, such as the database.
     * Call this once when the application ends.
     */
    public void close() {
        repository.close();
    }

    // To add a fixer, write a class that extends AbstractFixer and add
    // it here.
    private FixManager createFixManager() {
        FixManager fixManager = new FixManager();

        fixManager.addFixer(new FolderHierarchyFixer());
        fixManager.addFixer(new FolderNameFixer());
        fixManager.addFixer(new HeaderNameFixer(config));

        return fixManager;
    }

    // To add an export format, write a class that implements Exporter
    // and register it here.
    private ExportService createExportService() {
        ExportService exportService = new ExportService();

        exportService.registerExporter(new CsvScoreSheetExporter(config,
                Paths.get(config.getString(ConfigKeys.SCORE_SHEET_FILE))));
        exportService.registerExporter(new JsonExporter(new JsonWriter()));
        exportService.registerExporter(new AnomalyReportExporter());

        return exportService;
    }
}
