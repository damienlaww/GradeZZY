/*
 * File: AssessmentFolderScanner.java
 *
 * Purpose: Reads a tester folder and a starter folder and works out three
 * lists an assessment needs: the data files the testers read, the class
 * files given without source, and the starter folder names a student may
 * forget to rename.
 *
 * Where it fits: The assessment editor calls it after a folder is chosen,
 * so these fields fill in by themselves. It only reads; nothing changes.
 */
package gradezzy.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Stream;

import gradezzy.model.Question;
import gradezzy.testcase.TesterMarks;

/**
 * Finds data files, provided class files and starter folder names.
 */
public final class AssessmentFolderScanner {

    // The file ending of Java source and of compiled classes
    private static final String JAVA = ".java";
    private static final String CLASS = ".class";

    // A file whose name starts with this is notes for people, not data
    private static final String README = "readme";

    // How deep the starter folder is searched for class files
    private static final int MAX_DEPTH = 6;

    // Not used: every method is static.
    private AssessmentFolderScanner() {
    }

    /**
     * Lists the files in a tester folder that the testers read: every file
     * that is not Java source or a class file and whose name appears in a
     * tester's source. When no tester names any, every such file except a
     * readme is listed.
     *
     * @param testersFolder the tester folder
     * @return the file names, sorted; empty when the folder cannot be read
     */
    public static List<String> findDataFiles(Path testersFolder) {
        List<String> found = new ArrayList<>();
        if (testersFolder == null || !Files.isDirectory(testersFolder)) {
            return found;
        }
        List<String> others = new ArrayList<>();
        StringBuilder sources = new StringBuilder();
        try (Stream<Path> files = Files.list(testersFolder)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!Files.isRegularFile(file) || name.startsWith(".")) {
                    continue;
                }
                if (lower.endsWith(JAVA)) {
                    sources.append(read(file)).append('\n');
                } else if (!lower.endsWith(CLASS)) {
                    others.add(name);
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            return found;
        }
        String allSource = sources.toString();
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
     * @param starterFolder the starter folder
     * @return the file names, sorted, each once; empty when the folder
     *         cannot be read
     */
    public static List<String> findProvidedClassFiles(Path starterFolder) {
        TreeSet<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (starterFolder == null || !Files.isDirectory(starterFolder)) {
            return new ArrayList<>(found);
        }
        try (Stream<Path> files = Files.walk(starterFolder, MAX_DEPTH)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file)
                        || !name.toLowerCase(Locale.ROOT).endsWith(CLASS)) {
                    continue;
                }
                String base = name.substring(0, name.length()
                        - CLASS.length());
                if (!Files.exists(file.resolveSibling(base + JAVA))) {
                    found.add(name);
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            return new ArrayList<>();
        }
        return new ArrayList<>(found);
    }

    /**
     * Lists the starter folder names a student may forget to rename: the
     * starter folder's own name, or, when it holds just one folder that
     * is not a question folder, that folder's name.
     *
     * @param starterFolder the starter folder
     * @param questionFolders the question folder names, such as Q1
     * @return the names; empty when the folder cannot be read
     */
    public static List<String> findStarterFolderNames(Path starterFolder,
            List<String> questionFolders) {
        List<String> found = new ArrayList<>();
        if (starterFolder == null || !Files.isDirectory(starterFolder)
                || starterFolder.getFileName() == null) {
            return found;
        }
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> children = Files.list(starterFolder)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (Files.isDirectory(child)
                        && !child.getFileName().toString().startsWith(".")) {
                    folders.add(child);
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            return found;
        }
        if (folders.size() == 1 && !isQuestionFolder(
                folders.get(0).getFileName().toString(), questionFolders)) {
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
     * @param testersFolder the tester folder
     * @param testerSuffix the end of a tester's class name, such as Tester
     * @param starterFolder the starter folder, or null
     * @return the questions in the order of their ids; empty when the
     *         folder cannot be read
     */
    public static List<Question> findQuestions(Path testersFolder,
            String testerSuffix, Path starterFolder) {
        List<Question> found = new ArrayList<>();
        if (testersFolder == null || !Files.isDirectory(testersFolder)
                || testerSuffix == null || testerSuffix.isEmpty()) {
            return found;
        }
        String ending = testerSuffix + JAVA;
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (Stream<Path> files = Files.list(testersFolder)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.endsWith(ending)
                        && name.length() > ending.length()) {
                    ids.add(name.substring(0, name.length()
                            - ending.length()));
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            return found;
        }
        for (String id : ids) {
            double max = TesterMarks.UNKNOWN;
            try {
                max = TesterMarks.readMaxScore(testersFolder.resolve(id
                        + ending));
            } catch (IOException | UncheckedIOException exception) {
                // The mark stays unknown and can be typed
            }
            found.add(new Question(id, folderOf(id, starterFolder),
                    id + JAVA, max));
        }
        return found;
    }

    // Finds the folder that holds a question's answer in the starter
    // folder, or guesses it from the id.
    private static String folderOf(String id, Path starterFolder) {
        if (starterFolder != null && Files.isDirectory(starterFolder)) {
            try (Stream<Path> files = Files.walk(starterFolder, MAX_DEPTH)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    Path parent = file.getParent();
                    if (file.getFileName().toString().equalsIgnoreCase(
                            id + JAVA) && parent != null
                            && !parent.equals(starterFolder)) {
                        return parent.getFileName().toString();
                    }
                }
            } catch (IOException | UncheckedIOException exception) {
                // Guess from the id instead
            }
        }
        String folder = id.replaceAll("[a-z]+$", "");
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

    // Reads a source file, or returns "" when it cannot be read.
    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException exception) {
            return "";
        }
    }
}
