/*
 * File: StructureValidator.java
 *
 * Purpose: Checks the folder layout: a username folder holding one folder per
 * question group.
 *
 * Where it fits: Finds the missing wrapper folder, wrong folder name and
 * missing Qx folder cases described in the brief.
 */
package gradezzy.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Checks the folder layout: a username folder holding one folder per question
 * group.
 */
public class StructureValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Folder structure";

    /**
     * Creates a new structure validator.
     */
    public StructureValidator() {
        super(NAME);
    }

    /**
     * Checks the folder layout: a username folder holding one folder per
     * question group.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        try {
            return checkLayout(submission, assessment);
        } catch (IOException exception) {
            return reportUnreadable(submission, exception);
        }
    }

    // Checks the top folder first, then each question folder inside it.
    private List<Anomaly> checkLayout(Submission submission,
            Assessment assessment) throws IOException {
        List<Anomaly> anomalies = new ArrayList<>();
        Path workingCopy = submission.getWorkingCopyPath();

        if (workingCopy == null) {
            return anomalies;
        }

        Path contentRoot = findContentRoot(submission, assessment);
        List<Path> holders = findHolders(listFolders(workingCopy),
                assessment);

        if (contentRoot == null && holders.size() > 1) {
            // The folders are there, so one card says why none is graded
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                    describeSeveralAnswers(holders)));
            return anomalies;
        } else if (contentRoot == null) {
            addAllFoldersMissing(anomalies, submission, assessment,
                    describeTopLevel(workingCopy, submission));
            return anomalies;
        }

        String username = submission.getUsername();
        String topName = contentRoot.getFileName().toString();

        if (contentRoot.equals(workingCopy)) {
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_WRAPPER_FOLDER, Severity.INFO,
                    "The question folders were at the top of the ZIP file "
                    + "instead of inside a " + username + " folder."));
        } else if (!topName.equals(username)) {
            anomalies.add(createAnomaly(submission,
                    AnomalyType.WRONG_FOLDER_NAME, Severity.WARNING,
                    describeWrongName(topName, username, assessment)));
        }

        addMissingQuestionFolders(anomalies, submission, assessment,
                contentRoot);
        return anomalies;
    }

    // Reports each question folder that is not in the content root.
    private void addMissingQuestionFolders(List<Anomaly> anomalies,
            Submission submission, Assessment assessment, Path contentRoot)
            throws IOException {
        List<Path> folders = listFolders(contentRoot);
        Path extraFolder = findExtraFolder(folders, assessment);
        String nestingHint = describeNesting(contentRoot, extraFolder);
        List<String> nestedNames = listNestedNames(folders, extraFolder,
                assessment);
        boolean firstNested = true;

        for (String folderName : getQuestionFolderNames(assessment)) {
            boolean present = findExact(folders, folderName) != null;
            Path nearMatch = findIgnoringCase(folders, folderName);

            if (!present && nearMatch != null) {
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                        "The " + folderName + " folder is missing. A folder "
                        + "named " + nearMatch.getFileName() + " was found, "
                        + "but folder names must match exactly."));
            } else if (!present && isInside(extraFolder, folderName)) {
                // The fix moves every nested folder at once and keeps only
                // the first card, so that card names them all
                String subject = folderName + " was";
                if (firstNested && nestedNames.size() > 1) {
                    subject = joinNames(nestedNames) + " were";
                }
                firstNested = false;
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_QUESTION_FOLDER,
                        Severity.WARNING, subject + " one level too "
                        + "deep, inside " + contentRoot.getFileName() + "/"
                        + extraFolder.getFileName() + "."));
            } else if (!present) {
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                        "The " + folderName + " folder is missing."
                        + nestingHint));
            }
        }
    }

    // Returns the only folder of the content root when it holds question
    // folders one level too deep, or null.
    private Path findExtraFolder(List<Path> folders, Assessment assessment)
            throws IOException {
        if (folders.size() != 1) {
            return null;
        }

        Path inner = folders.get(0);

        if (!holdsQuestionFolder(listFolders(inner), assessment)) {
            return null;
        }
        return inner;
    }

    // Lists the absent question folders that sit in the extra folder.
    private List<String> listNestedNames(List<Path> folders,
            Path extraFolder, Assessment assessment) throws IOException {
        List<String> names = new ArrayList<>();

        for (String folderName : getQuestionFolderNames(assessment)) {
            boolean absent = findExact(folders, folderName) == null
                    && findIgnoringCase(folders, folderName) == null;
            if (absent && isInside(extraFolder, folderName)) {
                names.add(folderName);
            }
        }
        return names;
    }

    // Joins folder names as "Q1, Q2 and Q3".
    private String joinNames(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }

        int last = names.size() - 1;
        String joined = names.get(0);

        for (int index = 1; index < last; index++) {
            joined = joined + ", " + names.get(index);
        }
        if (last > 0) {
            joined = joined + " and " + names.get(last);
        }
        return joined;
    }

    // Says whether the extra folder holds the question folder.
    private boolean isInside(Path extraFolder, String folderName)
            throws IOException {
        return extraFolder != null
                && findIgnoringCase(listFolders(extraFolder),
                folderName) != null;
    }

    // Explains where the question folders sit one level too deep, or
    // returns empty text.
    private String describeNesting(Path contentRoot, Path extraFolder) {
        if (extraFolder == null) {
            return "";
        }

        return " The question folders were found one level too deep, in "
                + contentRoot.getFileName() + "/" + extraFolder.getFileName()
                + ".";
    }

    // Reports every question folder as missing, with the same reason.
    private void addAllFoldersMissing(List<Anomaly> anomalies,
            Submission submission, Assessment assessment, String reason) {
        for (String folderName : getQuestionFolderNames(assessment)) {
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                    "The " + folderName + " folder is missing. " + reason));
        }
    }

    // Says that several folders hold question folders, so GradeZZY cannot
    // tell which one is the answer.
    private String describeSeveralAnswers(List<Path> holders) {
        List<String> names = new ArrayList<>();

        for (Path holder : holders) {
            names.add(holder.getFileName().toString());
        }
        return "The ZIP file holds " + holders.size() + " folders with the "
                + "question folders in them (" + joinNames(names) + "), so "
                + "GradeZZY cannot tell which one to grade. Ask the student "
                + "which one is their answer, then grade it again.";
    }

    // Explains why no top folder could be used: none, or too many.
    private String describeTopLevel(Path workingCopy,
            Submission submission) throws IOException {
        List<Path> topFolders = listFolders(workingCopy);

        if (topFolders.isEmpty()) {
            return "The ZIP file holds no folder.";
        }

        String names = topFolders.get(0).getFileName().toString();

        for (int i = 1; i < topFolders.size(); i++) {
            names += ", " + topFolders.get(i).getFileName();
        }

        return "The ZIP file holds several folders (" + names + ") and "
                + "none is named " + submission.getUsername() + ".";
    }

    // Says what the top folder was called and why that is wrong.
    private String describeWrongName(String topName, String username,
            Assessment assessment) {
        String message = "The top folder is named " + topName
                + " instead of " + username + ".";

        if (assessment.getPlaceholderFolderNames().contains(topName)) {
            message += " This is the starter folder name, which the student "
                    + "should have renamed.";
        }

        return message;
    }
}
