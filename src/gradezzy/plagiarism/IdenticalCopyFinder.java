/*
 * File: IdenticalCopyFinder.java
 *
 * Purpose: Finds the answer files whose headers give away that one was
 * handed in as another's work. Two files with the same Email ID in their
 * header are a "same Email ID" pair. When their Name and their code are
 * the same too, the file was passed on whole: an identical copy.
 *
 * Where it fits: PlagiarismService adds these pairs to the ones the
 * similarity check finds and marks them. They are always found, whatever
 * the settings say: even when the answer is shorter than
 * plagiarism.min.lines, less alike than the threshold, or the similarity
 * check is switched off.
 */
package gradezzy.plagiarism;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;
import gradezzy.validation.HeaderNameValidator;

/**
 * Finds pairs of answer files with the same Email ID in their header,
 * and among them the identical copies.
 *
 * <p>Tier: COULD.
 */
public class IdenticalCopyFinder {

    // The similarity of an identical copy
    private static final double IDENTICAL = 1.0;

    // The similarity of two files with no fingerprint at all
    private static final double NO_SIMILARITY = 0.0;

    // Ends the username part of an email address
    private static final String EMAIL_AT = "@";

    // Returned by indexOf when the text is not found
    private static final int NOT_FOUND = -1;

    // Any run of spaces between the words of a name
    private static final String SPACES = "\\s+";

    // Put between the words of a name and between tokens
    private static final String SPACE = " ";

    // The character every line ending becomes after normalising
    private static final String NEW_LINE = "\n";

    // The application settings, which name the header labels
    private final AppConfig config;

    /**
     * Creates the finder.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     */
    public IdenticalCopyFinder(AppConfig config) {
        this.config = config;
    }

    /**
     * Finds, question by question, every two students whose answer files
     * give the same Email ID in their header. A pair whose files also
     * have the same Name and the same code is an identical copy, at
     * 100%; spaces and comments do not count, every other token must be
     * the same. Any other pair is scored by how alike its code is once
     * names are set aside, as winnowing does. A file whose header gives
     * no Email ID is left out.
     *
     * <p>Tier: COULD.
     *
     * @param submissions the finished submissions of a run
     * @param assessment the run's assessment
     * @return the pairs, each marked as sharing an Email ID
     */
    public List<SimilarityPair> find(List<Submission> submissions,
            Assessment assessment) {
        List<SimilarityPair> pairs = new ArrayList<>();
        if (assessment == null) {
            return pairs;
        }

        for (Question question : assessment.getQuestions()) {
            addPairs(submissions, assessment, question, pairs);
        }
        return pairs;
    }

    // Adds the pairs of one question.
    private void addPairs(List<Submission> submissions,
            Assessment assessment, Question question,
            List<SimilarityPair> pairs) {
        List<AnswerHeader> answers = new ArrayList<>();
        for (Submission submission : submissions) {
            AnswerHeader answer = readAnswer(submission, assessment,
                    question);
            if (answer != null) {
                addMatches(answers, answer, question, pairs);
                answers.add(answer);
            }
        }
    }

    // Pairs an answer with every earlier answer of the same Email ID.
    private void addMatches(List<AnswerHeader> answers,
            AnswerHeader answer, Question question,
            List<SimilarityPair> pairs) {
        for (AnswerHeader earlier : answers) {
            if (sameEmail(earlier.getEmail(), answer.getEmail())) {
                pairs.add(pairOf(earlier, answer, question));
            }
        }
    }

    // Makes the pair of two answers with the same Email ID: an identical
    // copy, or a pair scored by how alike its code is.
    private SimilarityPair pairOf(AnswerHeader first, AnswerHeader second,
            Question question) {
        boolean identical = !first.getName().isEmpty()
                && first.getName().equals(second.getName())
                && first.getCode().equals(second.getCode());
        if (identical) {
            return new SimilarityPair(first.getUsername(),
                    second.getUsername(), question.getId(), IDENTICAL, true,
                    true);
        }
        double similarity = jaccard(first.getFingerprints(),
                second.getFingerprints());
        return new SimilarityPair(first.getUsername(), second.getUsername(),
                question.getId(), similarity, false, true);
    }

