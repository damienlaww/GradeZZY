/*
 * File: AnswerFolderRule.java
 *
 * Purpose: Finds the folder of a working copy that holds the question
 * folders, by one rule that the checks, the fixes and grading all share.
 *
 * Where it fits: The validators, the folder fixers and
 * QuestionFolderFinder call it, so a folder the checks cannot choose is
 * never graded from a guess, and a folder they choose is the one graded.
 */
package gradezzy.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Finds the folder that holds the question folders (the content root),
 * the extra folders they may sit in, and the places that hold them when
 * there is more than one.
 *
 * <p>The rule: every place that holds question folders is a candidate.
 * That is the top of the working copy, and each top folder that holds
 * them directly or through an extra folder (see findExtraFolder). Folders
 * an IDE or macOS fills with copies (out, bin, __MACOSX) and hidden
 * folders never count. When there are several candidates, those whose
 * question folders hold no Java file, such as a stray Q1 folder with only
 * notes in it, are dropped. One candidate left is the content root; more
 * than one means GradeZZY cannot tell which to grade, and the content
 * root is null. With no candidate, the folder named after the username is
 * used, else the only top folder, else none.
 *
 * <p>Tier: MUST.
 */
public final class AnswerFolderRule {

    // The start of a hidden name, such as .idea, never part of an answer
    private static final String HIDDEN_PREFIX = ".";

    // The ending of a Java source file, compared in lower case
    private static final String JAVA_EXTENSION = ".java";

    // The ending of a compiled file, compared in lower case
    private static final String CLASS_EXTENSION = ".class";

    // Folders an IDE or macOS fills with copies, never the answer itself
    private static final String[] SKIPPED_FOLDER_NAMES = {
        "out", "bin", "__MACOSX"
    };

    // How many folders down a chain of single folders, or a search for a
    // Java file, goes, so an odd upload cannot make the walk go on and on
    private static final int MAX_DEPTH = 16;

    // A folder of a chain holds exactly this many visible sub-folders
    private static final int SINGLE_FOLDER = 1;

    /**
     * Not used: every method here is static.
     */
    private AnswerFolderRule() {
        // utility class, never instantiated
    }

    /**
     * Finds the folder that holds the question folders, by the rule in
     * the class comment.
     *
     * @param workingCopy the working copy
     * @param username the student's username
     * @param folderNames the question folder names, such as Q1
     * @return the folder, or null when there is none or several places
     *         hold question folders
     * @throws IOException if a folder cannot be listed
     */
    public static Path findContentRoot(Path workingCopy, String username,
            List<String> folderNames) throws IOException {
        boolean noFolder = workingCopy == null
                || !Files.isDirectory(workingCopy, LinkOption.NOFOLLOW_LINKS);
        if (noFolder) {
            return null;
        }

        List<Path> holders = findAnswerHolders(workingCopy, folderNames);
        if (holders.size() == SINGLE_FOLDER) {
            return holders.get(0);
        } else if (holders.size() > SINGLE_FOLDER) {
            return null;
        }

        List<Path> topFolders = listFolders(workingCopy);
        Path usernameFolder = findExact(topFolders, username);
        if (usernameFolder != null) {
            return usernameFolder;
        } else if (topFolders.size() == SINGLE_FOLDER) {
            return topFolders.get(0);
        }
        return null;
    }

    /**
     * Lists the places that hold question folders: the working copy
     * itself when they are at its top, and each top folder that holds
     * them directly or through an extra folder. When there are several,
     * those whose question folders hold no Java file are left out, unless
     * that would leave none.
     *
     * @param workingCopy the working copy
     * @param folderNames the question folder names
     * @return the places, the working copy first and then in name order;
     *         more than one means the answer folder cannot be chosen
     * @throws IOException if a folder cannot be listed
     */
    public static List<Path> findAnswerHolders(Path workingCopy,
            List<String> folderNames) throws IOException {
        List<Path> holders = new ArrayList<>();
        boolean noFolder = workingCopy == null
                || !Files.isDirectory(workingCopy, LinkOption.NOFOLLOW_LINKS);
        if (noFolder) {
            return holders;
        }

        List<Path> topFolders = listFolders(workingCopy);
        if (holdsQuestionFolder(topFolders, folderNames)) {
            holders.add(workingCopy);
        }
        for (Path folder : topFolders) {
            String name = folder.getFileName().toString();
            boolean candidate = !isQuestionFolderName(name, folderNames)
                    && !isSkippedFolder(name)
                    && findQuestionPlace(folder, folderNames) != null;
            if (candidate) {
                holders.add(folder);
            }
        }

        if (holders.size() <= SINGLE_FOLDER) {
            return holders;
        }
        return keepHoldersWithJava(holders, folderNames);
    }

