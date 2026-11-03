/*
 * File: TokenSimilarityDetector.java
 *
 * Purpose: Compares submissions by the tokens of their source code.
 *
 * Where it fits: Used by PlagiarismService.
 */
package gradezzy.plagiarism;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.util.TextUtils;

/**
 * Compares submissions by the tokens of their source code.
 */
public class TokenSimilarityDetector implements SimilarityDetector {

    /**
     * The name of the algorithm.
     */
    public static final String NAME = "Token similarity";

    // The number of tokens in a row that are compared as one unit
    private static final int TRIGRAM_LENGTH = 3;

    // The fewest new trigrams each answer needs before a pair is scored.
    // Short correct answers (about 30 to 45) are often written the same
    // way by honest students, so a match on them proves nothing.
    private static final int MIN_OWN_TRIGRAMS = 50;

    // The text placed between the tokens of a trigram
    private static final String TOKEN_SEPARATOR = " ";

    // The similarity of two texts that share nothing
    private static final double NO_SIMILARITY = 0.0;

    // The text that starts a comment running to the end of the line
    private static final String LINE_COMMENT = "//";

    // The text that starts a comment running to BLOCK_COMMENT_END
    private static final String BLOCK_COMMENT_START = "/*";

    // The text that ends a block comment
    private static final String BLOCK_COMMENT_END = "*/";

    // The text that ends a line
    private static final String NEW_LINE = "\n";

    // The quote around a string literal
    private static final char DOUBLE_QUOTE = '"';

    // The quote around a char literal
    private static final char SINGLE_QUOTE = '\'';

    // The character that escapes the next one inside a literal
    private static final char BACKSLASH = '\\';

    // The point inside a decimal number such as 3.14
    private static final char DECIMAL_POINT = '.';

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new token similarity detector.
     *
     * @param config the application settings
     */
    public TokenSimilarityDetector(AppConfig config) {
        this.config = config;
    }

