/*
 * File: ReviewStudents.java
 *
 * Purpose: Remembers the students of the shown run, so the review screens
 * can show each username with the student's full name.
 *
 * Where it fits: Review Anomalies and Validation fill one each time they
 * reload. Their tables, inspectors, filters and search suggestions read
 * the names from it instead of searching the run again.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.GradingRun;
import gradezzy.model.Student;
import gradezzy.model.Submission;

/**
 * The students of one run, by username.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ReviewStudents {

    // The students of the run, by username
    private final Map<String, Student> students = new HashMap<>();

    // The usernames of the run, in submission order
    private final List<String> usernames = new ArrayList<>();

    // The run remembered last
    private GradingRun run = null;

    // How many submissions that run held when it was remembered
    private int count = -1;

    /**
     * Creates an empty list of students.
     */
    public ReviewStudents() {
        // nothing to set up
    }

    /**
     * Remembers the students of a run, unless it is the run remembered
     * last with the same number of submissions.
     *
     * @param newRun the run, or null when the section has none
     * @return true when the run or its number of submissions changed
     */
    public boolean remember(GradingRun newRun) {
        int newCount = 0;
        if (newRun != null) {
            newCount = newRun.getSubmissions().size();
        }
        if (newRun == run && newCount == count) {
            return false;
        }
        run = newRun;
        count = newCount;
        students.clear();
        usernames.clear();
        if (newRun == null) {
            return true;
        }
        for (Submission submission : newRun.getSubmissions()) {
            usernames.add(submission.getUsername());
            if (submission.getStudent() != null) {
                students.put(submission.getUsername(),
                        submission.getStudent());
            }
        }
        return true;
    }

    /**
     * Returns the student with a username.
     *
     * @param username the username
     * @return the student, or null when the roster does not name one
     */
    public Student get(String username) {
        return students.get(username);
    }

    /**
     * Returns a student's first and last name.
     *
     * @param username the username
     * @return the full name, or an empty string when it is not known
     */
    public String fullName(String username) {
        Student student = students.get(username);
        if (student == null) {
            return "";
        }
        return student.getFirstName() + " " + student.getLastName();
    }

    /**
     * Returns the usernames of the run.
     *
     * @return the usernames, in submission order
     */
    public List<String> getUsernames() {
        return new ArrayList<>(usernames);
    }
}