    /**
     * Finds the one folder of the content root that is not a question
     * folder but leads to question folders: it holds them itself (one
     * level too deep, such as src in an IntelliJ project), or it starts a
     * chain of folders that each hold only one folder and no file, as in
     * Users/jy/Desktop/ping.lee.2023 from a command-line zip. When
     * several do, those whose question folders hold no Java file are left
     * out.
     *
     * @param contentRoot the folder that should hold the question folders
     * @param folderNames the question folder names
     * @return the first folder of the chain, or null when there is none
     *         or more than one
     * @throws IOException if a folder cannot be listed
     */
    public static Path findExtraFolder(Path contentRoot,
            List<String> folderNames) throws IOException {
        List<Path> extraFolders = new ArrayList<>();
        for (Path folder : listFolders(contentRoot)) {
            String name = folder.getFileName().toString();
            boolean extra = !isQuestionFolderName(name, folderNames)
                    && !isSkippedFolder(name)
                    && findChainEnd(folder, folderNames) != null;
            if (extra) {
                extraFolders.add(folder);
            }
        }

        if (extraFolders.size() > SINGLE_FOLDER) {
            extraFolders = keepChainsWithJava(extraFolders, folderNames);
        }
        if (extraFolders.size() != SINGLE_FOLDER) {
            return null;
        }
        return extraFolders.get(0);
    }