    // Reads a student's answer file: its header's name and Email ID, its
    // code and its fingerprints. Null when the file is missing,
    // unreadable, or its header gives no Email ID.
    private AnswerHeader readAnswer(Submission submission,
            Assessment assessment, Question question) {
        String source = readSource(submission, assessment, question);
        if (source.isEmpty()) {
            return null;
        }

        List<String> header = firstLines(source);
        String email = normaliseEmail(findValueAfter(header,
                config.getString(ConfigKeys.HEADER_EMAIL_LABEL)));
        if (email.isEmpty()) {
            return null;
        }

        String name = normaliseName(findValueAfter(header,
                config.getString(ConfigKeys.HEADER_NAME_LABEL)));
        List<JavaToken> tokens = JavaTokenizer.tokenize(source);
        String code = TextUtils.join(SPACE, JavaTokenizer.split(source));
        Set<Integer> fingerprints = fingerprintsOf(
                Winnowing.normalise(tokens));
        return new AnswerHeader(submission.getUsername(), email, name, code,
                fingerprints);
    }

    // Returns the hashes of the fingerprints winnowing picks in a file's
    // rename-proof tokens.
    private Set<Integer> fingerprintsOf(List<String> shapes) {
        Set<Integer> hashes = new HashSet<>();
        List<Integer> kGrams = Winnowing.hashKGrams(shapes,
                Winnowing.KGRAM_LENGTH);
        for (Fingerprint fingerprint : Winnowing.selectFingerprints(kGrams,
                Winnowing.WINDOW_SIZE)) {
            hashes.add(fingerprint.getHash());
        }
        return hashes;
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

    // Reads the text of a student's answer file, or "" when it is
    // missing or cannot be read.
    private String readSource(Submission submission, Assessment assessment,
            Question question) {
        try {
            Path folder = QuestionFolderFinder.find(submission, assessment,
                    question);
            if (folder == null) {
                return "";
            }
            Path file = folder.resolve(question.getFileName());
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return "";
            }
            return TextUtils.normaliseLineEndings(FileUtils.readText(file));
        } catch (IOException exception) {
            return "";
        }
    }

    // Returns the first lines of a file, as far as headers are looked for.
    private List<String> firstLines(String source) {
        List<String> header = new ArrayList<>();
        int scanLines = config.getInt(ConfigKeys.HEADER_SCAN_LINES);
        String[] lines = source.split(NEW_LINE);
        for (int i = 0; i < lines.length && i < scanLines; i++) {
            header.add(lines[i]);
        }
        return header;
    }

    // Returns the text after a label on the first header line that has
    // it, or "" when no line has it. The label is found the way
    // HeaderNameValidator finds it, so "Email ID : x" counts too.
    private String findValueAfter(List<String> header, String label) {
        for (String line : header) {
            int valueStart = HeaderNameValidator.findValueStart(line, label);
            if (valueStart >= 0) {
                return line.substring(valueStart).trim();
            }
        }
        return "";
    }

    // Returns an email address trimmed and in lower case.
    private String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Tells whether two normalised Email IDs belong to the same person.
    // When both give a domain, the whole address must match, so
    // bo@smu.edu.sg and bo@gmail.com are different people. When one of
    // them gives no domain (a student who wrote only "bo"), only the
    // usernames can be compared, so those are compared.
    private boolean sameEmail(String first, String second) {
        boolean bothHaveDomain = first.contains(EMAIL_AT)
                && second.contains(EMAIL_AT);
        if (bothHaveDomain) {
            return first.equals(second);
        }
        return usernameOf(first).equals(usernameOf(second));
    }

    // Returns the part of an email address before the @, trimmed.
    private String usernameOf(String email) {
        String user = email;
        int at = email.indexOf(EMAIL_AT);
        if (at != NOT_FOUND) {
            user = email.substring(0, at);
        }
        return user.trim();
    }

    // Returns a name in lower case with single spaces between its words.
    private String normaliseName(String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        return TextUtils.join(SPACE, trimmed.split(SPACES));
    }
}
