/*
 * File: ServiceRegistry.java
 *
 * Purpose: Holds one instance of every service.
 *
 * Where it fits: Built once by ApplicationContext and handed to each UI
 * controller, so controllers never create services themselves.
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

    // The search service
    private final SearchService searchService;

    // The audit service
    private final AuditService auditService;

    // The settings service
    private final SettingsService settingsService;

    // The history service
    private final HistoryService historyService;

    // The plagiarism service
    private final PlagiarismService plagiarismService;

    /**
     * Creates a new service registry.
     *
     * @param config the application settings
     * @param gradingService the grading service
     * @param anomalyService the anomaly service
     * @param exportService the export service
     * @param statisticsService the statistics service
     * @param searchService the search service
     * @param auditService the audit service
     * @param settingsService the settings service
     * @param historyService the history service
     * @param plagiarismService the plagiarism service
     */
    public ServiceRegistry(AppConfig config, GradingService gradingService,
            AnomalyService anomalyService, ExportService exportService,
            StatisticsService statisticsService, SearchService searchService,
            AuditService auditService, SettingsService settingsService,
            HistoryService historyService,
            PlagiarismService plagiarismService) {
        this.config = config;
        this.gradingService = gradingService;
        this.anomalyService = anomalyService;
        this.exportService = exportService;
        this.statisticsService = statisticsService;
        this.searchService = searchService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.historyService = historyService;
        this.plagiarismService = plagiarismService;
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
     * Returns the search service.
     *
     * @return the search service
     */
    public SearchService getSearchService() {
        return searchService;
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
}
