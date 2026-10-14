/*
 * File: StatisticsService.java
 *
 * Purpose: Loads saved runs and summarises them for the dashboard.
 *
 * Where it fits: Sits between the statistics screen and StatisticsCalculator.
 */
package gradezzy.service;

import gradezzy.persistence.ResultRepository;
import gradezzy.stats.StatisticsCalculator;
import gradezzy.stats.StatisticsFilter;
import gradezzy.stats.StatisticsSummary;

/**
 * Loads saved runs and summarises them for the dashboard.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class StatisticsService {

    // The helper that does the calculations
    private final StatisticsCalculator statisticsCalculator;

    // The store results are saved to
    private final ResultRepository repository;

    /**
     * Creates a new statistics service.
     *
     * @param statisticsCalculator the helper that does the calculations
     * @param repository the store results are saved to
     */
    public StatisticsService(StatisticsCalculator statisticsCalculator,
            ResultRepository repository) {
        this.statisticsCalculator = statisticsCalculator;
        this.repository = repository;
    }

    /**
     * Summarises the saved runs that match a filter.
     *
     * @param filter the filter
     * @return the summary for the dashboard
     */
    public StatisticsSummary summarise(StatisticsFilter filter) {
        // TODO(Jing Yuan): implement: loading the runs and calling
        //     statisticsCalculator.summarise
        return null;
    }
}
