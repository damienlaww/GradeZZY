/*
 * File: ZeroChecks.java
 *
 * Purpose: Remembers which students graded with 0 marks the instructor
 * has looked at. A 0 that nothing flagged needs a look before export;
 * once checked, the student counts as graded.
 *
 * Where it fits: DashboardBackend's isZeroChecked and setZeroChecked use
 * it, so Live and Demo work the same. The demo keeps a check until
 * GradeZZY closes; LiveBackend also saves it with the run and marks the
 * students again when it reads the saved runs. A student graded again
 * alone keeps the same submission, so the check stays until it is taken
 * back.
 */
package gradezzy.ui.backend;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import gradezzy.model.Submission;

/**
 * The 0-mark students marked as checked.
 */
final class ZeroChecks {

    // The checked submissions, by identity; a run no longer shown is
    // forgotten by itself
    private static final Set<Submission> CHECKED =
            Collections.synchronizedSet(Collections.newSetFromMap(
            new WeakHashMap<>()));

    private ZeroChecks() {
    }

    // Says whether a student's 0 was checked.
    static boolean isChecked(Submission submission) {
        return submission != null && CHECKED.contains(submission);
    }

    // Marks a student's 0 as checked or takes it back; true when it
    // changed.
    static boolean set(Submission submission, boolean checked) {
        if (checked) {
            return CHECKED.add(submission);
        }
        return CHECKED.remove(submission);
    }
}
