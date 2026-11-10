/*
 * File: AssessmentFolderScanner.java
 *
 * Purpose: Reads a tester folder and a starter folder and works out the
 * lists an assessment needs: the data files the testers read, the class
 * files given without source, the starter folder names a student may
 * forget to rename, and the other source files of each question.
 *
 * Where it fits: The assessment editor calls it after a folder is chosen,
 * so these fields fill in by themselves, and GradingService calls it for
 * each question's other source files. It only reads the folders.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.testcase.TesterMarks;
import gradezzy.util.FileUtils;

/**
 * Finds data files, provided class files and starter folder names.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public final class AssessmentFolderScanner {

    // The file ending of Java source and of compiled classes
    private static final String JAVA = ".java";
    private static final String CLASS = ".class";

    // A file whose name starts with this is notes for people, not data
    private static final String README = "readme";

    // How deep the starter folder is searched for class files
    private static final int MAX_DEPTH = 6;

    // How deep to look for a folder's own files, not its subfolders'
    private static final int OWN_FILES_DEPTH = 1;

    // The start of a hidden file or folder name, such as .DS_Store
    private static final String HIDDEN_PREFIX = ".";

    // Ends each tester's source when they are read into one text
    private static final String LINE_END = "\n";

    // A starter folder whose only folder is not a question folder is
    // wrapped in one more folder
    private static final int ONLY_FOLDER = 1;

    // The lower-case letters at the end of an id, such as the a of Q1a
    private static final String ID_LETTERS = "[a-z]+$";

    // Not used: every method is static.
    private AssessmentFolderScanner() {
    }

    /**
     * Fills in each question's other source files from the starter
     * folder: the .java files in the question's folder that are not the
     * answer file of any question, such as GoldMember.java next to
     * Q3.java. A folder shared by several questions, such as Q2 with
     * Q2a.java and Q2b.java, gives its other files to the first of them,
     * so each file is checked once. Earlier names are replaced, and
     * nothing is added when the starter folder cannot be read.
     *
     * <p>Tier: SHOULD.
     *
     * @param assessment the assessment whose questions are filled in
     * @param starterFolder the starter folder, or null
     */
    public static void fillExtraFiles(Assessment assessment,
            Path starterFolder) {
        List<String> answerFiles = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            question.getExtraFileNames().clear();
            String answerFile = question.getFileName().toLowerCase(
                    Locale.ROOT);
            answerFiles.add(answerFile);
        }
        boolean hasStarter = starterFolder != null
                && Files.isDirectory(starterFolder);
        if (!hasStarter) {
            return;
        }

        List<String> foldersDone = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            String folderName = question.getFolderName().toLowerCase(
                    Locale.ROOT);
            if (foldersDone.contains(folderName)) {
                continue;
            }
            foldersDone.add(folderName);
            Path folder = starterFolder.resolve(question.getFolderName());
            for (String name : listJavaFiles(folder)) {
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (!answerFiles.contains(lowerName)) {
                    question.getExtraFileNames().add(name);
                }
            }
        }
    }

    // Lists the names of the .java files directly in a folder, sorted;
    // empty when there is no such folder or it cannot be read.
    private static List<String> listJavaFiles(Path folder) {
        TreeSet<String> names = new TreeSet<>();
        if (!Files.isDirectory(folder)) {
            return new ArrayList<>(names);
        }
        try {
            for (Path file : listChildren(folder, OWN_FILES_DEPTH)) {
                String name = file.getFileName().toString();
                boolean isJavaFile = Files.isRegularFile(file)
                        && name.endsWith(JAVA);
                if (isJavaFile) {
                    names.add(name);
                }
            }
        } catch (IOException exception) {
            names.clear();
        }
        return new ArrayList<>(names);
    }

    /**
     * Lists the files in a tester folder that the testers read: every file
     * that is not Java source or a class file and whose name appears in a
     * tester's source. When no tester names any, every such file except a
     * readme is listed.
     *
     * <p>Tier: SHOULD.
     *
     * @param testersFolder the tester folder
     * @return the file names, sorted; empty when the folder cannot be read
     */
    public static List<String> findDataFiles(Path testersFolder) {
        List<String> found = new ArrayList<>();
        if (testersFolder == null || !Files.isDirectory(testersFolder)) {
            return found;
        }
        List<Path> files = null;
        try {
            files = listChildren(testersFolder, OWN_FILES_DEPTH);
        } catch (IOException exception) {
            return found;
        }
        List<String> others = new ArrayList<>();
        String allSource = "";
        for (Path file : files) {
            String name = file.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!Files.isRegularFile(file)
                    || name.startsWith(HIDDEN_PREFIX)) {
                continue;
            }
            if (lower.endsWith(JAVA)) {
                allSource += read(file) + LINE_END;
            } else if (!lower.endsWith(CLASS)) {
                others.add(name);
            }
        }
        for (String name : others) {
            if (allSource.contains(name)) {
                found.add(name);
            }
        }
        if (found.isEmpty()) {
            for (String name : others) {
                if (!name.toLowerCase(Locale.ROOT).startsWith(README)) {
                    found.add(name);
                }
            }
        }
        found.sort(String.CASE_INSENSITIVE_ORDER);
        return found;
    }

    /**
     * Lists the class files in a starter folder that have no Java source
     * beside them, such as DataException.class.
     *
     * <p>Tier: SHOULD.
     *
     * @param starterFolder the starter folder
     * @return the file names, sorted, each once; empty when the folder
     *         cannot be read
     */
    public static List<String> findProvidedClassFiles(Path starterFolder) {
        TreeSet<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (starterFolder == null || !Files.isDirectory(starterFolder)) {
            return new ArrayList<>(found);
        }
        List<Path> files = null;
        try {
            files = listChildren(starterFolder, MAX_DEPTH);
        } catch (IOException exception) {
            return new ArrayList<>();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            boolean isClassFile = Files.isRegularFile(file)
                    && name.toLowerCase(Locale.ROOT).endsWith(CLASS);
            if (!isClassFile) {
                continue;
            }
            int baseLength = name.length() - CLASS.length();
            String base = name.substring(0, baseLength);
            if (!Files.exists(file.resolveSibling(base + JAVA))) {
                found.add(name);
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * Lists the starter folder names a student may forget to rename: the
     * starter folder's own name, or, when it holds just one folder that
     * is not a question folder, that folder's name.
     *
     * <p>Tier: SHOULD.
     *
     * @param starterFolder the starter folder
     * @param questionFolders the question folder names, such as Q1
     * @return the names; empty when the folder cannot be read
     */
    public static List<String> findStarterFolderNames(Path starterFolder,
            List<String> questionFolders) {
        List<String> found = new ArrayList<>();
        boolean noFolder = starterFolder == null
                || !Files.isDirectory(starterFolder)
                || starterFolder.getFileName() == null;
        if (noFolder) {
            return found;
        }
        List<Path> children = null;
        try {
            children = listChildren(starterFolder, OWN_FILES_DEPTH);
        } catch (IOException exception) {
            return found;
        }
        List<Path> folders = new ArrayList<>();
        for (Path child : children) {
            boolean visibleFolder = Files.isDirectory(child)
                    && !child.getFileName().toString()
                    .startsWith(HIDDEN_PREFIX);
            if (visibleFolder) {
                folders.add(child);
            }
        }
        boolean oneOtherFolder = folders.size() == ONLY_FOLDER
                && !isQuestionFolder(folders.get(0).getFileName()
                        .toString(), questionFolders);
        if (oneOtherFolder) {
            found.add(folders.get(0).getFileName().toString());
        } else {
            found.add(starterFolder.getFileName().toString());
        }
        return found;
    }

    /**
     * Works out the questions from a tester folder: one per tester, such
     * as Q1aTester.java for Q1a, with its answer file Q1a.java and its
     * marks read from the tester. The answer's folder is the one that
     * holds Q1a.java in the starter folder, or else the id without its
     * last letters, so Q1a is in Q1 and Q3 in Q3.
     *
     * <p>Tier: SHOULD.
     *
     * @param testersFolder the tester folder
     * @param testerSuffix the end of a tester's class name, such as Tester
     * @param starterFolder the starter folder, or null
     * @return the questions in the order of their ids; empty when the
     *         folder cannot be read
     */
    public static List<Question> findQuestions(Path testersFolder,
            String testerSuffix, Path starterFolder) {
        List<Question> found = new ArrayList<>();
        boolean noTesters = testersFolder == null
                || !Files.isDirectory(testersFolder)
                || testerSuffix == null || testerSuffix.isEmpty();
        if (noTesters) {
            return found;
        }
        String ending = testerSuffix + JAVA;
        List<Path> files = null;
        try {
            files = listChildren(testersFolder, OWN_FILES_DEPTH);
        } catch (IOException exception) {
            return found;
        }
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Path file : files) {
            String name = file.getFileName().toString();
            boolean isTester = Files.isRegularFile(file)
                    && name.endsWith(ending)
                    && name.length() > ending.length();
            if (isTester) {
                int idLength = name.length() - ending.length();
                ids.add(name.substring(0, idLength));
            }
        }
        for (String id : ids) {
            String folderName = folderOf(id, starterFolder);
            Path tester = testersFolder.resolve(id + ending);
            double maxScore = readMaxScore(tester);
            found.add(new Question(id, folderName, id + JAVA, maxScore));
        }
        return found;
    }

    // Reads a tester's marks; one that cannot be read stays unknown, so
    // the instructor types it.
    private static double readMaxScore(Path tester) {
        try {
            return TesterMarks.readMaxScore(tester);
        } catch (IOException exception) {
            return TesterMarks.UNKNOWN;
        }
    }

    // Finds the folder that holds a question's answer in the starter
    // folder, or guesses it from the id.
    private static String folderOf(String id, Path starterFolder) {
        for (Path file : listOrEmpty(starterFolder)) {
            Path parent = file.getParent();
            String name = file.getFileName().toString();
            boolean isAnswerInFolder = name.equalsIgnoreCase(id + JAVA)
                    && parent != null && !parent.equals(starterFolder);
            if (isAnswerInFolder) {
                return parent.getFileName().toString();
            }
        }
        String folder = id.replaceAll(ID_LETTERS, "");
        if (folder.isEmpty()) {
            return id;
        }
        return folder;
    }

    // Says whether a folder name is one of the question folders.
    private static boolean isQuestionFolder(String name,
            List<String> questionFolders) {
        for (String folder : questionFolders) {
            if (folder.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Lists what a starter folder holds, up to MAX_DEPTH folders down;
    // empty when there is no folder or it cannot be read, so the caller
    // guesses instead.
    private static List<Path> listOrEmpty(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return new ArrayList<>();
        }
        try {
            return listChildren(folder, MAX_DEPTH);
        } catch (IOException exception) {
            return new ArrayList<>();
        }
    }

    // Lists the files and folders in a folder, then in its folders, down
    // to the given depth: 1 lists only what the folder holds. Each
    // folder is followed by what it holds. Links to folders are not
    // followed.
    private static List<Path> listChildren(Path folder, int depth)
            throws IOException {
        List<Path> found = new ArrayList<>();
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(folder);
            for (Path child : children) {
                found.add(child);
                boolean goDeeper = depth > OWN_FILES_DEPTH
                        && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
                if (goDeeper) {
                    int deeper = depth - 1;
                    found.addAll(listChildren(child, deeper));
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
        return found;
    }

    // Reads a source file, or returns "" when it cannot be read.
    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }
}
