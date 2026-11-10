/*
 * File: StatisticsService.java
 *
 * Purpose: Loads saved runs and this session's runs and summarises them
 * for the Statistics tab.
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

    // The service that knows this session's runs, or null for saved runs
    // only
    private final GradingService gradingService;

    /**
     * Creates a new statistics service that also counts the runs of this
     * session.
     *
     * <p>Tier: SHOULD.
     *
     * @param statisticsCalculator the helper that does the calculations
     * @param repository the store results are saved to
     * @param gradingService the service that knows this session's runs,
     *        or null for saved runs only
     */
    public StatisticsService(StatisticsCalculator statisticsCalculator,
            ResultRepository repository, GradingService gradingService) {
        this.statisticsCalculator = statisticsCalculator;
        this.repository = repository;
        this.gradingService = gradingService;
    }

    /**
     * Summarises the runs that match a filter: the saved runs, plus the
     * runs of this session. A run of this session replaces its saved copy,
     * because it holds the newest results, and a run that could not be
     * saved (or any run while the database is off) is still counted.
     *
     * <p>Tier: SHOULD.
     *
     * @param filter the filter
     * @return the summary for the Statistics tab; it holds empty values when
     *         no run matches
     */
    public StatisticsSummary summarise(StatisticsFilter filter) {
        List<GradingRun> runs = new ArrayList<>();

        if (repository != null) {
            try {
                // Copied, so the saved copies can be replaced below
                runs = new ArrayList<>(repository.listRuns());
            } catch (PersistenceException exception) {
                // Only the runs of this session are shown
            }
        }

        if (gradingService != null) {
            for (GradingRun sessionRun : gradingService.getSessionRuns()) {
                replaceOrAdd(runs, sessionRun);
            }
        }

        return statisticsCalculator.summarise(runs, filter);
    }

    // Puts a run in place of the run with the same id, or adds it.
    private void replaceOrAdd(List<GradingRun> runs, GradingRun run) {
        for (int index = 0; index < runs.size(); index++) {
            if (runs.get(index).getId().equals(run.getId())) {
                runs.set(index, run);
                return;
            }
        }
        runs.add(run);
    }
}
