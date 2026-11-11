/*
 * File: StudentMatcher.java
 *
 * Purpose: Finds the students on a score sheet whose username is closest
 * to an upload's, so a typo such as tina.goh.2052 for tina.goh.2025 can
 * be matched to the right row: how many letters must be changed, added,
 * removed or swapped to turn one into the other.
 *
 * Where it fits: The student panel on Review uses it to suggest who a
 * student not on the score sheet most likely is, and lists the others
 * closest first; GradingService.matchStudent keeps the choice.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gradezzy.model.Student;

/**
 * Ranks score sheet students by how close their username is to another.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public final class StudentMatcher {

    // The most changes between two usernames that still look like a
    // typo, such as two digits swapped
    private static final int MAX_TYPO_CHANGES = 2;

    // How far back a swap of two letters next to each other looks: the
    // letter before the one just before
    private static final int TWO_LETTERS_BACK = 2;

    // What one removal, addition, change or swap of letters costs
    private static final int ONE_CHANGE = 1;

    // What a letter that already matches costs
    private static final int NO_CHANGE = 0;

    // Not used: every method is static.
    private StudentMatcher() {
    }

    /**
     * Lists students closest first by username; students as close keep
     * their order.
     *
     * <p>Tier: COULD.
     *
     * @param students the students, such as those with no submission
     * @param username the upload's username
     * @return a new list, closest first
     */
    public static List<Student> rankByCloseness(List<Student> students,
            String username) {
        List<Student> ranked = new ArrayList<>();
        List<Integer> changes = new ArrayList<>();
        for (Student student : students) {
            int needed = countChanges(student.getUsername(), username);
            int place = ranked.size();
            while (place > 0 && changes.get(place - 1) > needed) {
                place--;
            }
            ranked.add(place, student);
            changes.add(place, needed);
        }
        return ranked;
    }

    /**
     * Says whether two usernames differ only by what a typo makes, such
     * as two letters swapped or one missing.
     *
     * <p>Tier: COULD.
     *
     * @param first one username
     * @param second the other
     * @return true when they are this close
     */
    public static boolean isLikelyTypo(String first, String second) {
        return countChanges(first, second) <= MAX_TYPO_CHANGES;
    }

    /**
     * Counts the fewest letters to change, add, remove or swap with the
     * next to turn one username into the other, ignoring case.
     *
     * <p>Tier: COULD.
     *
     * @param first one username
     * @param second the other
     * @return the number of changes, 0 when they are the same
     */
    public static int countChanges(String first, String second) {
        String left = first.toLowerCase(Locale.ROOT);
        String right = second.toLowerCase(Locale.ROOT);
        int rows = left.length() + 1;
        int columns = right.length() + 1;
        int[][] cost = new int[rows][columns];
        for (int row = 0; row <= left.length(); row++) {
            cost[row][0] = row;
        }
        for (int column = 0; column <= right.length(); column++) {
            cost[0][column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            for (int column = 1; column <= right.length(); column++) {
                cost[row][column] = cheapestStep(left, right, cost, row,
                        column);
            }
        }
        return cost[left.length()][right.length()];
    }

    // Returns the fewest changes for the first row letters of one name
    // and the first column letters of the other, from the steps before:
    // remove, add, change, or swap two letters next to each other.
    private static int cheapestStep(String left, String right,
            int[][] cost, int row, int column) {
        int previousRow = row - 1;
        int previousColumn = column - 1;
        char leftLetter = left.charAt(previousRow);
        char rightLetter = right.charAt(previousColumn);
        int change = ONE_CHANGE;
        if (leftLetter == rightLetter) {
            change = NO_CHANGE;
        }
        int removed = cost[previousRow][column] + ONE_CHANGE;
        int added = cost[row][previousColumn] + ONE_CHANGE;
        int changed = cost[previousRow][previousColumn] + change;
        int best = Math.min(removed, added);
        best = Math.min(best, changed);

        int rowTwoBack = row - TWO_LETTERS_BACK;
        int columnTwoBack = column - TWO_LETTERS_BACK;
        boolean swapped = row > 1 && column > 1
                && leftLetter == right.charAt(columnTwoBack)
                && left.charAt(rowTwoBack) == rightLetter;
        if (swapped) {
            int swappedCost = cost[rowTwoBack][columnTwoBack] + ONE_CHANGE;
            best = Math.min(best, swappedCost);
        }
        return best;
    }
}
