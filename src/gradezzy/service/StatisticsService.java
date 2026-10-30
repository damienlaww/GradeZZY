/*
 * File: StatisticsService.java
 *
 * Purpose: Loads saved runs and summarises them for the Statistics tab.
 *
 * Where it fits: Sits between the Statistics tab and StatisticsCalculator.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
import gradezzy.stats.StatisticsCalculator;
import gradezzy.stats.StatisticsFilter;
import gradezzy.stats.StatisticsSummary;

/**
 * Loads saved runs and summarises them for the Statistics tab.
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
     * @return the summary for the Statistics tab; it holds empty values when
     *         no saved run matches or the database cannot be read
     */
    public StatisticsSummary summarise(StatisticsFilter filter) {
        List<GradingRun> runs = new ArrayList<>();

        if (repository != null) {
            try {
                runs = repository.listRuns();
            } catch (PersistenceException exception) {
                // An empty summary is shown instead
            }
        }

        return statisticsCalculator.summarise(runs, filter);
    }
}
