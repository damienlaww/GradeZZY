/*
 * File: SubmissionPreview.java
 *
 * Purpose: Says, before grading, what grading will do with a chosen zip
 * file or folder: how many students it holds and what it skips.
 *
 * Where it fits: LiveBackend.inspect uses it for the student zips. It asks
 * SubmissionLoader, the class grading uses, so the Set up step and grading
 * always agree. It unpacks and changes nothing.
 */
package gradezzy.ui.backend;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.SubmissionLoader;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.util.TextUtils;

/**
 * Describes a chosen student zip, zip of student zips or folder.
 */
public class SubmissionPreview {

    // The extension of a zip file
    private static final String ZIP_EXTENSION = ".zip";

    // The most names listed in one message
    private static final int MAX_NAMES_SHOWN = 5;

    // A zip whose share of zips inside is above this mostly holds zips
    private static final double MOSTLY = 0.5;

    // The application settings
    private final AppConfig config;

    // The loader grading uses
    private final SubmissionLoader loader;

    /**
     * Creates a preview that uses the same settings as grading.
     *
     * @param config the application settings
     */
    public SubmissionPreview(AppConfig config) {
        this.config = config;
        this.loader = new SubmissionLoader(config);
    }

    /**
     * Describes a readable file: a student zip, or a zip that holds only
     * student zips, which grading treats as one student per zip inside.
     *
     * @param file the chosen file
     * @return the checked item
     */
    public ImportItem inspectFile(Path file) {
        String name = String.valueOf(file.getFileName());
        if (!name.toLowerCase().endsWith(ZIP_EXTENSION)) {
            return new ImportItem(InputKind.SUBMISSION, file, "",
                    ImportState.INVALID, "This is not a ZIP file.");
        }
        if (loader.isBatchArchive(file)) {
            return inspectZipOfZips(file);
        }

        List<String> files = new ArrayList<>();
        try {
            files = loader.listBatchEntries(file);
        } catch (IngestException exception) {
            return oneStudent(file, ImportState.WARNING, "GradeZZY could "
                    + "not read inside this ZIP file, so it may be damaged. "
                    + "It is graded as one student, and the problem is "
                    + "reported for that student.");
        }
        if (files.isEmpty()) {
            return oneStudent(file, ImportState.WARNING,
                    "This ZIP file holds no files.");
        } else if (countZips(files) > files.size() * MOSTLY) {
            return oneStudent(file, ImportState.WARNING, "Most files "
                    + "inside are ZIP files, but grading treats this as one "
                    + "student's ZIP file, so the ZIP files inside are not "
                    + "graded as students.");
        }
        return oneStudent(file, ImportState.ACCEPTED, "Ready to grade.");
    }

    /**
     * Describes a readable folder: one submission unzipped by hand, or the
     * student zips and unzipped submissions inside it.
     *
     * @param folder the chosen folder
     * @return the checked item
     */
    public ImportItem inspectFolder(Path folder) {
        List<Path> candidates = new ArrayList<>();
        try {
            candidates = loader.listCandidates(folder);
        } catch (IngestException exception) {
            // The same words grading would stop with
            return new ImportItem(InputKind.SUBMISSION, folder, "",
                    ImportState.INVALID, exception.getMessage());
        }

        if (candidates.isEmpty()) {
            return new ImportItem(InputKind.SUBMISSION, folder, "",
                    ImportState.INVALID, "Grading skips this folder, "
                    + "because its name is on the Ignored file names list "
                    + "in Settings.");
        }
        if (candidates.size() == 1 && candidates.get(0).equals(folder)) {
            return oneStudent(folder, ImportState.ACCEPTED, "A submission "
                    + "unzipped by hand, graded as one student.");
        }
        return inspectFolderOfStudents(folder, candidates);
    }

    // Describes a zip of student zips: one student per zip inside. The
    // zip is not unpacked, so for a student with two zips inside the
    // rule is named instead of the file graded.
    private ImportItem inspectZipOfZips(Path file) {
        List<String> innerZips = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try {
            innerZips = loader.listBatchEntries(file);
            skipped = loader.listBatchSkipped(file);
        } catch (IngestException exception) {
            return new ImportItem(InputKind.SUBMISSION, file, "",
                    ImportState.INVALID, exception.getMessage());
        }
        String holds = "A zip of " + count(innerZips.size(),
                "student ZIP file");
        return severalStudents(file, holds, innerZips,
                skippedNote(skipped), new HashMap<>());
    }

