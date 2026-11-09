/*
 * File: AnswerGroupSource.java
 *
 * Purpose: Where an exporter gets a run's answer groups, with the
 * names the marker gave the mistakes.
 *
 * Where it fits: AnswerGroupService implements it; ApplicationContext
 * hands it to CommonMistakesExporter and JsonExporter, so the report
 * package does not depend on the service package.
 */
package gradezzy.report;

import java.util.List;

import gradezzy.model.AnswerGroup;
import gradezzy.model.GradingRun;

/**
 * Lists the answer groups of a run.
 *
 * <p>Tier: COULD.
 */
public interface AnswerGroupSource {

    /**
     * Lists the answer groups of a run, with names and checks.
     *
     * <p>Tier: COULD.
     *
     * @param run the run
     * @return the groups, in question order, largest first
     */
    List<AnswerGroup> listGroups(GradingRun run);
}
