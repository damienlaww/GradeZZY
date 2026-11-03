/*
 * File: FixStage.java
 *
 * Purpose: Applies automatic corrections to the working copy.
 *
 * Where it fits: Third stage. Always reports Java files that javac cannot
 * read as UTF-8; the fixes are skipped when AUTO_FIX_ENABLED is false.
 */
package gradezzy.grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.config.ConfigKeys;
import gradezzy.fix.EncodingFixer;
import gradezzy.fix.FixManager;
import gradezzy.fix.FixRecord;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.util.TextUtils;
import gradezzy.validation.ValidationRunner;

/**
 * Applies automatic corrections to the working copy.
 */
public class FixStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Correcting common errors";

    // The text in front of the id of every problem this stage reports
    private static final String ID_PREFIX = "encoding-";

    // What the instructor can do about a file in the wrong encoding
    private static final String ENCODING_ACTION =
            "Approve the file saved again as UTF-8, or undo it.";

    // Separates the folders of a path shown to the instructor
    private static final String FOLDER_SEPARATOR = "/";

    // The helper that applies fixes
    private final FixManager fixManager;

    // The checks of the required files, run again after a move or rename
    private final ValidationRunner fileChecks;

    /**
     * Creates a new fix stage.
     *
     * @param fixManager the helper that applies fixes
     * @param fileChecks the checks of the required files, run again after
     *        a fix moved or renamed something
     */
    public FixStage(FixManager fixManager, ValidationRunner fileChecks) {
        this.fixManager = fixManager;
        this.fileChecks = fileChecks;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Reports Java files in the wrong encoding, then applies automatic
     * corrections unless they are turned off. A move or rename can bring
     * files into view that the checks could not see, so the required
     * files are then checked again and new problems fixed.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        addEncodingProblems(submission);
        if (!context.getConfig().getBoolean(ConfigKeys.AUTO_FIX_ENABLED)) {
            return;
        }

        List<FixRecord> applied = fixManager.applyAll(submission);
        while (movedOrRenamed(applied)) {
            List<Anomaly> newAnomalies = checkFilesAgain(submission,
                    context);
            applied = fixManager.applyTo(submission, newAnomalies);
        }
    }

    // Says whether a fix moved or renamed something: all but the header
    // and encoding fixes, which only change what is inside a file.
    private boolean movedOrRenamed(List<FixRecord> applied) {
        for (FixRecord fixRecord : applied) {
            AnomalyType type = fixRecord.getAnomaly().getType();
            if (type != AnomalyType.MISSING_HEADER_NAME
                    && type != AnomalyType.WRONG_ENCODING) {
                return true;
            }
        }
        return false;
    }

    // Adds a problem for each Java file that javac cannot read as UTF-8.
    private void addEncodingProblems(Submission submission) {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            return;
        }

        for (Path file : listJavaFiles(workingCopy)) {
            byte[] bytes = readOrEmpty(file);
            if (EncodingFixer.needsConverting(bytes)) {
                Anomaly found = createEncodingProblem(submission, file,
                        bytes);
                if (!isKnown(submission, found)) {
                    submission.addAnomaly(found);
                }
            }
        }
    }

    // Builds the problem for one file. Its message starts with the file's
    // path in the working copy, which EncodingFixer reads back.
    private Anomaly createEncodingProblem(Submission submission, Path file,
            byte[] bytes) {
        String shownPath = showPath(submission.getWorkingCopyPath(), file);
        String problem = EncodingFixer.describeProblem(shownPath, bytes);

        return new Anomaly(ID_PREFIX + UUID.randomUUID(),
                AnomalyType.WRONG_ENCODING, Severity.WARNING,
                submission.getUsername(), problem, ENCODING_ACTION);
    }

    // Returns the path of a file inside the working copy, such as
    // ping.lee.2023/Q1/Q1a.java.
    private String showPath(Path workingCopy, Path file) {
        List<String> names = new ArrayList<>();
        for (Path part : workingCopy.relativize(file)) {
            names.add(part.toString());
        }
        return TextUtils.join(FOLDER_SEPARATOR, names);
    }

    // Lists the working copy's Java files, or none if it cannot be read.
    // An unreadable working copy is reported by the checks.
    private List<Path> listJavaFiles(Path workingCopy) {
        try {
            return EncodingFixer.findJavaFiles(workingCopy);
        } catch (IOException exception) {
            return new ArrayList<>();
        }
    }

    // Reads a file, or returns no bytes if it cannot be read.
    private byte[] readOrEmpty(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    // Checks the required files again and adds the problems not yet known.
    private List<Anomaly> checkFilesAgain(Submission submission,
            GradingContext context) {
        List<Anomaly> newAnomalies = new ArrayList<>();
        for (Anomaly found : fileChecks.runAll(submission,
                context.getAssessment())) {
            if (!isKnown(submission, found)) {
                submission.addAnomaly(found);
                newAnomalies.add(found);
            }
        }
        return newAnomalies;
    }

    // Says whether the submission already has this problem, paths aside.
    private boolean isKnown(Submission submission, Anomaly found) {
        String foundText = cutAtPath(submission, found);
        for (Anomaly known : submission.getAnomalies()) {
            if (known.getType() == found.getType()
                    && cutAtPath(submission, known).equals(foundText)) {
                return true;
            }
        }
        return false;
    }

    // Returns the message up to its first path in the working copy.
    private String cutAtPath(Submission submission, Anomaly anomaly) {
        String message = anomaly.getMessage();
        int pathStart = message.indexOf(
                submission.getWorkingCopyPath().toString());
        if (pathStart < 0) {
            return message;
        }
        return message.substring(0, pathStart);
    }
}