    // Describes a folder of student zips and unzipped submissions.
    private ImportItem inspectFolderOfStudents(Path folder,
            List<Path> candidates) {
        int zips = 0;
        int folders = 0;
        List<String> names = new ArrayList<>();
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                folders++;
            } else {
                zips++;
            }
            names.add(String.valueOf(candidate.getFileName()));
        }

        List<String> skipped = new ArrayList<>();
        List<String> starterFolders = new ArrayList<>();
        try {
            for (Path path : loader.listSkipped(folder)) {
                String name = String.valueOf(path.getFileName());
                if (isStarterName(name)) {
                    starterFolders.add(name);
                } else {
                    skipped.add(name);
                }
            }
        } catch (IngestException exception) {
            skipped.add("items GradeZZY could not read");
        }
        String notes = skippedNote(skipped);
        if (!starterFolders.isEmpty()) {
            notes += " Skipped, because it has the name of the starter "
                    + "folder: " + listNames(starterFolders) + ".";
        }
        return severalStudents(folder, "Holds " + describeParts(zips,
                folders), names, notes, findGradedFiles(folder));
    }

    // Asks the loader which file grading keeps for each student, by
    // lower-case username. A folder is not unpacked, so this only reads
    // names. Empty when the folder cannot be read.
    private Map<String, String> findGradedFiles(Path folder) {
        Map<String, String> graded = new HashMap<>();
        List<Path> inputs = new ArrayList<>();
        inputs.add(folder);
        try {
            for (Submission submission : loader.loadSubmissions(inputs,
                    new Section("", ""))) {
                String username = submission.getUsername();
                if (!TextUtils.isBlank(username)) {
                    graded.put(username.toLowerCase(Locale.ROOT),
                            String.valueOf(submission.getOriginalPath()
                            .getFileName()));
                }
            }
        } catch (IngestException exception) {
            return new HashMap<>();
        }
        return graded;
    }

    // Builds the item of a file or folder that holds one student.
    private ImportItem oneStudent(Path path, ImportState state,
            String message) {
        String name = String.valueOf(path.getFileName());
        String username = loader.extractUsername(name);
        if (TextUtils.isBlank(username)) {
            return new ImportItem(InputKind.SUBMISSION, path, "",
                    ImportState.INVALID, "There is no username in the "
                    + "name, so grading cannot match it to a student.");
        } else if (isStarterName(username)) {
            return new ImportItem(InputKind.SUBMISSION, path, username,
                    ImportState.WARNING, "This has the name of the starter "
                    + "folder, so it may hold the starter files, not a "
                    + "student's work. It is graded as one student named "
                    + username + ".");
        }
        return new ImportItem(InputKind.SUBMISSION, path, username, state,
                message);
    }

    // Builds the item of a file or folder that holds several students.
    // Each username counts once, as grading grades one file per student;
    // graded names that file by username when the loader could say.
    private ImportItem severalStudents(Path path, String holds,
            List<String> names, String notes, Map<String, String> graded) {
        List<String> students = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        List<String> noUsername = new ArrayList<>();
        List<String> repeated = new ArrayList<>();
        List<String> notGraded = new ArrayList<>();
        for (String name : names) {
            String username = loader.extractUsername(name);
            String key = username.toLowerCase(Locale.ROOT);
            String gradedFile = graded.get(key);
            if (TextUtils.isBlank(username)) {
                noUsername.add(name);
            } else if (!seen.contains(key)) {
                seen.add(key);
                students.add(username);
            } else if (!repeated.contains(username)) {
                repeated.add(username);
            }
            if (gradedFile != null && !gradedFile.equals(name)) {
                notGraded.add(name + " (" + gradedFile + " is graded "
                        + "instead)");
            }
        }

        int extraFiles = names.size() - noUsername.size() - students.size();
        String message = holds + ". " + gradedAs(names.size(),
                names.size() - extraFiles) + notes;
        if (!notGraded.isEmpty()) {
            message += " Not graded: " + listNames(notGraded) + ".";
        } else if (!repeated.isEmpty()) {
            message += " More than one ZIP file for " + listNames(repeated)
                    + ": only the one without a copy number such as (1), "
                    + "else the lowest number, else the one named in lower "
                    + "case, is graded.";
        }
        if (noUsername.size() == 1) {
            message += " No username in " + listNames(noUsername)
                    + ", so grading reports it as failed.";
        } else if (noUsername.size() > 1) {
            message += " No username in " + listNames(noUsername)
                    + ", so grading reports the first as failed and names "
                    + "the others on it.";
        }

        ImportState state = ImportState.ACCEPTED;
        if (!notes.isEmpty() || extraFiles > 0 || !noUsername.isEmpty()) {
            state = ImportState.WARNING;
        }
        return new ImportItem(InputKind.SUBMISSION, path, state, message,
                students);
    }

    // Returns the note naming the files a folder or zip skips, or "".
    private String skippedNote(List<String> skipped) {
        if (skipped.isEmpty()) {
            return "";
        }
        return " Skipped, because only ZIP files and submission folders "
                + "are graded: " + listNames(skipped) + ".";
    }

    // Says whether a name is a starter folder name, such as
    // RenameToYourUsername, in any case.
    private boolean isStarterName(String name) {
        for (String starter : config.getList(
                ConfigKeys.PLACEHOLDER_FOLDER_NAMES)) {
            if (starter.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Counts the names that end with .zip.
    private int countZips(List<String> names) {
        int zips = 0;
        for (String name : names) {
            if (name.toLowerCase().endsWith(ZIP_EXTENSION)) {
                zips++;
            }
        }
        return zips;
    }

    // Says how many zips and unzipped folders a folder holds.
    private String describeParts(int zips, int folders) {
        if (folders == 0) {
            return count(zips, "student ZIP file");
        } else if (zips == 0) {
            return count(folders, "folder") + " unzipped by hand";
        }
        return count(zips, "student ZIP file") + " and "
                + count(folders, "folder") + " unzipped by hand";
    }

    // Says how the zips or folders found are graded: one student each,
    // unless some are a second file for a student.
    private String gradedAs(int found, int graded) {
        if (found == 1) {
            return "It is graded as one student.";
        } else if (graded == found) {
            return "Each is graded as one student.";
        }
        return "They are for " + count(graded, "student") + ", and only "
                + "one file per student is graded.";
    }

    // Lists the first few names, then says how many more there are.
    private String listNames(List<String> names) {
        String text = "";
        int shown = Math.min(names.size(), MAX_NAMES_SHOWN);
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                text += ", ";
            }
            text += names.get(index);
        }
        if (names.size() > shown) {
            text += " and " + (names.size() - shown) + " more";
        }
        return text;
    }

    // Writes a count with its noun, such as "1 folder" or "6 folders".
    private String count(int number, String noun) {
        if (number == 1) {
            return number + " " + noun;
        }
        return number + " " + noun + "s";
    }
}
