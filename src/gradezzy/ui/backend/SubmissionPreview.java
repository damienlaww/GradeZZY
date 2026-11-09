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
 *
 * <p>Tier: MUST.
 */
public class SubmissionPreview {

    // The extension of a zip file
    private static final String ZIP_EXTENSION = ".zip";

    // Starts each point of a note that lists several
    private static final String BULLET = "• ";

    // Comes between the points of a note
    private static final String NEW_LINE = "\n";

    // Comes between the names in a list of names
    private static final String LIST_SEPARATOR = ", ";

    // The note beside a listed file that grading leaves out
    private static final String SKIPPED_NOTE = "Skipped";

    // The note beside a second file for a student, not graded
    private static final String NOT_GRADED_NOTE = "Not graded";

    // The note beside a file whose name has no username
    private static final String NO_USERNAME_NOTE = "No username";

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
        int zipCount = countZips(files);
        double mostlyZips = files.size() * MOSTLY;
        if (files.isEmpty()) {
            return oneStudent(file, ImportState.WARNING,
                    "This ZIP file holds no files.");
        } else if (files.size() == 1 && zipCount == 1) {
            return oneStudent(file, ImportState.ACCEPTED, "This ZIP file "
                    + "holds only another ZIP file, which grading unpacks "
                    + "and grades as this student.");
        } else if (zipCount > mostlyZips) {
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
        String holds = "Zip of " + count(innerZips.size(),
                "student ZIP file");
        List<String> notes = new ArrayList<>();
        addSkippedNote(notes, skipped);

        return severalStudents(file, holds, innerZips, notes,
                new HashMap<>(), skipped);
    }

    // Describes a folder of student zips and unzipped submissions. A zip
    // of student zips in the folder gives the students inside it, as in
    // grading.
    private ImportItem inspectFolderOfStudents(Path folder,
            List<Path> candidates) {
        List<String> names = new ArrayList<>();
        List<String> bulkZips = new ArrayList<>();
        int folders = addCandidateNames(candidates, names, bulkZips);
        int zips = names.size() - folders;

        List<String> skipped = new ArrayList<>();
        List<String> starterFolders = new ArrayList<>();
        listSkipped(folder, skipped, starterFolders);
        List<String> notes = new ArrayList<>();
        addSkippedNote(notes, skipped);

        if (!starterFolders.isEmpty()) {
            notes.add("Skipped (starter folder name): "
                    + listNames(starterFolders));
        }

        // Asking the loader which file is graded would unpack a bulk zip,
        // and the preview must change nothing
        Map<String, String> graded = new HashMap<>();
        String holds = describeParts(zips, folders);
        if (bulkZips.isEmpty()) {
            graded = findGradedFiles(folder);
        } else {
            holds += ", incl. those inside " + listNames(bulkZips);
        }

        List<String> notUsed = new ArrayList<>(skipped);
        notUsed.addAll(starterFolders);
        return severalStudents(folder, holds, names, notes, graded,
                notUsed);
    }

