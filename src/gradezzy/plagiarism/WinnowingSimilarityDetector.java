/*
 * File: WinnowingSimilarityDetector.java
 *
 * Purpose: Compares submissions by winnowing fingerprints, so moving
 * methods does not hide copied code, and renaming does not hide it
 * either when the copied code is not written the same way by others.
 *
 * Where it fits: Used by PlagiarismService when plagiarism.algorithm is
 * winnowing, the default. TokenSimilarityDetector (trigrams) is the
 * first version, kept so the two can be compared.
 */
package gradezzy.plagiarism;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;

/**
 * Compares submissions by winnowing fingerprints, in two ways.
 *
 * <p>Winnowing picks fingerprints from the hashes of each run of
 * Winnowing.KGRAM_LENGTH tokens, wherever the run is in the file.
 * Fingerprints that are also k-grams of the starter file are removed,
 * and two answers are scored with the Jaccard measure: shared
 * fingerprints divided by all fingerprints. A pair's score is the
 * higher of:
 *
 * <ul>
 * <li>the tokens as written, so a copy with its methods moved and a
 * few names changed still matches, but honest answers with their own
 * names do not;</li>
 * <li>rename-proof tokens, where every name is ID and every text
 * literal STR, counting only the fingerprints no more than
 * MAX_SHARING_STUDENTS answers to the question hold. With names
 * ignored, honest students who follow the same plan write the same
 * code, so code many of them share proves nothing; code only the two
 * of them share does.</li>
 * </ul>
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class WinnowingSimilarityDetector implements SimilarityDetector {

    /**
     * The name of the algorithm.
     */
    public static final String NAME = "Rename-proof winnowing";

    // The fewest new rename-proof fingerprints each answer needs before
    // a pair is scored. Short correct answers (about 15 to 24
    // fingerprints) are often written the same way by honest students,
    // so a match on them proves nothing.
    private static final int MIN_OWN_FINGERPRINTS = 25;

    // The most answers to a question a rename-proof fingerprint may be
    // in and still count as unusual. Two: the pair being compared.
    private static final int MAX_SHARING_STUDENTS = 2;

    // The fewest unusual rename-proof fingerprints each answer needs
    // before its rename-proof score counts; a few matching lines are
    // not enough to call two answers alike
    private static final int MIN_UNUSUAL_FINGERPRINTS = 10;

    // How many k-gram hashes one window covers for the tokens as
    // written: smaller than Winnowing.WINDOW_SIZE, so a short answer
    // keeps enough fingerprints that one renamed loop variable does not
    // hide a copy
    private static final int WRITTEN_WINDOW_SIZE = 2;

    // The similarity of two answers that share nothing
    private static final double NO_SIMILARITY = 0.0;

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new winnowing similarity detector.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     */
    public WinnowingSimilarityDetector(AppConfig config) {
        this.config = config;
    }

    /**
     * Returns the name of the algorithm.
     *
     * <p>Tier: COULD.
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
     * <p>Tier: COULD.
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
     * <p>Tier: COULD.
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
        Path starterFolder = starter.resolve(question.getFolderName());
        List<Path> studentFolders = new ArrayList<>();
        for (Submission submission : submissions) {
            studentFolders.add(findStudentFolder(submission, assessment,
                    question));
        }
        List<Set<Integer>> written = readNewCode(studentFolders,
                starterFolder, question, false);
        List<Set<Integer>> renameProof = readNewCode(studentFolders,
                starterFolder, question, true);
        List<Set<Integer>> unusual = keepUnusual(renameProof);
        List<Integer> lineCounts = countNewLines(studentFolders,
                starterFolder, question);
        int minLines = config.getInt(ConfigKeys.PLAGIARISM_MIN_LINES);
        List<SimilarityPair> pairs = new ArrayList<>();

        for (int i = 0; i < submissions.size(); i++) {
            for (int j = i + 1; j < submissions.size(); j++) {
                boolean bothLongEnough =
                        renameProof.get(i).size() >= MIN_OWN_FINGERPRINTS
                        && renameProof.get(j).size() >= MIN_OWN_FINGERPRINTS
                        && lineCounts.get(i) >= minLines
                        && lineCounts.get(j) >= minLines;
                double writtenScore = jaccard(written.get(i),
                        written.get(j));
                double unusualScore = scoreUnusual(unusual.get(i),
                        unusual.get(j));
                double score = Math.max(writtenScore, unusualScore);

                if (bothLongEnough && score >= threshold) {
                    pairs.add(new SimilarityPair(
                            submissions.get(i).getUsername(),
                            submissions.get(j).getUsername(),
                            question.getId(), score));
                }
            }
        }

        return pairs;
    }

    // Reads each student's fingerprints for a question, as written or
    // rename-proof, minus those that are k-grams of the starter. The
    // question's other source files, such as GoldMember.java, count as
    // part of its answer. A missing folder gives no fingerprints.
    private List<Set<Integer>> readNewCode(List<Path> studentFolders,
            Path starterFolder, Question question, boolean renameProof) {
        Set<Integer> starterKGrams = readKGrams(starterFolder, question,
                renameProof);
        List<Set<Integer>> answers = new ArrayList<>();

        for (Path studentFolder : studentFolders) {
            Set<Integer> answer = readFingerprints(studentFolder, question,
                    renameProof);

            answer.removeAll(starterKGrams);
            answers.add(answer);
        }

        return answers;
    }

    // Counts each student's lines of new code for a question.
    private List<Integer> countNewLines(List<Path> studentFolders,
            Path starterFolder, Question question) {
        Set<String> starterLines = CodeLineCounter.readLines(starterFolder,
                question);
        List<Integer> lineCounts = new ArrayList<>();

        for (Path studentFolder : studentFolders) {
            lineCounts.add(CodeLineCounter.countNewLines(studentFolder,
                    question, starterLines));
        }

        return lineCounts;
    }

    // Keeps, of each answer's fingerprints, those in no more than
    // MAX_SHARING_STUDENTS answers, leaving the code that is the
    // student's own or shared with one other student.
    private List<Set<Integer>> keepUnusual(List<Set<Integer>> answers) {
        Map<Integer, Integer> answerCounts = new HashMap<>();
        for (Set<Integer> answer : answers) {
            for (Integer hash : answer) {
                Integer count = answerCounts.get(hash);
                if (count == null) {
                    count = 0;
                }
                answerCounts.put(hash, count + 1);
            }
        }

        List<Set<Integer>> unusualAnswers = new ArrayList<>();
        for (Set<Integer> answer : answers) {
            Set<Integer> unusual = new HashSet<>();
            for (Integer hash : answer) {
                if (answerCounts.get(hash) <= MAX_SHARING_STUDENTS) {
                    unusual.add(hash);
                }
            }
            unusualAnswers.add(unusual);
        }

        return unusualAnswers;
    }

    // Scores two answers by their unusual rename-proof fingerprints, or
    // gives no similarity when either has too few to judge.
    private double scoreUnusual(Set<Integer> firstHashes,
            Set<Integer> secondHashes) {
        boolean enough = firstHashes.size() >= MIN_UNUSUAL_FINGERPRINTS
                && secondHashes.size() >= MIN_UNUSUAL_FINGERPRINTS;
        if (!enough) {
            return NO_SIMILARITY;
        }
        return jaccard(firstHashes, secondHashes);
    }

    // Reads the hash of every k-gram in the files of a question in one
    // folder, such as the starter's, as written or rename-proof.
    private Set<Integer> readKGrams(Path folder, Question question,
            boolean renameProof) {
        Set<Integer> hashes = new HashSet<>();
        for (Path file : listAnswerFiles(folder, question)) {
            List<String> tokens = readTokens(file, renameProof);
            hashes.addAll(Winnowing.hashKGrams(tokens,
                    Winnowing.KGRAM_LENGTH));
        }
        return hashes;
    }

    // Reads the hashes of the fingerprints winnowing picks in the files
    // of a question in one folder, as written or rename-proof. Each file
    // is winnowed on its own, so no k-gram runs from the end of one file
    // into the next.
    private Set<Integer> readFingerprints(Path folder, Question question,
            boolean renameProof) {
        int windowSize = WRITTEN_WINDOW_SIZE;
        if (renameProof) {
            windowSize = Winnowing.WINDOW_SIZE;
        }
        Set<Integer> hashes = new HashSet<>();
        for (Path file : listAnswerFiles(folder, question)) {
            List<String> tokens = readTokens(file, renameProof);
            List<Integer> kGrams = Winnowing.hashKGrams(tokens,
                    Winnowing.KGRAM_LENGTH);
            List<Fingerprint> fingerprints = Winnowing.selectFingerprints(
                    kGrams, windowSize);

            for (Fingerprint fingerprint : fingerprints) {
                hashes.add(fingerprint.getHash());
            }
        }
        return hashes;
    }

    // Lists the files of a question in one folder: the answer file and
    // the question's other source files. Empty when the folder is
    // missing.
    private List<Path> listAnswerFiles(Path folder, Question question) {
        List<Path> files = new ArrayList<>();
        if (folder == null) {
            return files;
        }

        files.add(folder.resolve(question.getFileName()));
        for (String extraFile : question.getExtraFileNames()) {
            files.add(folder.resolve(extraFile));
        }
        return files;
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

    // Reads the tokens of a file, as written or rename-proof, or none
    // when it is missing or unreadable.
    private List<String> readTokens(Path file, boolean renameProof) {
        List<String> texts = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return texts;
        }

        try {
            String source = new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);
            List<JavaToken> tokens = JavaTokenizer.tokenize(source);
            if (renameProof) {
                return Winnowing.normalise(tokens);
            }
            for (JavaToken token : tokens) {
                texts.add(token.getText());
            }
            return texts;
        } catch (IOException exception) {
            return texts;
        }
    }

    // Divides the number of shared fingerprints by the number in either
    // set.
    private double jaccard(Set<Integer> firstHashes,
            Set<Integer> secondHashes) {
        if (firstHashes.isEmpty() && secondHashes.isEmpty()) {
            return NO_SIMILARITY;
        }

        int sharedCount = 0;

        for (Integer hash : firstHashes) {
            if (secondHashes.contains(hash)) {
                sharedCount++;
            }
        }

        int unionCount = firstHashes.size() + secondHashes.size()
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
