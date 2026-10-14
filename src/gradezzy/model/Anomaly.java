/*
 * File: Anomaly.java
 *
 * Purpose: One problem found in one submission.
 *
 * Where it fits: The common currency of the anomaly log: every validator,
 * fixer, detector and failed stage reports through this class.
 */
package gradezzy.model;

/**
 * One problem found in one submission.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Anomaly {

    // The unique id of this anomaly
    private final String id;

    // The kind of problem
    private final AnomalyType type;

    // How serious the problem is
    private final Severity severity;

    // The username of the affected submission
    private final String username;

    // A plain description of what is wrong
    private final String message;

    // What the instructor can do about it
    private final String suggestedAction;

    // The state of the automatic fix
    private FixStatus fixStatus = FixStatus.NOT_FIXABLE;

    /**
     * Creates a new anomaly.
     *
     * @param id the unique id of this anomaly
     * @param type the kind of problem
     * @param severity how serious the problem is
     * @param username the username of the affected submission
     * @param message a plain description of what is wrong
     * @param suggestedAction what the instructor can do about it
     */
    public Anomaly(String id, AnomalyType type, Severity severity,
            String username, String message, String suggestedAction) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.username = username;
        this.message = message;
        this.suggestedAction = suggestedAction;
    }

    /**
     * Returns the unique id of this anomaly.
     *
     * @return the unique id of this anomaly
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the kind of problem.
     *
     * @return the kind of problem
     */
    public AnomalyType getType() {
        return type;
    }

    /**
     * Returns how serious the problem is.
     *
     * @return how serious the problem is
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Returns the username of the affected submission.
     *
     * @return the username of the affected submission
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns a plain description of what is wrong.
     *
     * @return a plain description of what is wrong
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns what the instructor can do about it.
     *
     * @return what the instructor can do about it
     */
    public String getSuggestedAction() {
        return suggestedAction;
    }

    /**
     * Returns the state of the automatic fix.
     *
     * @return the state of the automatic fix
     */
    public FixStatus getFixStatus() {
        return fixStatus;
    }

    /**
     * Sets the state of the automatic fix.
     *
     * @param fixStatus the state of the automatic fix
     */
    public void setFixStatus(FixStatus fixStatus) {
        this.fixStatus = fixStatus;
    }
}
