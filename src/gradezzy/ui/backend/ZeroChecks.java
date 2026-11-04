/*
 * File: ZeroChecks.java
 *
 * Purpose: Remembers which students graded with 0 marks the instructor
 * has looked at. A 0 that nothing flagged needs a look before export;
 * once checked, the student counts as graded.
 *
 * Where it fits: DashboardBackend's isZeroChecked and setZeroChecked use
 * it, so Live and Demo work the same. Like review decisions, it lasts
 * until GradeZZY closes. A student graded again is a new submission, so
 * a new 0 waits for a look again.
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
        return checked ? CHECKED.add(submission)
                : CHECKED.remove(submission);
    }
}