    /**
     * Returns the name of the algorithm.
     *
     * @return the name of the algorithm
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Compares every pair of submissions whose answers hold enough new
     * code, leaving out the code in paths.starter.dir.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @return the pairs at or above the configured threshold
     */
    @Override
    public List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment) {
        return findSimilar(submissions, assessment, null);
    }

    /**
     * Compares every pair of submissions whose answers hold enough new
     * code, leaving out the code in the run's starter folder.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @param starterDirectory the run's starter folder, or null to use
     *        paths.starter.dir
     * @return the pairs at or above the configured threshold
     */
    @Override
    public List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment, Path starterDirectory) {
        double threshold = config.getDouble(ConfigKeys.PLAGIARISM_THRESHOLD);
        Path starter = starterDirectory;
        if (starter == null) {
            starter = Paths.get(config.getString(ConfigKeys.STARTER_DIR));
        }
        List<SimilarityPair> pairs = new ArrayList<>();

        for (Question question : assessment.getQuestions()) {
            List<SimilarityPair> questionPairs = compareQuestion(submissions,
                    assessment, question, starter, threshold);

            for (SimilarityPair pair : questionPairs) {
                insertBySimilarity(pairs, pair);
            }
        }

        return pairs;
    }

    /**
     * Measures how alike two source texts are.
     *
     * @param firstSource the first source
     * @param secondSource the second source
     * @return the similarity, from 0 to 1
     */
    public double similarity(String firstSource, String secondSource) {
        if (firstSource == null || secondSource == null) {
            return NO_SIMILARITY;
        }

        return jaccard(buildTrigrams(firstSource),
                buildTrigrams(secondSource));
    }

    // Finds the pairs of students whose new code for a question is alike.
    private List<SimilarityPair> compareQuestion(List<Submission> submissions,
            Assessment assessment, Question question, Path starter,
            double threshold) {
        List<Set<String>> answers = readNewCode(submissions, assessment,
                question, starter);
        List<SimilarityPair> pairs = new ArrayList<>();

        for (int i = 0; i < submissions.size(); i++) {
            for (int j = i + 1; j < submissions.size(); j++) {
                Set<String> firstAnswer = answers.get(i);
                Set<String> secondAnswer = answers.get(j);
                double score = jaccard(firstAnswer, secondAnswer);

                if (firstAnswer.size() >= MIN_OWN_TRIGRAMS
                        && secondAnswer.size() >= MIN_OWN_TRIGRAMS
                        && score >= threshold) {
                    pairs.add(new SimilarityPair(
                            submissions.get(i).getUsername(),
                            submissions.get(j).getUsername(),
                            question.getId(), score));
                }
            }
        }

        return pairs;
    }

    // Reads each student's trigrams for a question, minus the starter's.
    private List<Set<String>> readNewCode(List<Submission> submissions,
            Assessment assessment, Question question, Path starter) {
        Path starterFile = starter.resolve(question.getFolderName())
                .resolve(question.getFileName());
        Set<String> starterTrigrams = readTrigrams(starterFile);
        List<Set<String>> answers = new ArrayList<>();

        for (Submission submission : submissions) {
            Set<String> answer = readTrigrams(findStudentFile(submission,
                    assessment, question));

            answer.removeAll(starterTrigrams);
            answers.add(answer);
        }

        return answers;
    }

    // Finds a student's file for a question, or returns null if it is missing.
    private Path findStudentFile(Submission submission, Assessment assessment,
            Question question) {
        try {
            Path folder = QuestionFolderFinder.find(submission, assessment,
                    question);

            if (folder == null) {
                return null;
            }

            return folder.resolve(question.getFileName());
        } catch (IOException exception) {
            return null;
        }
    }

    // Reads the trigrams of a file, or none when it is missing or unreadable.
    private Set<String> readTrigrams(Path file) {
        if (file == null) {
            return new HashSet<>();
        }

        try {
            String source = new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);

            return buildTrigrams(source);
        } catch (IOException exception) {
            return new HashSet<>();
        }
    }

    // Collects every run of TRIGRAM_LENGTH tokens in a row as one string.
    private Set<String> buildTrigrams(String source) {
        List<String> tokens = splitIntoTokens(source);
        Set<String> trigrams = new HashSet<>();

        for (int i = 0; i + TRIGRAM_LENGTH <= tokens.size(); i++) {
            trigrams.add(TextUtils.join(TOKEN_SEPARATOR,
                    tokens.subList(i, i + TRIGRAM_LENGTH)));
        }

        return trigrams;
    }

    // Divides the number of shared trigrams by the number in either set.
    private double jaccard(Set<String> firstTrigrams,
            Set<String> secondTrigrams) {
        if (firstTrigrams.isEmpty() && secondTrigrams.isEmpty()) {
            return NO_SIMILARITY;
        }

        int sharedCount = 0;

        for (String trigram : firstTrigrams) {
            if (secondTrigrams.contains(trigram)) {
                sharedCount++;
            }
        }

        int unionCount = firstTrigrams.size() + secondTrigrams.size()
                - sharedCount;

        return (double) sharedCount / unionCount;
    }

    // Splits source code into tokens, skipping spaces and comments.
    private List<String> splitIntoTokens(String source) {
        List<String> tokens = new ArrayList<>();
        int start = 0;

        while (start < source.length()) {
            int end = findPieceEnd(source, start);
            String piece = source.substring(start, end);

            if (isToken(piece)) {
                tokens.add(piece);
            }

            start = end;
        }

        return tokens;
    }

    // Returns where the token, space or comment starting at start ends.
    private int findPieceEnd(String source, int start) {
        char first = source.charAt(start);

        if (source.startsWith(LINE_COMMENT, start)) {
            return findEndAfter(source, NEW_LINE, start);
        } else if (source.startsWith(BLOCK_COMMENT_START, start)) {
            return findEndAfter(source, BLOCK_COMMENT_END,
                    start + BLOCK_COMMENT_START.length());
        } else if (first == DOUBLE_QUOTE || first == SINGLE_QUOTE) {
            return findLiteralEnd(source, start);
        } else if (Character.isDigit(first)) {
            return findNumberEnd(source, start);
        } else if (Character.isJavaIdentifierStart(first)) {
            return findWordEnd(source, start);
        }

        return start + 1;
    }

    // Returns the position just after the next marker, or the source end.
    private int findEndAfter(String source, String marker, int from) {
        int markerStart = source.indexOf(marker, from);

        if (markerStart < 0) {
            return source.length();
        }

        return markerStart + marker.length();
    }

    // Returns where a string or char literal ends, after its closing quote.
    private int findLiteralEnd(String source, int start) {
        char quote = source.charAt(start);
        int end = start + 1;

        while (end < source.length() && source.charAt(end) != quote) {
            if (source.charAt(end) == BACKSLASH) {
                end++;
            }

            end++;
        }

        return Math.min(end + 1, source.length());
    }

    // Returns where a number such as 42, 3.14 or 10L ends.
    private int findNumberEnd(String source, int start) {
        int end = start + 1;

        while (end < source.length()
                && (Character.isLetterOrDigit(source.charAt(end))
                || source.charAt(end) == DECIMAL_POINT)) {
            end++;
        }

        return end;
    }

    // Returns where a name or keyword ends.
    private int findWordEnd(String source, int start) {
        int end = start + 1;

        while (end < source.length()
                && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }

        return end;
    }

    // Says whether a piece of source is a token, not a space or a comment.
    private boolean isToken(String piece) {
        return !Character.isWhitespace(piece.charAt(0))
                && !piece.startsWith(LINE_COMMENT)
                && !piece.startsWith(BLOCK_COMMENT_START);
    }

    // Adds a pair before the first pair with a lower similarity.
    private void insertBySimilarity(List<SimilarityPair> pairs,
            SimilarityPair newPair) {
        int position = 0;

        while (position < pairs.size() && pairs.get(position).getSimilarity()
                >= newPair.getSimilarity()) {
            position++;
        }

        pairs.add(position, newPair);
    }
}
