/*
 * File: ActionResult.java
 *
 * Purpose: Tells a screen how a backend action went.
 *
 * Where it fits: Returned by the DashboardBackend methods that change
 * something. A screen shows the message, or a Not connected yet panel.
 */
package gradezzy.ui.backend;

import java.nio.file.Path;

/**
 * The outcome of one backend action.
 */
public class ActionResult {

    // What happened
    private final ActionOutcome outcome;

    // A plain sentence for the instructor
    private final String message;

    // The missing feature, set only for NOT_CONNECTED
    private final Capability capability;

    // Preview text, or an empty string
    private final String preview;

    // The file that was written, or null
    private final Path path;

    // Creates a result; the factory methods call it.
    private ActionResult(ActionOutcome outcome, String message,
            Capability capability, String preview, Path path) {
        this.outcome = outcome;
        this.message = message;
        this.capability = capability;
        this.preview = preview;
        this.path = path;
    }

    /**
     * Creates a result for an action that happened.
     *
     * @param message a plain sentence in the past tense
     * @return the result
     */
    public static ActionResult done(String message) {
        return new ActionResult(ActionOutcome.DONE, message, null, "", null);
    }

    /**
     * Creates a result for an action that made a preview, not a file.
     *
     * @param message a plain sentence in the past tense
     * @param preview the text to show
     * @return the result
     */
    public static ActionResult preview(String message, String preview) {
        return new ActionResult(ActionOutcome.DONE, message, null, preview,
                null);
    }

    /**
     * Creates a result for an action that wrote a file.
     *
     * @param message a plain sentence in the past tense
     * @param path the file that was written
     * @return the result
     */
    public static ActionResult saved(String message, Path path) {
        return new ActionResult(ActionOutcome.DONE, message, null, "", path);
    }

    /**
     * Creates a result for an action no backend provides yet.
     *
     * @param capability the missing feature
     * @return the result
     */
    public static ActionResult notConnected(Capability capability) {
        return new ActionResult(ActionOutcome.NOT_CONNECTED,
                capability.getTitle() + " is not connected yet.",
                capability, "", null);
    }

    /**
     * Creates a result for an action that did not work.
     *
     * @param message what went wrong and what to do next
     * @return the result
     */
    public static ActionResult failed(String message) {
        return new ActionResult(ActionOutcome.FAILED, message, null, "", null);
    }

    /**
     * Returns what happened.
     *
     * @return the outcome
     */
    public ActionOutcome getOutcome() {
        return outcome;
    }

    /**
     * Says whether the action happened.
     *
     * @return true when the outcome is DONE
     */
    public boolean isDone() {
        return outcome == ActionOutcome.DONE;
    }

    /**
     * Returns the plain sentence for the instructor.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the missing feature.
     *
     * @return the capability, or null unless the outcome is NOT_CONNECTED
     */
    public Capability getCapability() {
        return capability;
    }

    /**
     * Returns the preview text.
     *
     * @return the preview, or an empty string
     */
    public String getPreview() {
        return preview;
    }

    /**
     * Returns the file that was written.
     *
     * @return the path, or null
     */
    public Path getPath() {
        return path;
    }
}
