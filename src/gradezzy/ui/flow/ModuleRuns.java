/*
 * File: ModuleRuns.java
 *
 * Purpose: The graded runs of one module, newest first, for Compare
 * sections: the run in use of each section and, when the backend keeps
 * a history, every saved run of the module. It lists the assessments
 * that have a graded run and finds, for each section, the run of one
 * assessment to compare.
 *
 * Where it fits: StatisticsScreen builds one each time Compare sections
 * is shown, so a Lab Test 1 run of one section and a Lab Test 2 run of
 * another are never drawn on the same chart.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RunRecord;

/**
 * The graded runs of one module.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ModuleRuns {

    // The graded runs of the module, newest first
    private final List<GradingRun> runs = new ArrayList<>();

    // The graded run in use of each section, by section code
    private final Map<String, GradingRun> inUse = new HashMap<>();

    /**
     * Reads the graded runs of a module from the backend.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param backend where the runs come from
     * @param moduleCode the module, such as CS102
     * @param sectionCodes the sections of the module
     */
    public ModuleRuns(DashboardBackend backend, String moduleCode,
            List<String> sectionCodes) {
        for (String code : sectionCodes) {
            GradingRun run = backend.getRun(new Section(moduleCode, code));
            if (isUsable(run)) {
                inUse.put(code, run);
                runs.add(run);
            }
        }
        if (backend.supports(Capability.HISTORY)) {
            addSavedRuns(backend, moduleCode, sectionCodes);
        }
        Collections.sort(runs, new RunNewestFirstComparator());
    }

    /**
     * Lists the assessments that have at least one graded run, the one
     * graded most recently first.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @return the assessment names
     */
    public List<String> getAssessmentNames() {
        List<String> names = new ArrayList<>();
        for (GradingRun run : runs) {
            String name = run.getAssessment().getName();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Returns the run of one assessment to compare for a section: the
     * section's run in use when it is of that assessment, else its
     * newest graded run of that assessment.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param sectionCode the section, such as G4
     * @param assessmentName the assessment, such as Lab Test 2
     * @return the run, or null when the section has no graded run of
     *         that assessment
     */
    public GradingRun findRun(String sectionCode, String assessmentName) {
        GradingRun current = inUse.get(sectionCode);
        boolean currentFits = current != null
                && isOf(current, assessmentName);
        if (currentFits) {
            return current;
        }
        for (GradingRun run : runs) {
            boolean fits = sectionCode.equals(
                    run.getSection().getSectionCode())
                    && isOf(run, assessmentName);
            if (fits) {
                return run;
            }
        }
        return null;
    }

    // Adds the saved runs of the module's sections that are not already
    // listed.
    private void addSavedRuns(DashboardBackend backend, String moduleCode,
            List<String> sectionCodes) {
        for (RunRecord record : backend.listRuns()) {
            GradingRun run = record.getRun();
            boolean ofModule = isUsable(run)
                    && moduleCode.equals(run.getSection().getModuleCode())
                    && sectionCodes.contains(
                    run.getSection().getSectionCode());
            boolean notListed = ofModule && !contains(run);
            if (notListed) {
                runs.add(run);
            }
        }
    }

    // Says whether a run with the same id is already listed.
    private boolean contains(GradingRun run) {
        for (GradingRun listed : runs) {
            if (listed.getId().equals(run.getId())) {
                return true;
            }
        }
        return false;
    }

    // Says whether a run has marks, a section and an assessment.
    private static boolean isUsable(GradingRun run) {
        if (!StatisticsScreen.isGraded(run)) {
            return false;
        }
        boolean described = run.getSection() != null
                && run.getAssessment() != null;
        return described;
    }

    // Says whether a run was graded with the named assessment.
    private static boolean isOf(GradingRun run, String assessmentName) {
        return run.getAssessment().getName().equals(assessmentName);
    }
}
