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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.util.AnswerFolderRule;
import gradezzy.util.TextUtils;

/**
 * Checks the folder layout: a username folder holding one folder per question
 * group.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class StructureValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Folder structure";

    // How the top of an upload is named in a message: a ZIP file, or a
    // folder unzipped by hand
    private static final String ZIP_TOP = "the ZIP file";
    private static final String FOLDER_TOP = "the uploaded folder";

    // Separates a folder and the folder inside it in a message
    private static final String FOLDER_SEPARATOR = "/";

    // Comes between two folder names in a list in a message
    private static final String LIST_SEPARATOR = ", ";

    // Names the top of the upload among the places that hold question
    // folders
    private static final String TOP_LEVEL_NAME = "its top level";

    // The depth, below the content root, of an extra folder that holds
    // the question folders itself
    private static final int ONE_LEVEL = 1;

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
        List<Path> holders = AnswerFolderRule.findAnswerHolders(workingCopy,
                getQuestionFolderNames(assessment));

        if (contentRoot == null && holders.size() > 1) {
            // The folders are there, so one card says why none is graded
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                    describeSeveralAnswers(holders, submission)));
            return anomalies;
        } else if (contentRoot == null) {
            addAllFoldersMissing(anomalies, submission, assessment,
                    describeTopLevel(workingCopy, submission));
            return anomalies;
        }

        String username = submission.getUsername();
        String topName = contentRoot.getFileName().toString();

        if (contentRoot.equals(workingCopy)) {
            String message = "The question folders were at the top of "
                    + describeUpload(submission) + " instead of inside a "
                    + "folder named " + username + ".";
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_WRAPPER_FOLDER, Severity.INFO,
                    message));
        } else if (!topName.equals(username)) {
            anomalies.add(createAnomaly(submission,
                    AnomalyType.WRONG_FOLDER_NAME, Severity.WARNING,
                    describeWrongName(topName, username, assessment)));
        }

        addMissingQuestionFolders(anomalies, submission, assessment,
                contentRoot);
        return anomalies;
    }

    // Reports each question folder that is not in the content root, or
    // that sits inside a folder of its own name, such as Q1/Q1.
    private void addMissingQuestionFolders(List<Anomaly> anomalies,
            Submission submission, Assessment assessment, Path contentRoot)
            throws IOException {
        List<Path> folders = listFolders(contentRoot);
        Path extraFolder = AnswerFolderRule.findExtraFolder(contentRoot,
                getQuestionFolderNames(assessment));
        Path chainEnd = null;
        if (extraFolder != null) {
            chainEnd = AnswerFolderRule.findChainEnd(extraFolder,
                    getQuestionFolderNames(assessment));
        }
        String nestingHint = describeNesting(contentRoot, chainEnd);
        List<String> nestedNames = listNestedNames(folders, chainEnd,
                assessment);
        boolean firstNested = true;

        for (String folderName : getQuestionFolderNames(assessment)) {
            Path present = findExact(folders, folderName);
            Path nearMatch = findIgnoringCase(folders, folderName);

            if (present != null) {
                addIfInsideItself(anomalies, submission, contentRoot,
                        present, folderName);
            } else if (nearMatch != null) {
                String message = "The " + folderName + " folder is missing. "
                        + "A folder named " + nearMatch.getFileName()
                        + " was found, but folder names must match exactly.";
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                        message));
            } else if (isInside(chainEnd, folderName)) {
                // The fix moves every nested folder at once and keeps only
                // the first card, so that card names them all
                String subject = folderName + " was";
                if (firstNested && nestedNames.size() > 1) {
                    subject = joinNames(nestedNames) + " were";
                }
                firstNested = false;
                String message = subject + " " + describeDepth(contentRoot,
                        chainEnd) + " too deep, inside "
                        + showPath(contentRoot, chainEnd) + ".";
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_QUESTION_FOLDER,
                        Severity.WARNING, message));
            } else {
                String message = "The " + folderName + " folder is missing."
                        + nestingHint;
                anomalies.add(createAnomaly(submission,
                        AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                        message));
            }
        }
    }

    // Reports a question folder that holds only a folder of its own name,
    // as when Q1 was dragged into Q1. FolderHierarchyFixer moves the inner
    // one up.
    private void addIfInsideItself(List<Anomaly> anomalies,
            Submission submission, Path contentRoot, Path questionFolder,
            String folderName) throws IOException {
        Path inner = findNestedQuestionFolder(questionFolder, folderName);
        if (inner == null) {
            return;
        }

        String message = folderName + " was one level too deep, inside "
                + showPath(contentRoot, questionFolder) + ".";
        anomalies.add(createAnomaly(submission,
                AnomalyType.MISSING_QUESTION_FOLDER, Severity.WARNING,
                message));
    }

    // Says how deep the question folders sit below the content root:
    // "one level" or, for a chain of folders, "3 levels".
    private String describeDepth(Path contentRoot, Path chainEnd) {
        int levels = contentRoot.relativize(chainEnd).getNameCount();
        if (levels == ONE_LEVEL) {
            return "one level";
        }
        return levels + " levels";
    }

    // Writes a folder below the content root as content root, then each
    // folder down to it, such as ping.lee.2023/src.
    private String showPath(Path contentRoot, Path folder) {
        String shown = contentRoot.getFileName().toString();
        for (Path part : contentRoot.relativize(folder)) {
            shown = shown + FOLDER_SEPARATOR + part;
        }
        return shown;
    }

    // Shows where a folder is inside the working copy, such as alice/src.
    private String showInside(Path workingCopy, Path folder) {
        List<String> parts = new ArrayList<>();
        for (Path part : workingCopy.relativize(folder)) {
            parts.add(part.toString());
        }
        return TextUtils.join(FOLDER_SEPARATOR, parts);
    }

    // Names the top of the upload: a ZIP file, or a folder unzipped by
    // hand.
    private String describeUpload(Submission submission) {
        Path upload = submission.getOriginalPath();
        boolean folder = upload != null
                && Files.isDirectory(upload, LinkOption.NOFOLLOW_LINKS);
        if (folder) {
            return FOLDER_TOP;
        }
        return ZIP_TOP;
    }

    // Lists the absent question folders that sit at the end of the extra
    // folder's chain.
    private List<String> listNestedNames(List<Path> folders,
            Path chainEnd, Assessment assessment) throws IOException {
        List<String> names = new ArrayList<>();

        for (String folderName : getQuestionFolderNames(assessment)) {
            boolean absent = findExact(folders, folderName) == null
                    && findIgnoringCase(folders, folderName) == null;
            if (absent && isInside(chainEnd, folderName)) {
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
            joined = joined + LIST_SEPARATOR + names.get(index);
        }
        if (last > 0) {
            joined = joined + " and " + names.get(last);
        }
        return joined;
    }

    // Says whether the end of the extra folder's chain holds the question
    // folder.
    private boolean isInside(Path chainEnd, String folderName)
            throws IOException {
        return chainEnd != null
                && findIgnoringCase(listFolders(chainEnd),
                folderName) != null;
    }

    // Explains where the question folders sit too deep, or returns empty
    // text.
    private String describeNesting(Path contentRoot, Path chainEnd) {
        if (chainEnd == null) {
            return "";
        }

        return " The question folders were found "
                + describeDepth(contentRoot, chainEnd) + " too deep, in "
                + showPath(contentRoot, chainEnd) + ".";
    }

    // Reports every question folder as missing, with the same reason.
    private void addAllFoldersMissing(List<Anomaly> anomalies,
            Submission submission, Assessment assessment, String reason) {
        for (String folderName : getQuestionFolderNames(assessment)) {
            String message = "The " + folderName + " folder is missing. "
                    + reason;
            anomalies.add(createAnomaly(submission,
                    AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                    message));
        }
    }

    // Says that several folders hold question folders, so GradeZZY cannot
    // tell which one is the answer. The top of the upload counts as one.
    private String describeSeveralAnswers(List<Path> holders,
            Submission submission) {
        List<String> names = new ArrayList<>();
        Path workingCopy = submission.getWorkingCopyPath();

        for (Path holder : holders) {
            if (holder.equals(workingCopy)) {
                names.add(TOP_LEVEL_NAME);
            } else {
                names.add(showInside(workingCopy, holder));
            }
        }

        String upload = "The ZIP file";
        if (describeUpload(submission).equals(FOLDER_TOP)) {
            upload = "The uploaded folder";
        }
        return upload + " holds the question folders in " + holders.size()
                + " places (" + joinNames(names) + "), so GradeZZY cannot "
                + "tell which one to grade. Ask the student which one is "
                + "their answer, then grade it again.";
    }

    // Explains why no top folder could be used: none, or too many.
    private String describeTopLevel(Path workingCopy,
            Submission submission) throws IOException {
        List<Path> topFolders = listFolders(workingCopy);

        if (topFolders.isEmpty()) {
            return "The ZIP file holds no folder.";
        }

        List<String> folderNames = new ArrayList<>();

        for (Path topFolder : topFolders) {
            folderNames.add(topFolder.getFileName().toString());
        }
        String names = TextUtils.join(LIST_SEPARATOR, folderNames);

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
