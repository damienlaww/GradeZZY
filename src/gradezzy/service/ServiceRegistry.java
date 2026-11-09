/*
 * File: ServiceRegistry.java
 *
 * Purpose: Holds one instance of every service.
 *
 * Where it fits: Built once by ApplicationContext and handed to the
 * backend, so the screens never create services themselves.
 */
package gradezzy.service;

import gradezzy.config.AppConfig;

/**
 * Holds one instance of every service.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ServiceRegistry {

    // The application settings
    private final AppConfig config;

    // The grading service
    private final GradingService gradingService;

    // The anomaly service
    private final AnomalyService anomalyService;

    // The export service
    private final ExportService exportService;

    // The statistics service
    private final StatisticsService statisticsService;

    // The audit service
    private final AuditService auditService;

    // The settings service
    private final SettingsService settingsService;

    // The history service
    private final HistoryService historyService;

    // The plagiarism service
    private final PlagiarismService plagiarismService;

    // The saved assessments, built from the settings service
    private final AssessmentLibrary assessmentLibrary;

    // Changes marks by hand; set after construction
    private ScoreAdjustmentService scoreAdjustmentService = null;

    // Keeps the names and checks of answer groups; set after construction
    private AnswerGroupService answerGroupService = null;

    /**
     * Creates a new service registry.
     *
     * @param config the application settings
     * @param gradingService the grading service
     * @param anomalyService the anomaly service
     * @param exportService the export service
     * @param statisticsService the statistics service
     * @param auditService the audit service
     * @param settingsService the settings service
     * @param historyService the history service
     * @param plagiarismService the plagiarism service
     */
    public ServiceRegistry(AppConfig config, GradingService gradingService,
            AnomalyService anomalyService, ExportService exportService,
            StatisticsService statisticsService, AuditService auditService,
            SettingsService settingsService, HistoryService historyService,
            PlagiarismService plagiarismService) {
        this.config = config;
        this.gradingService = gradingService;
        this.anomalyService = anomalyService;
        this.exportService = exportService;
        this.statisticsService = statisticsService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.historyService = historyService;
        this.plagiarismService = plagiarismService;
        this.assessmentLibrary = new AssessmentLibrary(config,
                settingsService);
    }

    /**
     * Gives the registry the service that changes marks by hand. It is
     * set after construction because it needs the result store.
     *
     * @param service the service
     */
    public void setScoreAdjustmentService(ScoreAdjustmentService service) {
        scoreAdjustmentService = service;
    }

    /**
     * Returns the service that changes marks by hand.
     *
     * @return the service; one that keeps changes for this session only
     *         when none was set
     */
    public ScoreAdjustmentService getScoreAdjustmentService() {
        if (scoreAdjustmentService == null) {
            scoreAdjustmentService = new ScoreAdjustmentService(null,
                    config);
        }
        return scoreAdjustmentService;
    }

    /**
     * Gives the registry the service that keeps the names and checks of
     * answer groups. It is set after construction because it needs the
     * result store.
     *
     * <p>Tier: COULD.
     *
     * @param service the service
     */
    public void setAnswerGroupService(AnswerGroupService service) {
        answerGroupService = service;
    }

    /**
     * Returns the service that keeps the names and checks of answer
     * groups.
     *
     * <p>Tier: COULD.
     *
     * @return the service; one that keeps them for this session only
     *         when none was set
     */
    public AnswerGroupService getAnswerGroupService() {
        if (answerGroupService == null) {
            answerGroupService = new AnswerGroupService(null, config);
        }
        return answerGroupService;
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
     * Returns the grading service.
     *
     * @return the grading service
     */
    public GradingService getGradingService() {
        return gradingService;
    }

    /**
     * Returns the anomaly service.
     *
     * @return the anomaly service
     */
    public AnomalyService getAnomalyService() {
        return anomalyService;
    }

    /**
     * Returns the export service.
     *
     * @return the export service
     */
    public ExportService getExportService() {
        return exportService;
    }

    /**
     * Returns the statistics service.
     *
     * @return the statistics service
     */
    public StatisticsService getStatisticsService() {
        return statisticsService;
    }

    /**
     * Returns the audit service.
     *
     * @return the audit service
     */
    public AuditService getAuditService() {
        return auditService;
    }

    /**
     * Returns the settings service.
     *
     * @return the settings service
     */
    public SettingsService getSettingsService() {
        return settingsService;
    }

    /**
     * Returns the history service.
     *
     * @return the history service
     */
    public HistoryService getHistoryService() {
        return historyService;
    }

    /**
     * Returns the plagiarism service.
     *
     * @return the plagiarism service
     */
    public PlagiarismService getPlagiarismService() {
        return plagiarismService;
    }

    /**
     * Returns the saved assessments.
     *
     * @return the assessment library
     */
    public AssessmentLibrary getAssessmentLibrary() {
        return assessmentLibrary;
    }
}
