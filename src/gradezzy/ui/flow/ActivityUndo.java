/*
 * File: ActivityUndo.java
 *
 * Purpose: Remembers how to take back each delete made since GradeZZY
 * opened, keyed by its line in the activity log, so the Activity view
 * can offer Undo for it.
 *
 * Where it fits: The deletes of sections, modules, export folders and
 * assessments log a line and register how to put the item back; History
 * › Activity shows Undo while the item has not been put back yet.
 */
package gradezzy.ui.flow;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The deletes that can still be taken back, until GradeZZY closes.
 */
public final class ActivityUndo {

    /** The log's words for a section that was deleted. */
    public static final String SECTION_DELETED = "Section deleted";

    /** The log's words for a module that was deleted. */
    public static final String MODULE_DELETED = "Module deleted";

    /** The log's words for an assessment that was deleted. */
    public static final String ASSESSMENT_DELETED = "Assessment deleted";

    /** The log's words for an export folder that was removed. */
    public static final String FOLDER_REMOVED = "Export folder removed";

    /** The log's words for a mark changed by hand. */
    public static final String MARK_CHANGED = "Mark changed";

    /** The log's words for a changed mark taken back on Review. */
    public static final String MARK_CHANGE_UNDONE = "Mark change undone";

    /** The log's words for anything put back. */
    public static final String RESTORED = "Restored";

    // How to put back each delete, by its action and detail
    private static final Map<String, Supplier<ActionResult>> RESTORES =
            new HashMap<>();

    // Not used: every method is static.
    private ActivityUndo() {
    }

    /**
     * Logs a delete and remembers how to put it back.
     *
     * @param backend the backend that keeps the log
     * @param action what was deleted, such as SECTION_DELETED
     * @param detail which one, such as "G2 of CS102"
     * @param restore what puts it back, saying whether it worked
     * @return what puts it back; once it works, it logs it and forgets
     *         the delete, and if it fails, Undo stays offered. Use it for
     *         the notice's Undo too.
     */
    public static Supplier<ActionResult> record(DashboardBackend backend,
            String action, String detail, Supplier<ActionResult> restore) {
        backend.recordActivity(action, detail);
        String key = keyOf(action, detail);
        Supplier<ActionResult> once = () -> {
            if (!RESTORES.containsKey(key)) {
                return ActionResult.failed("This was already put back.");
            }
            ActionResult result = restore.get();
            if (result == null) {
                result = ActionResult.failed("It could not be put back.");
            }
            if (result.isDone()) {
                RESTORES.remove(key);
                backend.recordActivity(RESTORED, detail);
            }
            return result;
        };
        RESTORES.put(key, once);
        return once;
    }

    /**
     * Forgets how to put back a change, once it was taken back another
     * way, so History no longer offers Undo for it.
     *
     * @param action the log line's action
     * @param detail the log line's detail
     */
    public static void forget(String action, String detail) {
        RESTORES.remove(keyOf(action, detail));
    }

    /**
     * Returns what puts back a logged delete, or null when it cannot be
     * put back any more, such as after GradeZZY was closed or once it was
     * put back.
     *
     * @param action the log line's action
     * @param detail the log line's detail
     * @return the restore, or null
     */
    public static Supplier<ActionResult> find(String action, String detail) {
        return RESTORES.get(keyOf(action, detail));
    }

    // The key of a delete in the map.
    private static String keyOf(String action, String detail) {
        return action + "\n" + detail;
    }
}
