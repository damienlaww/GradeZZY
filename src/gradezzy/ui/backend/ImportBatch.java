/*
 * File: ImportBatch.java
 *
 * Purpose: Holds the files for the next grading run and says if it may start.
 *
 * Where it fits: Kept in FlowState so the choices survive navigation.
 * Set up fills it and DashboardBackend.startGrading reads it.
 */
package gradezzy.ui.backend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import gradezzy.ingest.SubmissionLoader;
import gradezzy.testcase.TestCase;

/**
 * The files chosen for the next grading run.
 */
public class ImportBatch {

    // Every chosen item, in the order it was added
    private final List<ImportItem> items = new ArrayList<>();

    // Test cases typed on the Set up screen
    private final List<TestCase> typedTestCases = new ArrayList<>();

    // Whether grading uses the typed test cases instead of a folder
    private boolean useTypedTestCases = false;

    /**
     * Creates an empty batch.
     */
    public ImportBatch() {
        // nothing to set up
    }

    /**
     * Returns the test cases typed on the Set up screen.
     *
     * @return the typed test cases, which may be changed
     */
    public List<TestCase> getTypedTestCases() {
        return typedTestCases;
    }

    /**
     * Says whether grading uses the typed test cases.
     *
     * @return true for typed test cases, false for a tester folder
     */
    public boolean isUsingTypedTestCases() {
        return useTypedTestCases;
    }

    /**
     * Chooses between typed test cases and a tester folder.
     *
     * @param useTypedTestCases true for typed test cases
     */
    public void setUsingTypedTestCases(boolean useTypedTestCases) {
        this.useTypedTestCases = useTypedTestCases;
    }

    /**
     * Adds a chosen file. A new roster, tester folder or starter folder
     * replaces the old one, as a run uses only one.
     *
     * @param item the chosen file
     */
    public void add(ImportItem item) {
        if (item.getKind() != InputKind.SUBMISSION) {
            clear(item.getKind());
        }
        items.add(item);
    }

    /**
     * Removes a chosen file.
     *
     * @param item the file to remove
     */
    public void remove(ImportItem item) {
        items.remove(item);
    }

    /**
     * Puts a new file in the place of an old one.
     *
     * @param oldItem the file to replace
     * @param newItem the file that replaces it
     */
    public void replace(ImportItem oldItem, ImportItem newItem) {
        int index = items.indexOf(oldItem);
        if (index < 0) {
            add(newItem);
            return;
        }
        items.set(index, newItem);
    }

    /**
     * Removes every file of one group.
     *
     * @param kind the group to empty
     */
    public void clear(InputKind kind) {
        List<ImportItem> kept = new ArrayList<>();
        for (ImportItem item : items) {
            if (item.getKind() != kind) {
                kept.add(item);
            }
        }
        items.clear();
        items.addAll(kept);
    }

    /**
     * Removes every chosen file.
     */
    public void clearAll() {
        items.clear();
    }

    /**
     * Returns the files of one group.
     *
     * @param kind the group
     * @return the files, in the order they were added
     */
    public List<ImportItem> getItems(InputKind kind) {
        List<ImportItem> matches = new ArrayList<>();
        for (ImportItem item : items) {
            if (item.getKind() == kind) {
                matches.add(item);
            }
        }
        return matches;
    }

    /**
     * Returns the state to show for a file. When a file or folder is
     * chosen twice, or a student has two usable files, the one grading
     * leaves out is a duplicate. Both are still sent to grading, which
     * grades one file per student and notes the other on that student.
     *
     * @param item a file of this batch
     * @return the state to show
     */
    public ImportState getEffectiveState(ImportItem item) {
        if (findGradedInstead(item) != null) {
            return ImportState.DUPLICATE;
        }
        return item.getState();
    }

    /**
     * Returns the file grading keeps in place of this one, by the rule
     * grading uses: the newest download, which has the highest copy
     * number such as (1); then, for one username in two letter cases, the
     * lower-case one; then the file chosen first.
     *
     * @param item a file of this batch
     * @return the file graded instead, or null when this file is graded
     */
    public ImportItem findGradedInstead(ImportItem item) {
        if (item.getKind() != InputKind.SUBMISSION
                || !item.getState().isUsable()) {
            return null;
        }

        ImportItem kept = item;
        for (ImportItem other : getItems(InputKind.SUBMISSION)) {
            if (other != item && other.getState().isUsable()
                    && isSameUpload(other, item)
                    && isGradedBefore(other, kept)) {
                kept = other;
            }
        }
        if (kept == item) {
            return null;
        }
        return kept;
    }

    /**
     * Says whether a file is left out because the very same file was
     * chosen before. Grading then says the file was chosen more than once
     * and graded once.
     *
     * @param item a file of this batch
     * @return true when the file graded instead is the same file
     */
    public boolean isChosenAgain(ImportItem item) {
        ImportItem kept = findGradedInstead(item);
        return kept != null && kept.getPath().toAbsolutePath().normalize()
                .equals(item.getPath().toAbsolutePath().normalize());
    }

    /**
     * Counts the files of one group in one state.
     *
     * @param kind the group
     * @param state the state to count
     * @return the number of files
     */
    public int count(InputKind kind, ImportState state) {
        int total = 0;
        for (ImportItem item : getItems(kind)) {
            if (getEffectiveState(item) == state) {
                total++;
            }
        }
        return total;
    }

