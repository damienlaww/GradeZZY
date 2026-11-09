/*
 * File: AnswerGroup.java
 *
 * Purpose: The students of one run who lost marks the same way on one
 * question, such as everyone who failed tests 3 and 5 of Q2b, with the
 * name the marker gave the mistake and who was checked.
 *
 * Where it fits: AnswerGrouper builds the groups from a run;
 * AnswerGroupService adds the name and the checked students; the
 * Review step, Statistics and the common mistakes export show them.
 */
package gradezzy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The students who lost marks the same way on one question.
 *
 * <p>Tier: COULD.
 */
public class AnswerGroup {

    // Joins the question and the pattern into the group's key
    private static final String KEY_SEPARATOR = "|";

    // The question, such as Q2b
    private final String questionId;

    // How the answers did, such as PPFPF or COMPILE
    private final String pattern;

    // The words shown for the pattern, such as "Failed tests 3 and 5"
    private final String title;

    // The students in the group, in the run's order
    private final List<String> usernames = new ArrayList<>();

    // Sets of two or more students whose code has the same shape
    private final List<List<String>> sameShapes = new ArrayList<>();

    // The marker's name for the mistake, or "" for none
    private String label = "";

    // The students in the group when the marker checked it
    private final List<String> checkedUsernames = new ArrayList<>();

    /**
     * Creates an empty group.
     *
     * <p>Tier: COULD.
     *
     * @param questionId the question, such as Q2b
     * @param pattern how the answers did, such as PPFPF
     * @param title the words shown for the pattern
     */
    public AnswerGroup(String questionId, String pattern, String title) {
        this.questionId = questionId;
        this.pattern = pattern;
        this.title = title;
    }

    /**
     * Returns the question.
     *
     * <p>Tier: COULD.
     *
     * @return the question id, such as Q2b
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns how the answers did.
     *
     * <p>Tier: COULD.
     *
     * @return the pattern, such as PPFPF, COMPILE, MISSING, T:PP or X:PF
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Returns the words shown for the pattern.
     *
     * <p>Tier: COULD.
     *
     * @return the title, such as "Failed tests 3 and 5"
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the key that names the group within its run.
     *
     * <p>Tier: COULD.
     *
     * @return the question and pattern, such as "Q2b|PPFPF"
     */
    public String getKey() {
        return questionId + KEY_SEPARATOR + pattern;
    }

    /**
     * Returns the students in the group.
     *
     * <p>Tier: COULD.
     *
     * @return their usernames, in the run's order
     */
    public List<String> getUsernames() {
        return usernames;
    }

    /**
     * Adds a student to the group.
     *
     * <p>Tier: COULD.
     *
     * @param username the student
     */
    public void addUsername(String username) {
        usernames.add(username);
    }

    /**
     * Returns the sets of students whose code has the same shape.
     *
     * <p>Tier: COULD.
     *
     * @return each set holds two or more usernames
     */
    public List<List<String>> getSameShapes() {
        return sameShapes;
    }

    /**
     * Adds a set of students whose code has the same shape.
     *
     * <p>Tier: COULD.
     *
     * @param shapeUsernames two or more usernames
     */
    public void addSameShape(List<String> shapeUsernames) {
        sameShapes.add(shapeUsernames);
    }

    /**
     * Counts the students who share a shape with another student.
     *
     * <p>Tier: COULD.
     *
     * @return the number of students in any same-shape set
     */
    public int countSameShape() {
        int count = 0;
        for (List<String> shape : sameShapes) {
            count += shape.size();
        }
        return count;
    }

    /**
     * Returns the marker's name for the mistake.
     *
     * <p>Tier: COULD.
     *
     * @return the name, or "" when there is none
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the marker's name for the mistake.
     *
     * <p>Tier: COULD.
     *
     * @param label the name; null means none
     */
    public void setLabel(String label) {
        if (label == null) {
            this.label = "";
            return;
        }
        this.label = label;
    }

    /**
     * Returns the students who were in the group when it was checked.
     *
     * <p>Tier: COULD.
     *
     * @return their usernames, empty when it was never checked
     */
    public List<String> getCheckedUsernames() {
        return checkedUsernames;
    }

    /**
     * Sets the students who were in the group when it was checked.
     *
     * <p>Tier: COULD.
     *
     * @param checked their usernames; null or empty for not checked
     */
    public void setCheckedUsernames(List<String> checked) {
        checkedUsernames.clear();
        if (checked != null) {
            checkedUsernames.addAll(checked);
        }
    }

    /**
     * Says whether the marker checked every student now in the group.
     *
     * <p>Tier: COULD.
     *
     * @return true when it was checked and no student joined since
     */
    public boolean isChecked() {
        return !checkedUsernames.isEmpty() && countNewSinceChecked() == 0;
    }

    /**
     * Counts the students who joined the group after it was checked.
     *
     * <p>Tier: COULD.
     *
     * @return the number; every student when it was never checked
     */
    public int countNewSinceChecked() {
        int count = 0;
        for (String username : usernames) {
            if (!checkedUsernames.contains(username)) {
                count++;
            }
        }
        return count;
    }
}
