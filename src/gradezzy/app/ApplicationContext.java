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
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.fix.CaseNameFixer;
import gradezzy.fix.EncodingFixer;
import gradezzy.fix.FixManager;
import gradezzy.fix.FolderHierarchyFixer;
import gradezzy.fix.FolderNameFixer;
import gradezzy.fix.HeaderNameFixer;
import gradezzy.persistence.BackupManager;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.plagiarism.TokenSimilarityDetector;
import gradezzy.report.AnomalyReportExporter;
import gradezzy.report.AuditLogExporter;
import gradezzy.report.CsvScoreSheetExporter;
import gradezzy.report.JsonExporter;
import gradezzy.report.JsonWriter;
import gradezzy.report.StudentReportExporter;
import gradezzy.service.AnomalyService;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.AuditService;
import gradezzy.service.ExportService;
import gradezzy.service.GradingService;
import gradezzy.service.HistoryService;
import gradezzy.service.PipelineFactory;
import gradezzy.service.PlagiarismService;
import gradezzy.service.ScoreAdjustmentService;
import gradezzy.service.SearchService;
import gradezzy.service.ServiceRegistry;
import gradezzy.service.SettingsService;
import gradezzy.service.StatisticsService;
import gradezzy.stats.StatisticsCalculator;
import gradezzy.util.TextUtils;

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

    // Why the database could not be opened, or empty when it is fine
    private final String startupProblem;

    /**
     * Builds every service from the configuration. Every setting is
     * checked first, so a bad value stops the app at once with a message
     * naming all the bad settings, rather than failing later when that
     * setting is first read.
     *
     * @param config the application settings
     * @throws ConfigException if a setting in config.properties is not
     *         valid
     */
    public ApplicationContext(AppConfig config) {
        checkSettings(config);
        this.config = config;
        this.repository = new SqliteResultRepository(
                config.getString(ConfigKeys.DATABASE_FILE));

        this.startupProblem = openRepository();
        FixManager fixManager = createFixManager();
        PipelineFactory pipelineFactory = new PipelineFactory(config,
                fixManager, repository);

        // One audit log for every service, so the Activity list shows
        // grading, fixes, settings and backups together
        AuditService auditService = new AuditService(config, repository);
        // Made first: Statistics also counts the runs of this session
        GradingService gradingService = new GradingService(config,
                pipelineFactory, new AssessmentFactory(config), repository,
                auditService);
        // Grading runs the similar code check once a batch is graded
        PlagiarismService plagiarismService = new PlagiarismService(config,
                new TokenSimilarityDetector(config), repository);
        gradingService.setPlagiarismService(plagiarismService);

        this.services = new ServiceRegistry(config, gradingService,
                new AnomalyService(fixManager, repository, auditService,
                        gradingService),
                createExportService(),
                new StatisticsService(new StatisticsCalculator(config),
                        repository, gradingService),
                new SearchService(config),
                auditService,
                new SettingsService(config, auditService),
                new HistoryService(config, repository,
                        new BackupManager(config), auditService),
                plagiarismService);
        services.setScoreAdjustmentService(new ScoreAdjustmentService(
                repository, config));
    }

    // Stops the app when config.properties holds a value that is not
    // valid, naming every such setting at once.
    private static void checkSettings(AppConfig config) {
        List<String> problems = new SettingsService(config).findProblems();
        if (problems.isEmpty()) {
            return;
        }
        throw new ConfigException("Fix these settings in "
                + config.getConfigFile() + ", then start GradeZZY again: "
                + TextUtils.join(" ", problems));
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
     * Returns why the database could not be opened at start-up.
     *
     * <p>Grading still works without the database, but nothing is saved,
     * so a screen should show this message when it is not empty.
     *
     * @return the problem, or an empty string when there is none
     */
    public String getStartupProblem() {
        return startupProblem;
    }

    // Opens the database when it is switched on. A failure is reported
    // on the error stream and returned, but does not stop the app: grading
    // still works, only saving does not.
    private String openRepository() {
        if (!config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return "";
        }

        try {
            repository.open();
            return "";
        } catch (PersistenceException exception) {
            String problem = "Results will not be saved: "
                    + exception.getMessage();
            System.err.println(problem);
            return problem;
        }
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

        fixManager.addFixer(new EncodingFixer());
        fixManager.addFixer(new FolderHierarchyFixer(config));
        fixManager.addFixer(new FolderNameFixer(config));
        fixManager.addFixer(new CaseNameFixer(config));
        fixManager.addFixer(new HeaderNameFixer(config));

        return fixManager;
    }

    // To add an export format, write a class that implements Exporter
    // and register it here.
    private ExportService createExportService() {
        ExportService exportService = new ExportService(config);

        exportService.registerExporter(new CsvScoreSheetExporter(config,
                Paths.get(config.getString(ConfigKeys.SCORE_SHEET_FILE))));
        exportService.registerExporter(new JsonExporter(new JsonWriter()));
        exportService.registerExporter(new AnomalyReportExporter());
        exportService.registerExporter(new StudentReportExporter());
        exportService.registerExporter(new AuditLogExporter(repository));

        return exportService;
    }
}