    /**
     * Follows a chain of folders that each hold only one folder and no
     * visible file, starting at the given folder, to the first folder
     * that holds a question folder.
     *
     * @param folder the first folder of the chain
     * @param folderNames the question folder names
     * @return the folder that holds the question folders, maybe the
     *         given one, or null when the chain ends without one
     * @throws IOException if a folder cannot be listed
     */
    public static Path findChainEnd(Path folder, List<String> folderNames)
            throws IOException {
        Path current = folder;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            List<Path> subfolders = listFolders(current);
            if (holdsQuestionFolder(subfolders, folderNames)) {
                return current;
            }

            boolean onlyOneFolder = subfolders.size() == SINGLE_FOLDER
                    && listVisibleFiles(current).isEmpty();
            if (!onlyOneFolder) {
                return null;
            }
            current = subfolders.get(0);
        }
        return null;
    }

    /**
     * Finds a question folder dragged into its own folder, such as
     * Q1/Q1/Q1a.java: the folder holds only one visible folder, named
     * like it in any case, and no visible file but compiled ones, such as
     * the provided class files GradeZZY copies in.
     *
     * @param questionFolder the question folder, such as Q1
     * @param folderName the question folder name
     * @return the inner folder, or null when the folder is not like that
     * @throws IOException if the folder cannot be listed
     */
    public static Path findNestedQuestionFolder(Path questionFolder,
            String folderName) throws IOException {
        if (questionFolder == null) {
            return null;
        }

        List<Path> subfolders = listFolders(questionFolder);
        boolean onlyOneFolder = subfolders.size() == SINGLE_FOLDER
                && holdsOnlyCompiledFiles(questionFolder);
        if (!onlyOneFolder) {
            return null;
        }

        Path inner = subfolders.get(0);
        String innerName = inner.getFileName().toString();
        if (!innerName.equalsIgnoreCase(folderName)) {
            return null;
        }
        return inner;
    }

    /**
     * Says whether any of the folders is named like a question folder, in
     * any case.
     *
     * @param folders the folders to search
     * @param folderNames the question folder names
     * @return true if one of them is named like a question folder
     */
    public static boolean holdsQuestionFolder(List<Path> folders,
            List<String> folderNames) {
        for (Path folder : folders) {
            String name = folder.getFileName().toString();
            if (isQuestionFolderName(name, folderNames)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Says whether a name is a question folder name, in any case.
     *
     * @param name the folder name
     * @param folderNames the question folder names
     * @return true if it is one of them
     */
    public static boolean isQuestionFolderName(String name,
            List<String> folderNames) {
        for (String folderName : folderNames) {
            if (folderName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists the visible folders directly inside a folder, in name order.
     * Hidden folders and symbolic links are left out.
     *
     * @param directory the folder to list
     * @return the folders found
     * @throws IOException if the folder cannot be listed
     */
    public static List<Path> listFolders(Path directory) throws IOException {
        List<Path> folders = new ArrayList<>();
        for (Path child : listVisible(directory)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                folders.add(child);
            }
        }
        return folders;
    }

    // Returns the folder that holds the question folders inside a top
    // folder: the folder itself, or the end of its extra folder's chain,
    // or null when it leads to none.
    private static Path findQuestionPlace(Path folder,
            List<String> folderNames) throws IOException {
        if (holdsQuestionFolder(listFolders(folder), folderNames)) {
            return folder;
        }

        Path extraFolder = findExtraFolder(folder, folderNames);
        if (extraFolder == null) {
            return null;
        }
        return findChainEnd(extraFolder, folderNames);
    }

    // Keeps the places whose question folders hold a Java file, or all of
    // them when none does.
    private static List<Path> keepHoldersWithJava(List<Path> holders,
            List<String> folderNames) throws IOException {
        List<Path> withJava = new ArrayList<>();
        for (Path holder : holders) {
            Path place = holder;
            if (!holdsQuestionFolder(listFolders(holder), folderNames)) {
                place = findQuestionPlace(holder, folderNames);
            }
            if (questionFoldersHoldJava(place, folderNames)) {
                withJava.add(holder);
            }
        }

        if (withJava.isEmpty()) {
            return holders;
        }
        return withJava;
    }

    // Keeps the extra folders whose chain ends in question folders that
    // hold a Java file, or all of them when none does.
    private static List<Path> keepChainsWithJava(List<Path> extraFolders,
            List<String> folderNames) throws IOException {
        List<Path> withJava = new ArrayList<>();
        for (Path extraFolder : extraFolders) {
            Path chainEnd = findChainEnd(extraFolder, folderNames);
            if (questionFoldersHoldJava(chainEnd, folderNames)) {
                withJava.add(extraFolder);
            }
        }

        if (withJava.isEmpty()) {
            return extraFolders;
        }
        return withJava;
    }

    // Says whether a question folder in the place holds a Java file, at
    // any depth.
    private static boolean questionFoldersHoldJava(Path place,
            List<String> folderNames) throws IOException {
        if (place == null) {
            return false;
        }

        for (Path folder : listFolders(place)) {
            String name = folder.getFileName().toString();
            boolean javaInside = isQuestionFolderName(name, folderNames)
                    && holdsJavaFile(folder, 0);
            if (javaInside) {
                return true;
            }
        }
        return false;
    }

    // Says whether a folder, or a visible folder inside it, holds a Java
    // source file.
    private static boolean holdsJavaFile(Path folder, int depth)
            throws IOException {
        if (depth >= MAX_DEPTH) {
            return false;
        }

        for (Path file : listVisibleFiles(folder)) {
            String name = file.getFileName().toString()
                    .toLowerCase(Locale.ROOT);
            if (name.endsWith(JAVA_EXTENSION)) {
                return true;
            }
        }

        int nextDepth = depth + 1;
        for (Path subfolder : listFolders(folder)) {
            if (holdsJavaFile(subfolder, nextDepth)) {
                return true;
            }
        }
        return false;
    }

    // Says whether every visible file directly in a folder is compiled,
    // which is also true when there is none.
    private static boolean holdsOnlyCompiledFiles(Path folder)
            throws IOException {
        for (Path file : listVisibleFiles(folder)) {
            String name = file.getFileName().toString()
                    .toLowerCase(Locale.ROOT);
            if (!name.endsWith(CLASS_EXTENSION)) {
                return false;
            }
        }
        return true;
    }

    // Says whether a folder name is one an IDE or macOS fills with copies.
    private static boolean isSkippedFolder(String name) {
        for (String skipped : SKIPPED_FOLDER_NAMES) {
            if (skipped.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Returns the path whose name matches exactly, including case, or
    // null. Names are compared as text because Windows and macOS would
    // also find q1 when asked for Q1.
    private static Path findExact(List<Path> paths, String wantedName) {
        for (Path path : paths) {
            if (path.getFileName().toString().equals(wantedName)) {
                return path;
            }
        }
        return null;
    }

    // Lists the visible ordinary files directly inside a folder.
    private static List<Path> listVisibleFiles(Path directory)
            throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path child : listVisible(directory)) {
            if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                files.add(child);
            }
        }
        return files;
    }

    // Lists what is directly inside a folder, in name order, without
    // hidden names. The listing is closed even if reading it fails.
    private static List<Path> listVisible(Path directory)
            throws IOException {
        List<Path> children = new ArrayList<>();
        DirectoryStream<Path> listing = null;
        try {
            listing = Files.newDirectoryStream(directory);
            for (Path child : listing) {
                String name = child.getFileName().toString();
                if (!name.startsWith(HIDDEN_PREFIX)) {
                    children.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(listing);
        }
        Collections.sort(children);
        return children;
    }
}