    // Adds the name of every student zip and unzipped folder among the
    // candidates, opening each zip of student zips for the zips inside,
    // and names those bulk zips. Returns how many unzipped folders there
    // are; every other name is a zip.
    private int addCandidateNames(List<Path> candidates, List<String> names,
            List<String> bulkZips) {
        int folders = 0;
        for (Path candidate : candidates) {
            List<String> innerZips = listInnerStudentZips(candidate);
            String candidateName = String.valueOf(candidate.getFileName());
            if (innerZips != null) {
                bulkZips.add(candidateName);
                names.addAll(innerZips);
            } else {
                names.add(candidateName);
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    folders++;
                }
            }
        }
        return folders;
    }

    // Sorts what grading skips in a folder into starter folders and other
    // items; one item stands for everything when the folder cannot be read.
    private void listSkipped(Path folder, List<String> skipped,
            List<String> starterFolders) {
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
    }

    // Returns the student zips inside a zip of student zips, or null when
    // the path is not one, or cannot be read.
    private List<String> listInnerStudentZips(Path candidate) {
        if (!loader.isBatchArchive(candidate)) {
            return null;
        }
        try {
            return loader.listBatchEntries(candidate);
        } catch (IngestException exception) {
            return null;
        }
    }

    // Asks the loader which file grading keeps for each student, by
    // lower-case username. A folder is not unpacked, so this only reads
    // names. Empty when the folder cannot be read.
    private Map<String, String> findGradedFiles(Path folder) {
        Map<String, String> graded = new HashMap<>();
        List<Path> inputs = new ArrayList<>();
        inputs.add(folder);
        try {
            Section noSection = new Section("", "");
            List<Submission> submissions = loader.loadSubmissions(inputs,
                    noSection);
            for (Submission submission : submissions) {
                String username = submission.getUsername();
                if (!TextUtils.isBlank(username)) {
                    String key = username.toLowerCase(Locale.ROOT);
                    Path original = submission.getOriginalPath();
                    graded.put(key, String.valueOf(original.getFileName()));
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
    // graded names that file by username when the loader could say. The
    // message lists each point on its own line. skipped names the files
    // grading leaves out, which are listed with the others.
    private ImportItem severalStudents(Path path, String holds,
            List<String> names, List<String> notes,
            Map<String, String> graded, List<String> skipped) {
        List<ContainedFile> files = new ArrayList<>();
        List<String> students = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        List<String> noUsername = new ArrayList<>();
        List<String> repeated = new ArrayList<>();
        List<String> notGraded = new ArrayList<>();
        for (String name : names) {
            String username = loader.extractUsername(name);
            String key = username.toLowerCase(Locale.ROOT);
            String gradedFile = graded.get(key);
            String fileNote = "";
            if (TextUtils.isBlank(username)) {
                noUsername.add(name);
                fileNote = NO_USERNAME_NOTE;
            } else if (!seen.contains(key)) {
                seen.add(key);
                students.add(username);
            } else if (!repeated.contains(username)) {
                repeated.add(username);
            }
            if (gradedFile != null && !gradedFile.equals(name)) {
                String instead = name + " (" + gradedFile + " is graded "
                        + "instead)";
                notGraded.add(instead);
                fileNote = NOT_GRADED_NOTE;
            }
            files.add(new ContainedFile(name, fileNote));
        }

        for (String name : skipped) {
            files.add(new ContainedFile(name, SKIPPED_NOTE));
        }
        files.sort(new ContainedFileNameComparator());

        int extraFiles = names.size() - noUsername.size() - students.size();
        int gradedCount = names.size() - extraFiles;
        List<String> points = new ArrayList<>();
        points.add(holds);
        points.add(gradedAs(names.size(), gradedCount));
        points.addAll(notes);
        addRepeatPoint(points, notGraded, repeated);
        addNoUsernamePoint(points, noUsername);

        ImportState state = ImportState.ACCEPTED;
        boolean needsWarning = !notes.isEmpty() || extraFiles > 0
                || !noUsername.isEmpty();
        if (needsWarning) {
            state = ImportState.WARNING;
        }
        return new ImportItem(InputKind.SUBMISSION, path, state,
                toPoints(points), students, files);
    }

    // Adds the point about files not graded because the student has
    // another file, or else about students with several ZIPs, if any.
    private void addRepeatPoint(List<String> points, List<String> notGraded,
            List<String> repeated) {
        if (!notGraded.isEmpty()) {
            points.add("Not graded: " + listNames(notGraded));
        } else if (!repeated.isEmpty()) {
            points.add("Several ZIPs for " + listNames(repeated)
                    + ": only the newest copy is graded");
        }
    }

    // Adds the point about files with no username, if any.
    private void addNoUsernamePoint(List<String> points,
            List<String> noUsername) {
        if (noUsername.size() == 1) {
            points.add("No username, reported as failed: "
                    + listNames(noUsername));
        } else if (noUsername.size() > 1) {
            points.add("No username, the first reported as failed and "
                    + "the rest noted on it: " + listNames(noUsername));
        }
    }

    // Joins points into a list with one bullet per line.
    private String toPoints(List<String> points) {
        List<String> lines = new ArrayList<>();
        for (String point : points) {
            lines.add(BULLET + point);
        }

        return TextUtils.join(NEW_LINE, lines);
    }

    // Adds the note naming the files a folder or zip skips, if any.
    private void addSkippedNote(List<String> notes, List<String> skipped) {
        if (!skipped.isEmpty()) {
            notes.add("Skipped (not a ZIP or folder): "
                    + listNames(skipped));
        }
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
            return count(zips, "ZIP file");
        } else if (zips == 0) {
            return count(folders, "unzipped folder");
        }
        return count(zips, "ZIP file") + " and "
                + count(folders, "unzipped folder");
    }

    // Says how the zips or folders found are graded: one student each,
    // unless some are a second file for a student.
    private String gradedAs(int found, int graded) {
        if (found == 1) {
            return "Graded as one student";
        } else if (graded == found) {
            return "One student each";
        }
        return count(graded, "student") + ", one file graded each";
    }

    // Lists the first few names, then says how many more there are.
    private String listNames(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }

        int shown = Math.min(names.size(), MAX_NAMES_SHOWN);
        List<String> shownNames = new ArrayList<>();
        for (int index = 0; index < shown; index++) {
            shownNames.add(names.get(index));
        }

        String text = TextUtils.join(LIST_SEPARATOR, shownNames);
        if (names.size() > shown) {
            int more = names.size() - shown;
            text += " and " + more + " more";
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
