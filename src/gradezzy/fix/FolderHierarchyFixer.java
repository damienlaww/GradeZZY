/*
 * File: FolderHierarchyFixer.java
 *
 * Purpose: Corrects a wrong folder hierarchy: moves Q folders that sit at the
 * top of the zip into a new folder named after the username, moves Q
 * folders that sit one level too deep back up, and makes the missing Q
 * folders for answer files left loose in the username folder.
 *
 * Where it fits: Handles the improper hierarchy cases from the brief.
 */
package gradezzy.fix;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;

/**
 * Corrects a wrong folder hierarchy: adds a missing username folder,
 * moves question folders up out of an extra folder, or makes the missing
 * question folders for answer files left loose.
 */
public class FolderHierarchyFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fix folder hierarchy";

    // Follows the folder names in Before for a missing username folder
    private static final String AT_THE_TOP = " at the top of the ZIP";

    // Follows them instead when the upload was a folder unzipped by hand
    private static final String AT_THE_TOP_OF_FOLDER =
            " at the top of the uploaded folder";

    // The ending of a compiled file. The provided ones are copied into
    // the new folders from the starter, so the student's are not moved
    private static final String CLASS_EXTENSION = ".class";

    // The application settings, which name the question folders
    private final AppConfig config;

    /**
     * Creates a new folder hierarchy fixer.
     *
     * @param config the application settings
     */
    public FolderHierarchyFixer(AppConfig config) {
        super(NAME);
        this.config = config;
    }

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.MISSING_WRAPPER_FOLDER
                || anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER;
    }

    /**
     * Finds what the fix would change: the extra folder that holds the
     * anomaly's question folder, the folder that holds the question's
     * answer file loose, or the whole working copy when the username
     * folder is missing.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the extra folder, the folder with the loose files or the
     *         working copy, or null when the question folder is neither
     *         one level too deep nor missing with its answer file loose
     * @throws FixException if a username folder exists already or the
     *         working copy cannot be read
     */
    @Override
    public Path findPathToChange(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        if (anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER) {
            Path extraFolder = findExtraFolderOf(submission, anomaly);
            if (extraFolder != null) {
                return extraFolder;
            }
            return findLooseFilesFolderOf(submission, anomaly);
        }

        String username = submission.getUsername();
        if (Files.exists(workingCopy.resolve(username),
                LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("A " + username + " folder already "
                    + "exists, so the folders were left where they are.");
        }
        return workingCopy;
    }

    /**
     * Adds the missing username folder, moves every question folder up
     * out of an extra folder in one fix, giving each the right letter
     * case, or makes every missing question folder whose answer file is
     * loose and moves that question's files into it, in one fix. The
     * other problems of that fix are then removed, so the instructor sees
     * one fix.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone, or null when the
     *         question folder is neither one level too deep nor missing
     *         with its answer file loose
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path pathToChange = findPathToChange(submission, anomaly);
        if (pathToChange == null) {
            return null;
        }

        if (anomaly.getType() == AnomalyType.MISSING_WRAPPER_FOLDER) {
            return addUsernameFolder(submission, anomaly);
        } else if (findExtraFolderOf(submission, anomaly) != null) {
            return moveUp(submission, anomaly, pathToChange);
        }
        return makeFolders(submission, anomaly, pathToChange);
    }

    /**
     * Reverses the fix: removes the added username folder, moves the
     * question folders back down into the extra folder, or moves the
     * loose files back out and removes the folders the fix made.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        if (fixRecord.getAnomaly().getType()
                != AnomalyType.MISSING_QUESTION_FOLDER) {
            removeUsernameFolder(fixRecord);
        } else if (madeFolders(fixRecord)) {
            moveFilesBack((FolderMoveRecord) fixRecord);
        } else {
            moveBackDown(fixRecord);
        }
    }

    // Finds the extra folder holding the anomaly's question folder.
    private Path findExtraFolderOf(Submission submission,
            Anomaly anomaly) throws FixException {
        List<String> folderNames = readFolderNames(config);
        Path contentRoot = findContentRoot(submission.getWorkingCopyPath(),
                submission.getUsername(), folderNames);
        if (contentRoot == null) {
            return null;
        }

        String folderName = findMissingFolderName(submission, anomaly,
                contentRoot, folderNames);
        Path extraFolder = findExtraFolder(contentRoot, folderNames);
        if (folderName == null || extraFolder == null
                || !canMoveUp(folderName, contentRoot, extraFolder)) {
            return null;
        }
        return extraFolder;
    }

    // Finds the folder that holds the answer file of the anomaly's
    // question loose, when that question's folder is missing.
    private Path findLooseFilesFolderOf(Submission submission,
            Anomaly anomaly) throws FixException {
        List<String> folderNames = readFolderNames(config);
        Path contentRoot = findContentRoot(submission.getWorkingCopyPath(),
                submission.getUsername(), folderNames);
        if (contentRoot == null) {
            return null;
        }

        String folderName = findMissingFolderName(submission, anomaly,
                contentRoot, folderNames);
        if (!canMakeFolder(folderName, contentRoot)) {
            return null;
        }
        return contentRoot;
    }

    // Says whether a question folder is missing, in any case, while an
    // answer file of its questions lies loose in the content root.
    private boolean canMakeFolder(String folderName, Path contentRoot)
            throws FixException {
        if (folderName == null) {
            return false;
        }

        Path existing = findIgnoringCase(listFolders(contentRoot),
                folderName);
        if (existing != null) {
            return false;
        }

        List<Path> answerFiles = findAnswerFiles(folderName, contentRoot);
        return !answerFiles.isEmpty();
    }

    // Finds the loose answer files of the questions in a folder, such as
    // Q2a.java and Q2b.java for Q2.
    private List<Path> findAnswerFiles(String folderName,
            Path contentRoot) throws FixException {
        List<Path> looseFiles = listFiles(contentRoot);
        List<Path> answerFiles = new ArrayList<>();
        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            String fileName = readFileName(config, questionId);
            Path answerFile = findByName(looseFiles, fileName);
            boolean inFolder = readFolderName(config, questionId).equals(
                    folderName);
            boolean isLooseAnswer = inFolder && answerFile != null;
            if (isLooseAnswer) {
                answerFiles.add(answerFile);
            }
        }
        return answerFiles;
    }

    // Finds the loose files that go into a new question folder: its
    // answer files, then the files named like the starter's other files
    // in that folder, such as GoldMember.java for Q3 or a data file such
    // as orders.txt. A file already taken by an earlier folder is left
    // out.
    private List<Path> findFilesFor(String folderName, Path contentRoot,
            List<Path> taken) throws FixException {
        List<Path> files = findAnswerFiles(folderName, contentRoot);
        List<Path> looseFiles = listFiles(contentRoot);
        for (String name : listStarterFiles(folderName)) {
            Path looseFile = findByName(looseFiles, name);
            boolean isNewFile = looseFile != null
                    && !files.contains(looseFile);
            if (isNewFile) {
                files.add(looseFile);
            }
        }

        files.removeAll(taken);
        return files;
    }

    // Lists the files the starter folder has in a question folder, other
    // than compiled ones: the answer files, the question's other source
    // files and its data files. There are none when no starter folder is
    // set or it lacks that folder.
    private List<String> listStarterFiles(String folderName)
            throws FixException {
        List<String> names = new ArrayList<>();
        String starterSetting = config.getString(ConfigKeys.STARTER_DIR);
        if (starterSetting.isEmpty()) {
            return names;
        }

        Path starterFolder = Paths.get(starterSetting).resolve(folderName);
        if (!Files.isDirectory(starterFolder)) {
            return names;
        }
        for (Path file : listFiles(starterFolder)) {
            String name = file.getFileName().toString();
            boolean isCompiled = name.endsWith(CLASS_EXTENSION);
            if (Files.isRegularFile(file) && !isCompiled) {
                names.add(name);
            }
        }
        return names;
    }

    // Says whether a question folder is in the extra folder only.
    private boolean canMoveUp(String folderName, Path contentRoot,
            Path extraFolder) throws FixException {
        return findIgnoringCase(listFolders(contentRoot), folderName) == null
                && findByName(listFolders(extraFolder), folderName) != null;
    }

    // Moves the visible top-level entries into a new username folder.
    private FixRecord addUsernameFolder(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        String username = submission.getUsername();
        Path usernameFolder = workingCopy.resolve(username);
        List<Path> entries = listEntries(workingCopy, false);
        List<String> folderNames = new ArrayList<>();
        List<String> shownBefore = new ArrayList<>();
        for (Path folder : listFolders(workingCopy)) {
            folderNames.add(folder.getFileName().toString());
            shownBefore.add(folder.getFileName() + FOLDER_SEPARATOR);
        }

        try {
            Files.createDirectory(usernameFolder);
        } catch (IOException exception) {
            throw new FixException("Could not create the " + username
                    + " folder: " + exception.getMessage(), exception);
        }

        try {
            moveEntries(entries, workingCopy, usernameFolder);
        } catch (FixException exception) {
            removeEmptyFolder(usernameFolder, exception);
            throw exception;
        }

        return new FixRecord(anomaly, NAME, "Moved the question folders "
                + "into a new " + username + " folder.", workingCopy,
                workingCopy, usernameFolder, null,
                joinNames(shownBefore) + describeTop(submission),
                username + FOLDER_SEPARATOR + joinNames(folderNames));
    }

    // Says where the question folders were: at the top of the ZIP file,
    // or of the folder the instructor chose.
    private String describeTop(Submission submission) {
        Path upload = submission.getOriginalPath();
        boolean folder = upload != null
                && Files.isDirectory(upload, LinkOption.NOFOLLOW_LINKS);
        if (folder) {
            return AT_THE_TOP_OF_FOLDER;
        }
        return AT_THE_TOP;
    }

    // Moves everything back to the top and removes the username folder.
    private void removeUsernameFolder(FixRecord fixRecord)
            throws FixException {
        Path usernameFolder = fixRecord.getFixedPath();
        Path workingCopy = fixRecord.getOriginalPath();
        if (!Files.isDirectory(usernameFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("The " + usernameFolder.getFileName()
                    + " folder is no longer there, so nothing was moved.");
        }

        moveEntries(listEntries(usernameFolder, true), usernameFolder,
                workingCopy);
        try {
            Files.delete(usernameFolder);
        } catch (IOException exception) {
            throw new FixException("Moved the folders back but could not "
                    + "remove " + usernameFolder.getFileName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    // Moves every question folder up out of the extra folder at once.
    private FixRecord moveUp(Submission submission, Anomaly anomaly,
            Path extraFolder) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        Path contentRoot = extraFolder.getParent();
        List<String> folderNames = readFolderNames(config);
        List<Anomaly> sameMove = findSameMove(submission, anomaly,
                contentRoot, extraFolder, folderNames);

        List<Path> pathsBefore = new ArrayList<>();
        List<Path> pathsAfter = new ArrayList<>();
        List<String> namesBefore = new ArrayList<>();
        for (String folderName : folderNames) {
            if (canMoveUp(folderName, contentRoot, extraFolder)) {
                Path questionFolder = findByName(listFolders(extraFolder),
                        folderName);
                pathsBefore.add(questionFolder);
                pathsAfter.add(contentRoot.resolve(folderName));
                namesBefore.add(questionFolder.getFileName().toString());
            }
        }
        List<String> namesAfter = new ArrayList<>();
        for (Path movedFolder : pathsAfter) {
            namesAfter.add(movedFolder.getFileName().toString());
        }

        moveFolders(pathsBefore, pathsAfter);
        boolean removed = removeIfEmpty(extraFolder);
        submission.getAnomalies().removeAll(sameMove);

        String description = describeMove(namesBefore, namesAfter,
                extraFolder, removed);
        String valueBefore = showFolder(workingCopy, extraFolder)
                + joinNames(namesBefore);
        String valueAfter = showFolder(workingCopy, contentRoot)
                + joinNames(namesAfter);

        return new FolderMoveRecord(anomaly, NAME, description, workingCopy,
                extraFolder, contentRoot, valueBefore, valueAfter,
                pathsBefore, pathsAfter);
    }

    // Finds the other missing folder problems this move also fixes.
    private List<Anomaly> findSameMove(Submission submission,
            Anomaly anomaly, Path contentRoot, Path extraFolder,
            List<String> folderNames) throws FixException {
        List<Anomaly> sameMove = new ArrayList<>();
        for (Anomaly other : submission.getAnomalies()) {
            boolean movedToo = isOtherMissingFolder(other, anomaly)
                    && isMovedToo(submission, other, contentRoot,
                    extraFolder, folderNames);
            if (movedToo) {
                sameMove.add(other);
            }
        }
        return sameMove;
    }

    // Says whether a problem is another missing folder not yet fixed.
    private boolean isOtherMissingFolder(Anomaly other, Anomaly anomaly) {
        return other.getType() == AnomalyType.MISSING_QUESTION_FOLDER
                && other.getFixStatus() != FixStatus.APPLIED
                && !other.getId().equals(anomaly.getId());
    }

    // Says whether the folder another problem is about moves up too.
    private boolean isMovedToo(Submission submission, Anomaly other,
            Path contentRoot, Path extraFolder, List<String> folderNames)
            throws FixException {
        String folderName = findMissingFolderName(submission, other,
                contentRoot, folderNames);
        return folderName != null
                && canMoveUp(folderName, contentRoot, extraFolder);
    }

    // Says in words what moveUp did.
    private String describeMove(List<String> namesBefore,
            List<String> namesAfter, Path extraFolder, boolean removed) {
        String description = "Moved " + joinNames(namesBefore)
                + " up out of the extra " + extraFolder.getFileName()
                + " folder";
        if (!namesBefore.equals(namesAfter)) {
            description += ", named " + joinNames(namesAfter);
        }
        description += ".";

        if (removed) {
            description += " That folder was then empty and was removed.";
        }
        return description;
    }

    // Moves the question folders back into the extra folder.
    private void moveBackDown(FixRecord fixRecord) throws FixException {
        if (!(fixRecord instanceof FolderMoveRecord)) {
            throw new FixException("This fix did not keep the folders it "
                    + "moved, so nothing was moved.");
        }

        FolderMoveRecord moveRecord = (FolderMoveRecord) fixRecord;
        Path contentRoot = findContentRoot(fixRecord.getWorkingCopy(),
                fixRecord.getAnomaly().getUsername(),
                readFolderNames(config));
        if (contentRoot == null) {
            throw new FixException("The moved folders are no longer "
                    + "there, so nothing was moved.");
        }

        List<Path> movedFolders = new ArrayList<>();
        for (Path pathAfter : moveRecord.getPathsAfter()) {
            Path movedFolder = findExact(listFolders(contentRoot),
                    pathAfter.getFileName().toString());
            if (movedFolder == null) {
                throw new FixException("The " + pathAfter.getFileName()
                        + " folder is no longer there, so nothing was "
                        + "moved.");
            }
            movedFolders.add(movedFolder);
        }

        Path extraName = fixRecord.getOriginalPath().getFileName();
        Path extraFolder = contentRoot.resolve(extraName);
        try {
            Files.createDirectories(extraFolder);
        } catch (IOException exception) {
            throw new FixException("Could not make the " + extraName
                    + " folder again: " + exception.getMessage(), exception);
        }

        List<Path> targets = new ArrayList<>();
        for (Path pathBefore : moveRecord.getPathsBefore()) {
            targets.add(extraFolder.resolve(pathBefore.getFileName()));
        }
        moveFolders(movedFolders, targets);
    }

    // Makes every missing question folder whose answer file is loose in
    // the content root and moves that question's loose files into it,
    // all in one fix. Files that belong to no question stay where they
    // are.
    private FixRecord makeFolders(Submission submission, Anomaly anomaly,
            Path contentRoot) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        List<String> folderNames = readFolderNames(config);
        List<Anomaly> sameFix = findSameFolders(submission, anomaly,
                contentRoot, folderNames);

        List<Path> newFolders = new ArrayList<>();
        List<String> newNames = new ArrayList<>();
        List<Path> pathsBefore = new ArrayList<>();
        List<Path> pathsAfter = new ArrayList<>();
        for (String folderName : folderNames) {
            if (canMakeFolder(folderName, contentRoot)) {
                Path newFolder = contentRoot.resolve(folderName);
                newFolders.add(newFolder);
                newNames.add(folderName);
                List<Path> files = findFilesFor(folderName, contentRoot,
                        pathsBefore);
                for (Path file : files) {
                    pathsBefore.add(file);
                    pathsAfter.add(newFolder.resolve(file.getFileName()));
                }
            }
        }

        createFolders(newFolders);
        try {
            moveFolders(pathsBefore, pathsAfter);
        } catch (FixException exception) {
            removeFolders(newFolders, exception);
            throw exception;
        }
        submission.getAnomalies().removeAll(sameFix);

        List<String> namesBefore = new ArrayList<>();
        List<String> namesAfter = new ArrayList<>();
        for (int i = 0; i < pathsBefore.size(); i++) {
            namesBefore.add(pathsBefore.get(i).getFileName().toString());
            namesAfter.add(showMoved(pathsAfter.get(i)));
        }
        String shownRoot = showFolder(workingCopy, contentRoot);
        String description = describeNewFolders(newNames, namesBefore,
                contentRoot);
        String valueBefore = shownRoot + joinNames(namesBefore);
        String valueAfter = shownRoot + joinNames(namesAfter);

        return new FolderMoveRecord(anomaly, NAME, description, workingCopy,
                contentRoot, valueBefore, valueAfter, pathsBefore, pathsAfter,
                newFolders);
    }

    // Finds the other missing folder problems that makeFolders also
    // fixes.
    private List<Anomaly> findSameFolders(Submission submission,
            Anomaly anomaly, Path contentRoot, List<String> folderNames)
            throws FixException {
        List<Anomaly> sameFix = new ArrayList<>();
        for (Anomaly other : submission.getAnomalies()) {
            if (!isOtherMissingFolder(other, anomaly)) {
                continue;
            }

            String otherFolder = findMissingFolderName(submission, other,
                    contentRoot, folderNames);
            if (canMakeFolder(otherFolder, contentRoot)) {
                sameFix.add(other);
            }
        }
        return sameFix;
    }

    // Says in words what makeFolders did.
    private String describeNewFolders(List<String> folderNames,
            List<String> fileNames, Path contentRoot) {
        String made = "Made the " + joinNames(folderNames) + " folder";
        String into = "it";
        if (folderNames.size() > 1) {
            made += "s";
            into = "their question folders";
        }
        return made + " in " + contentRoot.getFileName() + " and moved "
                + joinNames(fileNames) + " into " + into + ".";
    }

    // Makes the new question folders, removing those made if one fails.
    private void createFolders(List<Path> folders) throws FixException {
        for (int i = 0; i < folders.size(); i++) {
            Path folder = folders.get(i);
            try {
                Files.createDirectory(folder);
            } catch (IOException exception) {
                FixException failure = new FixException("Could not make "
                        + "the " + folder.getFileName() + " folder: "
                        + exception.getMessage(), exception);
                removeFolders(folders.subList(0, i), failure);
                throw failure;
            }
        }
    }

    // Removes the new question folders after a failed fix; each is empty.
    private void removeFolders(List<Path> folders, FixException failure) {
        for (Path folder : folders) {
            removeEmptyFolder(folder, failure);
        }
    }

    // Says whether a fix made new question folders for loose files.
    private boolean madeFolders(FixRecord fixRecord) {
        if (!(fixRecord instanceof FolderMoveRecord)) {
            return false;
        }

        FolderMoveRecord moveRecord = (FolderMoveRecord) fixRecord;
        return !moveRecord.getNewFolders().isEmpty();
    }

    // Moves the files back out of the folders made for them, wherever
    // the content root is now, then removes those folders together with
    // what grading put in them since, such as the testers.
    private void moveFilesBack(FolderMoveRecord moveRecord)
            throws FixException {
        String username = moveRecord.getAnomaly().getUsername();
        Path contentRoot = findContentRoot(moveRecord.getWorkingCopy(),
                username, readFolderNames(config));
        if (contentRoot == null) {
            throw new FixException("The new question folders are no "
                    + "longer there, so nothing was moved.");
        }

        List<Path> pathsBefore = moveRecord.getPathsBefore();
        List<Path> pathsAfter = moveRecord.getPathsAfter();
        List<Path> movedFiles = new ArrayList<>();
        List<Path> targets = new ArrayList<>();
        for (int i = 0; i < pathsAfter.size(); i++) {
            Path pathAfter = pathsAfter.get(i);
            Path movedFile = findMovedFile(contentRoot, pathAfter);
            if (movedFile == null) {
                throw new FixException(showMoved(pathAfter)
                        + " is no longer there, so nothing was moved.");
            }
            movedFiles.add(movedFile);
            Path pathBefore = pathsBefore.get(i);
            targets.add(contentRoot.resolve(pathBefore.getFileName()));
        }
        moveFolders(movedFiles, targets);

        for (Path newFolder : moveRecord.getNewFolders()) {
            Path folderNow = contentRoot.resolve(newFolder.getFileName());
            deleteNewFolder(folderNow);
        }
    }

    // Finds a moved file in its new question folder as the working copy
    // is now, or returns null.
    private Path findMovedFile(Path contentRoot, Path pathAfter)
            throws FixException {
        String folderName = pathAfter.getParent().getFileName().toString();
        Path folder = findExact(listFolders(contentRoot), folderName);
        if (folder == null) {
            return null;
        }

        String fileName = pathAfter.getFileName().toString();
        return findExact(listFiles(folder), fileName);
    }

    // Writes a moved file as its folder and name, such as Q3/Q3.java.
    private String showMoved(Path pathAfter) {
        return pathAfter.getParent().getFileName() + FOLDER_SEPARATOR
                + pathAfter.getFileName();
    }

    // Removes a folder the fix made, with what grading put in it since.
    private void deleteNewFolder(Path folder) throws FixException {
        try {
            FileUtils.deleteDirectory(folder);
        } catch (IOException exception) {
            throw new FixException("Moved the files back but could not "
                    + "remove " + folder.getFileName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    // Returns the one folder that is not a question folder but holds one.
    private Path findExtraFolder(Path contentRoot,
            List<String> folderNames) throws FixException {
        List<Path> extraFolders = new ArrayList<>();
        for (Path folder : listFolders(contentRoot)) {
            String name = folder.getFileName().toString();
            if (!isQuestionFolderName(name, folderNames)
                    && holdsQuestionFolder(listFolders(folder),
                    folderNames)) {
                extraFolders.add(folder);
            }
        }

        if (extraFolders.size() != 1) {
            return null;
        }
        return extraFolders.get(0);
    }

    // Says whether a name is a question folder name, in any case.
    private boolean isQuestionFolderName(String name,
            List<String> folderNames) {
        for (String folderName : folderNames) {
            if (folderName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Moves folders or files to new paths, putting them back if a move
    // fails.
    private void moveFolders(List<Path> folders,
            List<Path> targets) throws FixException {
        for (int i = 0; i < folders.size(); i++) {
            try {
                moveFolder(folders.get(i), targets.get(i));
            } catch (FixException exception) {
                putBack(targets, folders, i, exception);
                throw exception;
            }
        }
    }

    // Moves the first few moved folders back after a failed move.
    private void putBack(List<Path> targets, List<Path> folders,
            int movedCount, FixException failure) {
        for (int i = 0; i < movedCount; i++) {
            try {
                Files.move(targets.get(i), folders.get(i));
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
    }

    // Moves one folder or file to a new path, whose name may differ in
    // case.
    private void moveFolder(Path folder, Path target) throws FixException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException(target.getFileName() + " already exists "
                    + "in " + target.getParent().getFileName()
                    + ", so nothing was moved.");
        }

        try {
            Files.move(folder, target);
        } catch (IOException exception) {
            throw new FixException("Could not move " + folder.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }

    // Removes a now empty folder; one that cannot be removed is harmless.
    private boolean removeIfEmpty(Path folder) {
        try {
            if (isEmptyFolder(folder)) {
                Files.delete(folder);
                return true;
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    // Moves entries to another folder, putting them back if a move fails.
    private void moveEntries(List<Path> entries, Path from,
            Path to) throws FixException {
        for (Path entry : entries) {
            Path target = to.resolve(entry.getFileName());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new FixException(entry.getFileName() + " already "
                        + "exists in " + to.getFileName()
                        + ", so nothing was moved.");
            }
        }

        List<Path> moved = new ArrayList<>();
        try {
            for (Path entry : entries) {
                Path target = to.resolve(entry.getFileName());
                Files.move(entry, target);
                moved.add(target);
            }
        } catch (IOException exception) {
            moveBack(moved, from, exception);
            throw new FixException("Could not move the folders: "
                    + exception.getMessage(), exception);
        }
    }

    // Moves entries back after a failed move.
    private void moveBack(List<Path> moved, Path from,
            IOException failure) {
        for (Path entry : moved) {
            try {
                Files.move(entry, from.resolve(entry.getFileName()));
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
    }

    // Removes the username folder made by a fix that then failed.
    private void removeEmptyFolder(Path folder, FixException failure) {
        try {
            Files.deleteIfExists(folder);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    // Lists the entries of a folder in name order.
    private List<Path> listEntries(Path folder,
            boolean includeHidden) throws FixException {
        List<Path> entries = new ArrayList<>();

        try {
            DirectoryStream<Path> children = Files.newDirectoryStream(folder);
            try {
                for (Path child : children) {
                    if (includeHidden || !isHidden(child)) {
                        entries.add(child);
                    }
                }
            } finally {
                children.close();
            }
        } catch (IOException exception) {
            throw new FixException("Could not list "
                    + folder.getFileName() + ": " + exception.getMessage(),
                    exception);
        }

        Collections.sort(entries);
        return entries;
    }
}
