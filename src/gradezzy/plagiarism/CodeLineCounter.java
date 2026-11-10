/*
 * File: CodeLineCounter.java
 *
 * Purpose: Counts the lines of new code in a student's answer to a
 * question: lines that hold code, leaving out blank lines, comments,
 * lines of only braces, brackets or semicolons, and lines that are also
 * in the starter file.
 *
 * Where it fits: Both similarity detectors leave out an answer with
 * fewer such lines than plagiarism.min.lines, because short answers to
 * easy questions are often written the same way by honest students.
 */
package gradezzy.plagiarism;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gradezzy.model.Question;
import gradezzy.util.TextUtils;

/**
 * Counts the lines of new code in an answer.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public final class CodeLineCounter {

    // Tokens that only lay code out: a line of nothing else is not
    // counted, so where a student puts their braces changes nothing
    private static final String[] LAYOUT_TOKENS = {
        "{", "}", "(", ")", ";"
    };

    // Comes between the tokens of a line when it is written as one text
    private static final String TOKEN_SEPARATOR = " ";

    // The line number before the first line of a file
    private static final int NO_LINE = -1;

    // Not used: every method is static.
    private CodeLineCounter() {
    }

    /**
     * Reads the lines of code of a question's files in one folder, such
     * as the starter's, each written as its tokens with one space
     * between, so spacing and comments do not matter.
     *
     * <p>Tier: COULD.
     *
     * @param folder the question's folder, or null when it is missing
     * @param question the question, whose answer file and other source
     *        files are read
     * @return the lines of code; empty when the folder is missing
     */
    public static Set<String> readLines(Path folder, Question question) {
        return new HashSet<>(listLines(folder, question));
    }

    /**
     * Counts the lines of code of a question's files in a student's
     * folder that are not also in the starter.
     *
     * <p>Tier: COULD.
     *
     * @param folder the student's folder for the question, or null when
     *        it is missing
     * @param question the question
     * @param starterLines the starter's lines, from readLines
     * @return the number of new lines of code
     */
    public static int countNewLines(Path folder, Question question,
            Set<String> starterLines) {
        int count = 0;
        for (String line : listLines(folder, question)) {
            if (!starterLines.contains(line)) {
                count++;
            }
        }
        return count;
    }

    // Lists the lines of code of the answer file and the question's other
    // source files, such as GoldMember.java, in one folder.
    private static List<String> listLines(Path folder, Question question) {
        List<String> lines = new ArrayList<>();
        if (folder == null) {
            return lines;
        }

        lines.addAll(readFileLines(folder.resolve(question.getFileName())));
        for (String extraFile : question.getExtraFileNames()) {
            lines.addAll(readFileLines(folder.resolve(extraFile)));
        }
        return lines;
    }

    // Reads the lines of code of one file, or none when it is missing or
    // cannot be read.
    private static List<String> readFileLines(Path file) {
        List<String> lines = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return lines;
        }

        String source = "";
        try {
            source = new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return lines;
        }

        int lineNumber = NO_LINE;
        List<String> lineTokens = new ArrayList<>();
        for (JavaToken token : JavaTokenizer.tokenize(source)) {
            if (token.getLine() != lineNumber) {
                addLine(lines, lineTokens);
                lineTokens = new ArrayList<>();
                lineNumber = token.getLine();
            }
            lineTokens.add(token.getText());
        }
        addLine(lines, lineTokens);
        return lines;
    }

    // Adds the tokens of one line as one text, unless the line holds only
    // layout or nothing at all.
    private static void addLine(List<String> lines, List<String> tokens) {
        boolean onlyLayout = true;
        for (String token : tokens) {
            if (!isLayout(token)) {
                onlyLayout = false;
            }
        }
        if (!onlyLayout) {
            lines.add(TextUtils.join(TOKEN_SEPARATOR, tokens));
        }
    }

    // Says whether a token only lays code out.
    private static boolean isLayout(String token) {
        for (String layout : LAYOUT_TOKENS) {
            if (layout.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
