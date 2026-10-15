/*
 * File: ScoreAdjustment.java
 *
 * Purpose: One mark changed by hand, with the reason and the earlier
 * score so it can be undone.
 *
 * Where it fits: Created by DashboardBackend.adjustScore and shown on the
 * History tab of the Student Evidence screen.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import java.time.LocalDateTime;

/**
 * A mark changed by hand.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class ScoreAdjustment {

    // The student whose mark changed
    private final String username;

    // The question whose mark changed
    private final String questionId;

    // The mark before the change
    private final double previousScore;

    // The mark after the change
    private final double newScore;

    // Why the instructor changed it
    private final String reason;

    // When the change was saved
    private final LocalDateTime savedAt;

    /**
     * Creates a score adjustment.
     *
     * @param username the student whose mark changed
     * @param questionId the question whose mark changed
     * @param previousScore the mark before the change
     * @param newScore the mark after the change
     * @param reason why the instructor changed it
     * @param savedAt when the change was saved
     */
    public ScoreAdjustment(String username, String questionId,
            double previousScore, double newScore, String reason,
            LocalDateTime savedAt) {
        this.username = username;
        this.questionId = questionId;
        this.previousScore = previousScore;
        this.newScore = newScore;
        this.reason = reason;
        this.savedAt = savedAt;
    }

    /**
     * Returns the student whose mark changed.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the question whose mark changed.
     *
     * @return the question id
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the mark before the change.
     *
     * @return the earlier score
     */
    public double getPreviousScore() {
        return previousScore;
    }

    /**
     * Returns the mark after the change.
     *
     * @return the new score
     */
    public double getNewScore() {
        return newScore;
    }

    /**
     * Returns why the instructor changed the mark.
     *
     * @return the reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns when the change was saved.
     *
     * @return the time
     */
    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}
