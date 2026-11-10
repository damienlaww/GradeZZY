/*
 * File: FlagQuestionMatcher.java
 *
 * Purpose: Works out which question a flag is about from the words of
 * its message and suggested action: a question's id, file name or
 * folder name named as a whole word.
 *
 * Where it fits: AnswerGrouper uses it to tell which flags of a student
 * belong to the question being grouped.
 */
package gradezzy.stats;

import gradezzy.model.Anomaly;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;

/**
 * Matches the text of a flag to the questions it names.
 *
 * <p>Tier: COULD.
 */
final class FlagQuestionMatcher {

    // Comes between a flag's message and its suggested action
    private static final String TEXT_SEPARATOR = " ";

    // The one character other than letters and digits a word can hold
    private static final char UNDERSCORE = '_';

    // Only static methods; never created
    private FlagQuestionMatcher() {
    }

    // Returns the id of the question a flag is about: the longest of a
    // question's id, file name or folder name named as a whole word in
    // the flag's message or suggested action, or null when it names none.
    static String questionOf(Anomaly anomaly, Submission submission) {
        String text = textOf(anomaly);
        String found = null;
        int foundLength = 0;
        for (QuestionResult result : submission.getQuestionResults()) {
            Question question = result.getQuestion();
            if (question != null) {
                String[] names = {question.getId(), question.getFileName(),
                    question.getFolderName()};
                for (String name : names) {
                    if (namesLonger(text, name, foundLength)) {
                        found = question.getId();
                        foundLength = name.length();
                    }
                }
            }
        }
        return found;
    }

    // Says whether a flag is about a question: it names the question's id
    // or file, or it names the question's folder and no other question
    // (such as "The Q2 folder is missing"), which is about every question
    // in that folder.
    static boolean isAbout(Anomaly anomaly, Submission submission,
            Question question) {
        String text = textOf(anomaly);
        if (namesQuestion(text, question)) {
            return true;
        }
        if (!namesWord(text, question.getFolderName())) {
            return false;
        }
        return !namesOtherQuestion(text, submission, question);
    }

    // Says whether a text names a name longer than the longest found so
    // far as a whole word. A null name names nothing.
    private static boolean namesLonger(String text, String name,
            int foundLength) {
        if (name == null) {
            return false;
        }
        if (name.length() <= foundLength) {
            return false;
        }
        return namesWord(text, name);
    }

    // Says whether a text names a question's id or file name.
    private static boolean namesQuestion(String text, Question question) {
        boolean namesId = namesWord(text, question.getId());
        boolean namesFile = namesWord(text, question.getFileName());
        return namesId || namesFile;
    }

    // Says whether a text names another question than the one given.
    private static boolean namesOtherQuestion(String text,
            Submission submission, Question question) {
        for (QuestionResult result : submission.getQuestionResults()) {
            Question other = result.getQuestion();
            boolean another = other != null
                    && !other.getId().equals(question.getId());
            if (another && namesQuestion(text, other)) {
                return true;
            }
        }
        return false;
    }

    // Returns a flag's message and suggested action as one text.
    private static String textOf(Anomaly anomaly) {
        return anomaly.getMessage() + TEXT_SEPARATOR
                + anomaly.getSuggestedAction();
    }

    // Says whether a text holds a name as a whole word: no letter, digit
    // or underscore right before or after it.
    private static boolean namesWord(String text, String name) {
        boolean nothing = text == null || name == null;
        if (nothing) {
            return false;
        }
        if (name.isEmpty()) {
            return false;
        }
        int start = text.indexOf(name);
        while (start >= 0) {
            int end = start + name.length();
            if (isWholeWord(text, start, end)) {
                return true;
            }
            int next = start + 1;
            start = text.indexOf(name, next);
        }
        return false;
    }

    // Says whether the text from start up to end is a whole word: no
    // word character right before or after it.
    private static boolean isWholeWord(String text, int start, int end) {
        if (start > 0) {
            int before = start - 1;
            if (isWordChar(text.charAt(before))) {
                return false;
            }
        }
        if (end < text.length()) {
            return !isWordChar(text.charAt(end));
        }
        return true;
    }

    // Says whether a character can be part of a word.
    private static boolean isWordChar(char character) {
        boolean letterOrDigit = Character.isLetterOrDigit(character);
        return letterOrDigit || character == UNDERSCORE;
    }
}
