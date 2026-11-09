/*
 * File: AnswerGroupNote.java
 *
 * Purpose: What the marker decided about one answer group: the name of
 * the mistake and the students who were in it when it was checked.
 *
 * Where it fits: AnswerGroupService keeps one per group it has a
 * decision for, and ResultRepository saves it with the run, keyed by
 * the question and pattern, so it is there after a restart.
 */
package gradezzy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The marker's name and check for one answer group.
 *
 * <p>Tier: COULD.
 */
public class AnswerGroupNote {

    // The question, such as Q2b
    private final String questionId;

    // How the answers did, such as PPFPF
    private final String pattern;

    // The name of the mistake, or "" for none
    private final String label;

    // The students in the group when it was checked; empty when not
    private final List<String> checkedUsernames = new ArrayList<>();

    /**
     * Creates a note.
     *
     * <p>Tier: COULD.
     *
     * @param questionId the question
     * @param pattern how the answers did
     * @param label the name of the mistake; null or "" for none
     * @param checkedUsernames the students checked; null or empty for
     *        not checked
     */
    public AnswerGroupNote(String questionId, String pattern, String label,
            List<String> checkedUsernames) {
        this.questionId = questionId;
        this.pattern = pattern;
        if (label == null) {
            this.label = "";
        } else {
            this.label = label;
        }
        if (checkedUsernames != null) {
            this.checkedUsernames.addAll(checkedUsernames);
        }
    }

    /**
     * Returns the question.
     *
     * <p>Tier: COULD.
     *
     * @return the question id
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns how the answers did.
     *
     * <p>Tier: COULD.
     *
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Returns the name of the mistake.
     *
     * <p>Tier: COULD.
     *
     * @return the name, or "" for none
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the students who were in the group when it was checked.
     *
     * <p>Tier: COULD.
     *
     * @return their usernames, empty when not checked
     */
    public List<String> getCheckedUsernames() {
        return checkedUsernames;
    }

    /**
     * Says whether the note holds nothing, so it need not be kept.
     *
     * <p>Tier: COULD.
     *
     * @return true with no name and no check
     */
    public boolean isEmpty() {
        boolean noName = label.isEmpty();
        boolean noCheck = checkedUsernames.isEmpty();
        return noName && noCheck;
    }
}
