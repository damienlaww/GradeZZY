/*
 * File: SearchService.java
 *
 * Purpose: Searches, filters and sorts the submissions of a run.
 *
 * Where it fits: Used by the results and anomaly screens.
 */
package gradezzy.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * Searches, filters and sorts the submissions of a run. Every method
 * returns a new list and leaves the run unchanged.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class SearchService {

    // Extension of the source files searched by searchCode
    private static final String SOURCE_EXTENSION = ".java";

    /**
     * Creates a new search service.
     */
    public SearchService() {
        // nothing to set up yet
    }

    /**
     * Finds submissions whose username or name contains a keyword. The
     * match ignores case and spaces around the keyword. The name is
     * matched as "first last" and as "last first". A blank keyword
     * matches every submission.
     *
     * @param gradingRun the grading run
     * @param keyword the keyword
     * @return the matching submissions, or an empty list if there is no
     *         run
     */
    public List<Submission> search(GradingRun gradingRun, String keyword) {
        List<Submission> matches = new ArrayList<>();

        if (gradingRun == null) {
            return matches;
        }

        String wanted = normalise(keyword);

        for (Submission submission : gradingRun.getSubmissions()) {
            if (wanted.isEmpty() || matchesKeyword(submission, wanted)) {
                matches.add(submission);
            }
        }

        return matches;
    }

    /**
     * Finds submissions whose source contains a piece of code. Only the
     * .java files in each working copy are searched, and the match is
     * case sensitive. A file that cannot be read is skipped.
     *
     * @param gradingRun the grading run
     * @param codeFragment the code fragment
     * @return the matching submissions, or an empty list if the run or
     *         the fragment is missing or empty
     */
    public List<Submission> searchCode(GradingRun gradingRun,
            String codeFragment) {
        List<Submission> matches = new ArrayList<>();

        if (gradingRun == null || codeFragment == null
                || codeFragment.isEmpty()) {
            return matches;
        }

        for (Submission submission : gradingRun.getSubmissions()) {
            Path workingCopy = submission.getWorkingCopyPath();

            if (workingCopy != null
                    && containsCode(workingCopy.toFile(), codeFragment)) {
                matches.add(submission);
            }
        }

        return matches;
    }

    /**
     * Keeps the submissions with a given status.
     *
     * @param gradingRun the grading run
     * @param status the status
     * @return the matching submissions, or an empty list if the run or
     *         the status is missing
     */
    public List<Submission> filterByStatus(GradingRun gradingRun,
            SubmissionStatus status) {
        List<Submission> matches = new ArrayList<>();

        if (gradingRun == null || status == null) {
            return matches;
        }

        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getStatus() == status) {
                matches.add(submission);
            }
        }

        return matches;
    }

    /**
     * Keeps the submissions that have a given kind of anomaly. A
     * submission with several anomalies of that kind appears once.
     *
     * @param gradingRun the grading run
     * @param anomalyType the anomaly type
     * @return the matching submissions, or an empty list if the run or
     *         the type is missing
     */
    public List<Submission> filterByAnomalyType(GradingRun gradingRun,
            AnomalyType anomalyType) {
        List<Submission> matches = new ArrayList<>();

        if (gradingRun == null || anomalyType == null) {
            return matches;
        }

        for (Submission submission : gradingRun.getSubmissions()) {
            if (hasAnomalyType(submission, anomalyType)) {
                matches.add(submission);
            }
        }

        return matches;
    }

    /**
     * Sorts submissions by total score. Submissions with the same total
     * keep their original order. The list passed in is not changed.
     *
     * @param submissions the submissions
     * @param ascending true for lowest first, false for highest first
     * @return a new sorted list, or an empty list if there are no
     *         submissions
     */
    public List<Submission> sortByScore(List<Submission> submissions,
            boolean ascending) {
        List<Submission> sorted = new ArrayList<>();

        if (submissions == null) {
            return sorted;
        }

        // Insertion sort: each submission moves left past every
        // submission it must come before, so equal totals keep their
        // original order.
        for (Submission submission : submissions) {
            int position = sorted.size();

            while (position > 0 && comesBefore(submission,
                    sorted.get(position - 1), ascending)) {
                position--;
            }

            sorted.add(position, submission);
        }

        return sorted;
    }

    // Lower case with spaces at both ends removed; null becomes "".
    private String normalise(String text) {
        if (text == null) {
            return "";
        }

        return text.trim().toLowerCase();
    }

    // Says whether the username or the student's name holds the keyword.
    private boolean matchesKeyword(Submission submission, String wanted) {
        if (normalise(submission.getUsername()).contains(wanted)) {
            return true;
        }

        Student student = submission.getStudent();

        if (student == null) {
            return false;
        }

        String firstName = normalise(student.getFirstName());
        String lastName = normalise(student.getLastName());
        String fullName = firstName + " " + lastName;
        String reversedName = lastName + " " + firstName;

        return fullName.contains(wanted) || reversedName.contains(wanted);
    }

    // Says whether a .java file in or under a folder holds the fragment.
    private boolean containsCode(File folder, String codeFragment) {
        File[] entries = folder.listFiles();

        if (entries == null) {
            return false;
        }

        for (File entry : entries) {
            // Links are skipped so a link to a parent folder cannot loop.
            boolean isLink = Files.isSymbolicLink(entry.toPath());

            if (!isLink && entry.isDirectory()
                    && containsCode(entry, codeFragment)) {
                return true;
            }

            if (!isLink && isSourceFile(entry)
                    && fileContains(entry, codeFragment)) {
                return true;
            }
        }

        return false;
    }

    // Says whether a file is a Java source file.
    private boolean isSourceFile(File file) {
        return file.isFile() && file.getName().endsWith(SOURCE_EXTENSION);
    }

    // Says whether a file's text holds the fragment.
    private boolean fileContains(File file, String codeFragment) {
        try {
            String text = Files.readString(file.toPath());
            return text.contains(codeFragment);
        } catch (IOException exception) {
            // An unreadable file, or one that is not UTF-8, cannot
            // match; the search carries on with the other files.
            return false;
        }
    }

    // Says whether a submission has at least one anomaly of a type.
    private boolean hasAnomalyType(Submission submission,
            AnomalyType anomalyType) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == anomalyType) {
                return true;
            }
        }

        return false;
    }

    // Says whether the first submission must come before the second.
    private boolean comesBefore(Submission first, Submission second,
            boolean ascending) {
        if (ascending) {
            return first.getTotalScore() < second.getTotalScore();
        }

        return first.getTotalScore() > second.getTotalScore();
    }
}
