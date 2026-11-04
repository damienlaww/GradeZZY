/*
 * File: HeaderNameValidator.java
 *
 * Purpose: Checks that each Java file has the student name and email in its
 * header comment.
 *
 * Where it fits: Finds blank headers so HeaderNameFixer can fill them in.
 */
package gradezzy.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.util.TextUtils;

/**
 * Checks that each Java file has the student name and email in its header
 * comment. Only the required files of the assessment are checked, as
 * agreed by the team; extra helper classes are not.
 */
public class HeaderNameValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Header name";

    // The character every line ending becomes after normalising
    private static final String NEW_LINE = "\n";

    // The application settings, or null when none were given
    private final AppConfig config;

    /**
     * Creates a header name validator without settings. It reports nothing,
     * because the labels and the number of lines to search come from the
     * settings. Kept only until PipelineFactory passes the settings to the
     * other constructor.
     */
    public HeaderNameValidator() {
        this(null);
    }

    /**
     * Creates a header name validator that reads the header labels and the
     * number of lines to search from the settings.
     *
     * @param config the application settings
     */
    public HeaderNameValidator(AppConfig config) {
        super(NAME);
        this.config = config;
    }

    /**
     * Checks that each Java file has the student name and email in its header
     * comment.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        if (config == null) {
            return new ArrayList<>();
        }

        try {
            return checkHeaders(submission, assessment);
        } catch (IOException exception) {
            return reportUnreadable(submission, exception);
        }
    }

    // Checks the header of each required file that is present.
    private List<Anomaly> checkHeaders(Submission submission,
            Assessment assessment) throws IOException {
        List<Anomaly> anomalies = new ArrayList<>();
        Path contentRoot = findContentRoot(submission, assessment);

        if (contentRoot == null) {
            return anomalies;
        }

        for (Question question : assessment.getQuestions()) {
            Path file = findSourceFile(contentRoot, question);
            String problem = null;

            if (file != null) {
                problem = findProblem(readHeader(file));
            }

            if (problem != null) {
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_HEADER_NAME, Severity.WARNING,
                        question.getFolderName() + "/"
                        + question.getFileName() + " has " + problem
                        + " in its header."));
            }
        }

        return anomalies;
    }

    // Finds the question's file with its exact name, or returns null. A
    // missing or misnamed file is reported by the other validators.
    private Path findSourceFile(Path contentRoot,
            Question question) throws IOException {
        Path folder = findQuestionFolder(contentRoot, question);

        if (folder == null) {
            return null;
        }

        return findExact(listFiles(folder), question.getFileName());
    }

    // Reads the first HEADER_SCAN_LINES lines of a file. The bytes are
    // decoded so that a character that is not valid UTF-8 is replaced
    // instead of stopping the check.
    private List<String> readHeader(Path file) throws IOException {
        int scanLines = config.getInt(ConfigKeys.HEADER_SCAN_LINES);
        String text = new String(Files.readAllBytes(file),
                StandardCharsets.UTF_8);
        String[] lines = TextUtils.normaliseLineEndings(text)
                .split(NEW_LINE);
        List<String> header = new ArrayList<>();

        for (int i = 0; i < lines.length && i < scanLines; i++) {
            header.add(lines[i]);
        }

        return header;
    }

    // Describes what is missing from the header, or returns null when both
    // the name and the email are filled in.
    private String findProblem(List<String> header) {
        String nameLabel = config.getString(ConfigKeys.HEADER_NAME_LABEL);
        String emailLabel = config.getString(ConfigKeys.HEADER_EMAIL_LABEL);
        String nameProblem = describeField(header, nameLabel, "name");
        String emailProblem = describeField(header, emailLabel, "email");

        if (nameProblem != null && emailProblem != null) {
            return nameProblem + " and " + emailProblem;
        } else if (nameProblem != null) {
            return nameProblem;
        }

        return emailProblem;
    }

    // Describes one missing field, or returns null when it is filled in.
    private String describeField(List<String> header, String label,
            String fieldName) {
        String value = findValueAfter(header, label);

        if (value == null) {
            return "no " + label + " line";
        } else if (TextUtils.isBlank(value)) {
            return "no " + fieldName + " after " + label;
        }

        return null;
    }

    // Returns the text after the first occurrence of the label, trimmed,
    // or null when no line holds the label.
    private String findValueAfter(List<String> header, String label) {
        for (String line : header) {
            int position = line.indexOf(label);

            if (position >= 0) {
                return line.substring(position + label.length()).trim();
            }
        }

        return null;
    }
}
