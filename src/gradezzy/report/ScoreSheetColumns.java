/*
 * File: ScoreSheetColumns.java
 *
 * Purpose: Where each value goes in the score sheet CSV: the username,
 * the total, and the mark of every question of the run.
 *
 * Where it fits: CsvScoreSheetExporter reads the sheet's header, then
 * asks this class where to put each student's marks. A question column
 * the sheet already has is filled in place; the others are added just
 * before the End-of-Line Indicator, which eLearn needs as the last
 * column.
 */
package gradezzy.report;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.model.Question;

/**
 * The columns of the score sheet that the export fills or adds.
 *
 * <p>Tier: SHOULD.
 */
public class ScoreSheetColumns {

    /**
     * The last column of an eLearn score sheet; added columns go before
     * it so it stays last.
     */
    public static final String END_OF_LINE_COLUMN = "End-of-Line Indicator";

    /**
     * Ends the name of a question's column. eLearn (Brightspace) reads a
     * column named "item name Points Grade" as the points of a numeric
     * grade item with that name, and offers to create the item if the
     * course does not have it yet.
     */
    public static final String POINTS_SUFFIX = " Points Grade";

    // Stands for a column the sheet does not have yet
    private static final int NOT_IN_SHEET = -1;

    // Joins the assessment name and the question id in a column name
    private static final String NAME_SEPARATOR = " ";

    // Stands in for a character a grade item name may not hold
    private static final char SPACE = ' ';

    // The characters kept in a grade item name besides letters and
    // digits; eLearn refuses names with characters such as , " / : and #
    private static final String SAFE_NAME_CHARACTERS = " -_";

    // The longest grade item name eLearn accepts
    private static final int MAX_ITEM_NAME_LENGTH = 128;

    // Where the username is, counted from 0
    private final int usernameIndex;

    // Where the total goes
    private final int numeratorIndex;

    // Where the added columns go: the End-of-Line Indicator's place, or
    // the end of the header when the sheet has none
    private final int insertIndex;

    // The run's questions, in assessment order
    private final List<Question> questions;

    // The column name of each question, in the same order
    private final List<String> questionNames = new ArrayList<>();

    // The place of each question's column in the sheet, or NOT_IN_SHEET
    private final List<Integer> questionIndexes = new ArrayList<>();

    /**
     * Works out the columns from the sheet's header and the run's
     * assessment.
     *
     * @param header the values of the sheet's header
     * @param usernameIndex where the username is
     * @param numeratorIndex where the total goes
     * @param assessment the run's assessment, or null for no questions
     */
    public ScoreSheetColumns(List<String> header, int usernameIndex,
            int numeratorIndex, Assessment assessment) {
        this.usernameIndex = usernameIndex;
        this.numeratorIndex = numeratorIndex;
        this.questions = new ArrayList<>();
        if (assessment != null) {
            questions.addAll(assessment.getQuestions());
        }

        int endIndex = header.indexOf(END_OF_LINE_COLUMN);
        if (endIndex < 0) {
            endIndex = header.size();
        }
        this.insertIndex = endIndex;

        for (Question question : questions) {
            String name = nameColumn(assessment, question);
            questionNames.add(name);
            questionIndexes.add(header.indexOf(name));
        }
    }

    /**
     * Names the column of a question, such as "Lab Test 2 Q1a Points
     * Grade". The assessment name keeps Q1a of two lab tests apart, as
     * eLearn needs each grade item name to be unique in a course.
     *
     * @param assessment the assessment, or null
     * @param question the question
     * @return the column name
     */
    public static String nameColumn(Assessment assessment,
            Question question) {
        String itemName = question.getId();
        boolean named = assessment != null && assessment.getName() != null
                && !assessment.getName().trim().isEmpty();
        if (named) {
            itemName = assessment.getName().trim() + NAME_SEPARATOR
                    + question.getId();
        }
        return makeSafe(itemName) + POINTS_SUFFIX;
    }

    /**
     * Returns where the username is.
     *
     * @return the column's place, counted from 0
     */
    public int getUsernameIndex() {
        return usernameIndex;
    }

    /**
     * Returns where the total goes.
     *
     * @return the column's place, counted from 0
     */
    public int getNumeratorIndex() {
        return numeratorIndex;
    }

    /**
     * Returns where the added columns go.
     *
     * @return the place of the End-of-Line Indicator, or the number of
     *         columns when the sheet has none
     */
    public int getInsertIndex() {
        return insertIndex;
    }

    /**
     * Returns the run's questions, in assessment order.
     *
     * @return the questions
     */
    public List<Question> getQuestions() {
        return questions;
    }

    /**
     * Says whether the sheet already has a question's column.
     *
     * @param position the question's place in getQuestions
     * @return true when the column is filled in place
     */
    public boolean isInSheet(int position) {
        return questionIndexes.get(position) != NOT_IN_SHEET;
    }

    /**
     * Returns where a question's column is in the sheet.
     *
     * @param position the question's place in getQuestions
     * @return the column's place, or -1 when it is added
     */
    public int getQuestionIndex(int position) {
        return questionIndexes.get(position);
    }

    /**
     * Returns the names of the columns to add, in assessment order.
     *
     * @return the names of the question columns the sheet lacks
     */
    public List<String> listAddedNames() {
        List<String> added = new ArrayList<>();
        for (int position = 0; position < questions.size(); position++) {
            if (!isInSheet(position)) {
                added.add(questionNames.get(position));
            }
        }
        return added;
    }

    // Keeps letters, digits, spaces, dashes and underscores, turns any
    // other character into a space, then removes doubled spaces and
    // shortens the name to what eLearn accepts
    private static String makeSafe(String text) {
        String safe = "";
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            boolean kept = Character.isLetterOrDigit(current)
                    || SAFE_NAME_CHARACTERS.indexOf(current) >= 0;
            if (!kept) {
                current = SPACE;
            }
            boolean doubledSpace = current == SPACE
                    && safe.endsWith(NAME_SEPARATOR);
            if (!doubledSpace) {
                safe += current;
            }
        }
        safe = safe.trim();
        if (safe.length() > MAX_ITEM_NAME_LENGTH) {
            safe = safe.substring(0, MAX_ITEM_NAME_LENGTH).trim();
        }
        return safe;
    }
}