    /**
     * Counts the different students in the usable submission files. A
     * folder of zips or a zip of student zips counts each student inside,
     * and a student in two files counts once, as grading keeps only one.
     *
     * @return the number of students
     */
    public int countStudents() {
        Set<String> seen = new HashSet<>();
        for (String username : listStudents()) {
            seen.add(username.toLowerCase(Locale.ROOT));
        }
        return seen.size();
    }

    /**
     * Lists the usernames found in more than one usable submission, across
     * every chosen file and folder. Grading grades one file per student
     * (the newest download, with the highest copy number such as (1),
     * else the one named in lower case, else the first chosen) and notes
     * the others on that student. Case does not matter. A file
     * chosen again (see isChosenAgain) is not counted twice.
     *
     * @return each repeated username once, in the order first repeated
     */
    public List<String> listDuplicateUsernames() {
        Set<String> seen = new HashSet<>();
        Set<String> repeated = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String username : listStudents()) {
            String key = username.toLowerCase(Locale.ROOT);
            if (!seen.add(key) && repeated.add(key)) {
                duplicates.add(username);
            }
        }
        return duplicates;
    }

    /**
     * Returns the submission files that will be graded.
     *
     * @return the usable uploads, including duplicates for per-file reporting
     */
    public List<Path> getSubmissionPaths() {
        List<Path> paths = new ArrayList<>();
        for (ImportItem item : getItems(InputKind.SUBMISSION)) {
            if (item.getState().isUsable()) {
                paths.add(item.getPath());
            }
        }
        return paths;
    }

    /**
     * Returns the chosen file of a single-file group.
     *
     * @param kind the roster, tester or starter group
     * @return the chosen path, or null if none is chosen
     */
    public Path getSinglePath(InputKind kind) {
        List<ImportItem> chosen = getItems(kind);
        if (chosen.isEmpty()) {
            return null;
        }
        return chosen.get(0).getPath();
    }

    /**
     * Returns every reason grading cannot start yet; empty means ready.
     *
     * @return plain sentences, one per problem
     */
    public List<String> getBlockingReasons() {
        List<String> reasons = new ArrayList<>();
        List<ImportItem> submissions = getItems(InputKind.SUBMISSION);

        if (submissions.isEmpty()) {
            reasons.add("Add at least one student ZIP file.");
        }

        int invalid = count(InputKind.SUBMISSION, ImportState.INVALID);
        if (invalid > 0) {
            reasons.add(invalid + " chosen file" + plural(invalid)
                    + " or folder" + plural(invalid)
                    + " cannot be used. Replace or remove "
                    + pronoun(invalid) + ".");
        }

        addSingleReason(reasons, InputKind.ROSTER);

        if (!useTypedTestCases) {
            addSingleReason(reasons, InputKind.TESTERS);
        } else if (typedTestCases.isEmpty()) {
            reasons.add("Type at least one test case, or choose a tester "
                    + "folder instead.");
        }
        return reasons;
    }

    /**
     * Says whether grading may start.
     *
     * @return true when nothing blocks the batch
     */
    public boolean isReady() {
        return getBlockingReasons().isEmpty();
    }

    // Says whether two uploads are the same path, or one student's file
    // twice. A folder or zip of several students has no username.
    private boolean isSameUpload(ImportItem first, ImportItem second) {
        if (first.getPath().equals(second.getPath())) {
            return true;
        }
        return !second.getUsername().isEmpty()
                && first.getUsername().equalsIgnoreCase(
                        second.getUsername());
    }

    // Says whether grading keeps the first of two files of one student,
    // by the rule findGradedInstead describes.
    private boolean isGradedBefore(ImportItem first, ImportItem second) {
        int firstCopy = SubmissionLoader.readCopyNumber(first.getName());
        int secondCopy = SubmissionLoader.readCopyNumber(second.getName());
        if (firstCopy != secondCopy) {
            return firstCopy > secondCopy;
        }

        boolean firstLower = isLowerCase(first.getUsername());
        boolean secondLower = isLowerCase(second.getUsername());
        if (firstLower != secondLower) {
            return firstLower;
        }
        return items.indexOf(first) < items.indexOf(second);
    }

    // Says whether a username has no capital letter.
    private boolean isLowerCase(String username) {
        return username.equals(username.toLowerCase(Locale.ROOT));
    }

    // Lists the students of every usable submission, in the order
    // grading meets them; duplicates are still sent to grading. A file
    // chosen again is left out, as grading grades it once.
    private List<String> listStudents() {
        List<String> students = new ArrayList<>();
        for (ImportItem item : getItems(InputKind.SUBMISSION)) {
            if (item.getState().isUsable() && !isChosenAgain(item)) {
                students.addAll(item.getStudents());
            }
        }
        return students;
    }

    // Adds the reason a required single-file group blocks grading.
    private void addSingleReason(List<String> reasons, InputKind kind) {
        List<ImportItem> chosen = getItems(kind);
        if (chosen.isEmpty()) {
            reasons.add("Add the " + kind.getTitle().toLowerCase() + ".");
            return;
        }
        if (!chosen.get(0).getState().isUsable()) {
            reasons.add("The " + kind.getTitle().toLowerCase()
                    + " cannot be used: " + chosen.get(0).getMessage());
        }
    }

    // Returns "s" for any count other than one.
    private String plural(int count) {
        if (count == 1) {
            return "";
        }
        return "s";
    }

    // Returns "it" or "them" for a count.
    private String pronoun(int count) {
        if (count == 1) {
            return "it";
        }
        return "them";
    }
}
