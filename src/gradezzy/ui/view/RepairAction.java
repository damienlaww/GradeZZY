/*
 * File: RepairAction.java
 *
 * Purpose: The three decisions the instructor can make about a repair of
 * a working copy: approve it, reject it (which undoes it) or undo the
 * decision. Each knows which repairs it applies to, the backend call
 * behind it and how to describe itself.
 *
 * Where it fits: The Validation screen applies these to the selected
 * findings or to every decided repair of the run. Findings with no
 * automatic repair never take an action. The original ZIP file is never
 * changed.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.FixStatus;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.component.Labels;

/**
 * A decision about a repair.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public enum RepairAction {

    /** Approves a repair of the working copy. */
    APPROVE("Repair approved", Capability.REPAIR_DECISIONS,
            "The selected repairs are already approved."),

    /** Rejects a repair, which undoes it on the working copy. */
    REJECT("Repair rejected", Capability.REPAIR_DECISIONS,
            "The selected repairs are already rejected and undone."),

    /** Returns a decided repair to Applied. */
    UNDO("Repair decision undone", Capability.REPAIR_DECISION_UNDO,
            "The selected repairs have no decision to undo yet.");

    // The short name recorded in the audit log
    private final String auditName;

    // The backend feature the action needs
    private final Capability capability;

    // Why the action does nothing when every selected repair has it
    private final String alreadyDone;

    // Creates an action constant.
    RepairAction(String auditName, Capability capability,
            String alreadyDone) {
        this.auditName = auditName;
        this.capability = capability;
        this.alreadyDone = alreadyDone;
    }

    /**
     * Returns the short name recorded in the audit log.
     *
     * @return the audit name
     */
    public String getAuditName() {
        return auditName;
    }

    /**
     * Returns the backend feature the action needs.
     *
     * @return the capability
     */
    public Capability getCapability() {
        return capability;
    }

    /**
     * Says whether the action can change a repair.
     *
     * @param repair the finding and its repair
     * @return true when the repair exists and does not have this
     *         decision yet
     */
    public boolean appliesTo(RepairItem repair) {
        if (repair.isBlocked()) {
            return false;
        }
        FixStatus status = repair.getAnomaly().getFixStatus();
        if (this == APPROVE) {
            return status != FixStatus.APPROVED;
        } else if (this == REJECT) {
            return status != FixStatus.UNDONE;
        }
        return ReviewRules.isRepairSettled(repair.getAnomaly());
    }

    /**
     * Keeps the repairs the action can change.
     *
     * @param repairs the findings
     * @return the findings the action applies to, in order
     */
    public List<RepairItem> keep(List<RepairItem> repairs) {
        List<RepairItem> result = new ArrayList<>();
        for (RepairItem repair : repairs) {
            if (appliesTo(repair)) {
                result.add(repair);
            }
        }
        return result;
    }

    /**
     * Calls the backend method behind the action for one repair.
     *
     * @param backend the backend
     * @param repair the finding and its repair
     * @return what the backend answered
     */
    public ActionResult apply(DashboardBackend backend, RepairItem repair) {
        if (this == APPROVE) {
            return backend.approveRepair(repair);
        } else if (this == REJECT) {
            return backend.rejectRepair(repair);
        }
        return backend.undoRepairDecision(repair);
    }

    /**
     * Describes the action on some repairs, in the past tense.
     *
     * @param count how many repairs changed
     * @return the message
     */
    public String describe(int count) {
        String what = count + " repair" + Labels.plural(count);
        if (this == APPROVE) {
            return "Approved " + what + ". The original ZIP files "
                    + "were not changed.";
        } else if (this == REJECT) {
            return "Rejected " + what + " and put the working copies "
                    + "back. The original ZIP files were not changed.";
        }
        return "Returned " + what + " to Applied, waiting for review.";
    }

    /**
     * Explains why none of the selected findings can take the action.
     *
     * @param selected the selected findings
     * @return the explanation
     */
    public String explainNothingToDo(List<RepairItem> selected) {
        if (selected.isEmpty()) {
            return "Select one or more findings first.";
        }
        for (RepairItem repair : selected) {
            if (!repair.isBlocked()) {
                return alreadyDone;
            }
        }
        return "No automatic repair exists for the selected findings. "
                + "GradeZZY never writes a missing answer or renames files "
                + "it cannot match, so handle them by hand.";
    }
}
