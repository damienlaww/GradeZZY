/*
 * File: TokenSimilarityDetector.java
 *
 * Purpose: Compares submissions by the tokens of their source code,
 * split by JavaTokenizer.
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
 *
 * <p>Tier: COULD. Owner: Jun Sheng.
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

    // Finds the pairs of students whose new code for a question is alike.
    private List<SimilarityPair> compareQuestion(List<Submission> submissions,
            Assessment assessment, Question question, Path starter,
            double threshold) {
        List<Integer> lineCounts = new ArrayList<>();
        List<Set<String>> answers = readNewCode(submissions, assessment,
                question, starter, lineCounts);
        int minLines = config.getInt(ConfigKeys.PLAGIARISM_MIN_LINES);
        List<SimilarityPair> pairs = new ArrayList<>();

        for (int i = 0; i < submissions.size(); i++) {
            for (int j = i + 1; j < submissions.size(); j++) {
                Set<String> firstAnswer = answers.get(i);
                Set<String> secondAnswer = answers.get(j);
                double score = jaccard(firstAnswer, secondAnswer);
                boolean enoughCode = firstAnswer.size() >= MIN_OWN_TRIGRAMS
                        && secondAnswer.size() >= MIN_OWN_TRIGRAMS
                        && lineCounts.get(i) >= minLines
                        && lineCounts.get(j) >= minLines;

                if (enoughCode && score >= threshold) {
                    String firstUsername = submissions.get(i).getUsername();
                    String secondUsername = submissions.get(j).getUsername();
                    pairs.add(new SimilarityPair(firstUsername,
                            secondUsername, question.getId(), score));
                }
            }
        }

        return pairs;
    }

    // Reads each student's trigrams for a question, minus the starter's,
    // and puts each one's number of new lines of code in lineCounts. The
    // question's other source files, such as GoldMember.java, count as
    // part of its answer.
    private List<Set<String>> readNewCode(List<Submission> submissions,
            Assessment assessment, Question question, Path starter,
            List<Integer> lineCounts) {
        Path starterFolder = starter.resolve(question.getFolderName());
        Set<String> starterTrigrams = readQuestionTrigrams(starterFolder,
                question);
        Set<String> starterLines = CodeLineCounter.readLines(starterFolder,
                question);
        List<Set<String>> answers = new ArrayList<>();

        for (Submission submission : submissions) {
            Path studentFolder = findStudentFolder(submission, assessment,
                    question);
            Set<String> answer = readQuestionTrigrams(studentFolder,
                    question);

            answer.removeAll(starterTrigrams);
            answers.add(answer);
            lineCounts.add(CodeLineCounter.countNewLines(studentFolder,
                    question, starterLines));
        }

        return answers;
    }

    // Reads the trigrams of every file the student writes for a question
    // in one folder, or none when the folder is missing.
    private Set<String> readQuestionTrigrams(Path folder, Question question) {
        Set<String> trigrams = new HashSet<>();
        if (folder == null) {
            return trigrams;
        }

        Path answerFile = folder.resolve(question.getFileName());
        trigrams.addAll(readTrigrams(answerFile));
        for (String extraFile : question.getExtraFileNames()) {
            Path extraPath = folder.resolve(extraFile);
            trigrams.addAll(readTrigrams(extraPath));
        }
        return trigrams;
    }

    // Finds a student's folder for a question, or returns null if it is
    // missing.
    private Path findStudentFolder(Submission submission,
            Assessment assessment, Question question) {
        try {
            return QuestionFolderFinder.find(submission, assessment,
                    question);
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
        List<String> tokens = JavaTokenizer.split(source);
        Set<String> trigrams = new HashSet<>();

        for (int i = 0; i + TRIGRAM_LENGTH <= tokens.size(); i++) {
            int end = i + TRIGRAM_LENGTH;
            List<String> trigram = tokens.subList(i, end);
            trigrams.add(TextUtils.join(TOKEN_SEPARATOR, trigram));
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

    // Adds a pair before the first pair with a lower similarity.
    private void insertBySimilarity(List<SimilarityPair> pairs,
            SimilarityPair newPair) {
        int position = 0;

        while (isAtLeastAsSimilar(pairs, position, newPair)) {
            position++;
        }

        pairs.add(position, newPair);
    }

    // Says whether there is a pair at a position and it is at least as
    // similar as the new pair, so the new pair goes after it.
    private boolean isAtLeastAsSimilar(List<SimilarityPair> pairs,
            int position, SimilarityPair newPair) {
        if (position >= pairs.size()) {
            return false;
        }
        double similarity = pairs.get(position).getSimilarity();
        return similarity >= newPair.getSimilarity();
    }
}
