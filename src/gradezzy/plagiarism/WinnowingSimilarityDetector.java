/*
 * File: WinnowingSimilarityDetector.java
 *
 * Purpose: Compares submissions by the winnowing fingerprints of their
 * rename-proof tokens, so renaming variables or moving methods does not
 * hide copied code.
 *
 * Where it fits: Used by PlagiarismService when plagiarism.algorithm is
 * winnowing. TokenSimilarityDetector (trigrams) is the first version
 * and the default, kept so the two can be compared.
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

/**
 * Compares submissions by the winnowing fingerprints of their
 * rename-proof tokens.
 *
 * <p>Each answer file is split into tokens; every name becomes ID and
 * every text literal STR. Winnowing picks fingerprints from the hashes
 * of each run of Winnowing.KGRAM_LENGTH tokens. Fingerprints that are
 * also k-grams of the starter file are removed, and two answers are
 * scored with the Jaccard measure: shared fingerprints divided by all
 * fingerprints.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class WinnowingSimilarityDetector implements SimilarityDetector {

    /**
     * The name of the algorithm.
     */
    public static final String NAME = "Rename-proof winnowing";

    // The fewest new fingerprints each answer needs before a pair is
    // scored. Once names are ignored, short correct answers (about 15
    // to 24 fingerprints) are often written the same way by honest
    // students, so a match on them proves nothing.
    private static final int MIN_OWN_FINGERPRINTS = 25;

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
        List<Set<Integer>> answers = readNewCode(submissions, assessment,
                question, starter);
        List<SimilarityPair> pairs = new ArrayList<>();

        for (int i = 0; i < submissions.size(); i++) {
            for (int j = i + 1; j < submissions.size(); j++) {
                Set<Integer> firstAnswer = answers.get(i);
                Set<Integer> secondAnswer = answers.get(j);
                double score = jaccard(firstAnswer, secondAnswer);
                boolean bothLongEnough =
                        firstAnswer.size() >= MIN_OWN_FINGERPRINTS
                        && secondAnswer.size() >= MIN_OWN_FINGERPRINTS;

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

    // Reads each student's fingerprints for a question, minus those that
    // are k-grams of the starter. The question's other source files,
    // such as GoldMember.java, count as part of its answer.
    private List<Set<Integer>> readNewCode(List<Submission> submissions,
            Assessment assessment, Question question, Path starter) {
        Path starterFolder = starter.resolve(question.getFolderName());
        Set<Integer> starterKGrams = readKGrams(starterFolder, question);
        List<Set<Integer>> answers = new ArrayList<>();

        for (Submission submission : submissions) {
            Path studentFolder = findStudentFolder(submission, assessment,
                    question);
            Set<Integer> answer = readFingerprints(studentFolder, question);

            answer.removeAll(starterKGrams);
            answers.add(answer);
        }

        return answers;
    }

    // Reads the hash of every k-gram in the files of a question in one
    // folder, such as the starter's.
    private Set<Integer> readKGrams(Path folder, Question question) {
        Set<Integer> hashes = new HashSet<>();
        for (Path file : listAnswerFiles(folder, question)) {
            List<String> shapes = readShapes(file);
            hashes.addAll(Winnowing.hashKGrams(shapes,
                    Winnowing.KGRAM_LENGTH));
        }
        return hashes;
    }

    // Reads the hashes of the fingerprints winnowing picks in the files
    // of a question in one folder. Each file is winnowed on its own, so
    // no k-gram runs from the end of one file into the next.
    private Set<Integer> readFingerprints(Path folder, Question question) {
        Set<Integer> hashes = new HashSet<>();
        for (Path file : listAnswerFiles(folder, question)) {
            List<String> shapes = readShapes(file);
            List<Integer> kGrams = Winnowing.hashKGrams(shapes,
                    Winnowing.KGRAM_LENGTH);
            List<Fingerprint> fingerprints = Winnowing.selectFingerprints(
                    kGrams, Winnowing.WINDOW_SIZE);

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

    // Reads the rename-proof tokens of a file, or none when it is missing
    // or unreadable.
    private List<String> readShapes(Path file) {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }

        try {
            String source = new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);

            return Winnowing.normalise(JavaTokenizer.tokenize(source));
        } catch (IOException exception) {
            return new ArrayList<>();
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
