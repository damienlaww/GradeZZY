/*
 * File: ScoreAdjustment.java
 *
 * Purpose: One mark changed by hand, with the reason and the earlier mark.
 *
 * Where it fits: Created by ScoreAdjustmentService when a mark is
 * changed, by AdjustmentTable when saved changes are read back and by
 * the demo, and listed in the student panel on Review.
 */
package gradezzy.model;

import java.time.LocalDateTime;

/**
 * A mark changed by hand.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
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
     * <p>Tier: SHOULD.
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
     * <p>Tier: SHOULD.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the question whose mark changed.
     *
     * <p>Tier: SHOULD.
     *
     * @return the question id
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the mark before the change.
     *
     * <p>Tier: SHOULD.
     *
     * @return the earlier score
     */
    public double getPreviousScore() {
        return previousScore;
    }

    /**
     * Returns the mark after the change.
     *
     * <p>Tier: SHOULD.
     *
     * @return the new score
     */
    public double getNewScore() {
        return newScore;
    }

    /**
     * Returns why the instructor changed the mark.
     *
     * <p>Tier: SHOULD.
     *
     * @return the reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns when the change was saved.
     *
     * <p>Tier: SHOULD.
     *
     * @return the time
     */
    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}
