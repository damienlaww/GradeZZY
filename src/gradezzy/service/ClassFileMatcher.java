/*
 * File: ClassFileMatcher.java
 *
 * Purpose: Checks that the chosen score sheet, tester folder and starter
 * folder belong with the uploads, and looks nearby for one that fits
 * them better. A score sheet fits when most uploads are on it. Testers
 * and starter files fit when they use the methods the students' files
 * declare, such as getIsogramWords in Q1a.java.
 *
 * Where it fits: The Set up step calls it through LiveBackend whenever
 * the uploads or a class file change, on a background thread. It only
 * reads files: nothing is compiled or unzipped to disk, and grading is
 * never blocked by what it finds.
 */
package gradezzy.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Student;
import gradezzy.util.FileUtils;

/**
 * Checks a score sheet, tester folder or starter folder against the
 * uploads and suggests a better one nearby.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ClassFileMatcher {

    // How many uploads are read to learn the students' methods. A sample
    // is enough to tell one lab test from another and keeps Set up fast.
    private static final int MAX_UPLOADS_READ = 12;

    // How many files or folders nearby are tried as a suggestion
    private static final int MAX_CANDIDATES = 80;

    // Largest question file read from an upload, in bytes (1 MB)
    private static final int MAX_SOURCE_BYTES = 1048576;

    // How deep a question file is looked for in an unzipped upload
    private static final int MAX_FOLDER_DEPTH = 4;

    // How many tokens after "static" a method name is looked for. A real
    // declaration has a few modifiers and a type; the limit keeps a file
    // made of "static" words from taking long.
    private static final int MAX_DECLARATION_TOKENS = 40;

    // How many unknown method names a problem names
    private static final int MAX_NAMES_SHOWN = 2;

    // A count fits when it is at least half of its total
    private static final int HALF = 2;

    // Every file the check reads or lists has one of these endings
    private static final String JAVA_EXTENSION = ".java";
    private static final String CSV_EXTENSION = ".csv";
    private static final String ZIP_EXTENSION = ".zip";

    // The method every program may declare, which says nothing about the
    // lab test
    private static final String MAIN_METHOD = "main";

    // The word in front of a method that belongs to the class
    private static final String STATIC_KEYWORD = "static";

    // Folders that macOS adds to a zip, and the start of hidden names
    private static final String MAC_FOLDER = "__MACOSX";
    private static final String HIDDEN_PREFIX = ".";

    // Separator between the folders of a zip entry name
    private static final String ENTRY_SEPARATOR = "/";

    // Words that are followed by "(" but are not method calls
    private static final String[] NOT_CALLS = {"if", "for", "while",
        "switch", "catch", "return", "synchronized", "super", "this",
        "new", "assert", "throw"};

    // The words in a problem about testers or starter files from another
    // lab test
    private static final String OTHER_LAB_TESTERS = "These testers are for "
            + "a different lab test.";
    private static final String OTHER_LAB_STARTER = "These starter files "
            + "are for a different lab test.";

    // The application settings
    private final AppConfig config;

    // The questions of the lab test being graded
    private final Assessment assessment;

    // For each question id, how many uploads declare each method name
    private final Map<String, Map<String, Integer>> uploadMethods =
            new HashMap<>();

    // For each question id, how many uploads had that question's file
    private final Map<String, Integer> filesRead = new HashMap<>();

    /**
     * Creates a matcher for the lab test being graded.
     *
     * @param config the application settings
     * @param assessment the questions of the lab test
     */
    public ClassFileMatcher(AppConfig config, Assessment assessment) {
        this.config = config;
        this.assessment = assessment;
    }

    // ----------------------------------------------------- score sheet

    /**
     * Checks that most uploads are on a score sheet. When they are not,
     * or the sheet cannot be read, it looks for a CSV nearby that lists
     * more of them: in the sheet's folder, the folder above it, the
     * uploads' folders and the score sheet in Settings.
     *
     * <p>Tier: SHOULD.
     *
     * @param sheet the chosen score sheet
     * @param usernames the usernames of the uploads
     * @param uploads the chosen zip files and folders
     * @return the problem and a better sheet, either of which may be
     *         missing
     */
    public ClassFileMatch checkScoreSheet(Path sheet, List<String> usernames,
            List<Path> uploads) {
        Set<String> wanted = new HashSet<>();
        for (String username : usernames) {
            wanted.add(username.toLowerCase(Locale.ROOT));
        }
        int total = wanted.size();

        String problem = null;
        int onSheet = 0;
        try {
            onSheet = countOnSheet(sheet, wanted);
        } catch (IngestException exception) {
            problem = "This file cannot be used as the score sheet. "
                    + describeRosterProblem(exception);
        }

        if (total == 0) {
            return new ClassFileMatch(problem, null, "");
        }
        if (problem == null && onSheet * HALF >= total) {
            return ClassFileMatch.fits();
        }
        if (problem == null) {
            problem = describeStudentsOnSheet(onSheet, total);
        }

        Path configured = readConfiguredPath(ConfigKeys.SCORE_SHEET_FILE);
        Path best = null;
        int bestCount = onSheet;
        for (Path candidate : listCandidateFiles(sheet, uploads,
                configured)) {
            int count = countOnSheetOrNone(candidate, wanted);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        if (best == null || bestCount * HALF < total) {
            return new ClassFileMatch(problem, null, "");
        }
        return new ClassFileMatch(problem, best, ", "
                + describeWhere(best, sheet, configured) + ", lists "
                + describeShare(bestCount, total) + ".");
    }

    // Counts the wanted usernames that are on a score sheet.
    private int countOnSheet(Path sheet, Set<String> wanted)
            throws IngestException {
        List<Student> students = new RosterReader(config).readRoster(sheet);
        int count = 0;
        for (Student student : students) {
            if (wanted.contains(student.getUsername()
                    .toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    // Counts the wanted usernames on a sheet, or -1 when it cannot be
    // read as a score sheet.
    private int countOnSheetOrNone(Path sheet, Set<String> wanted) {
        try {
            return countOnSheet(sheet, wanted);
        } catch (IngestException exception) {
            return -1;
        }
    }

    // Says why a sheet cannot be read, without its full path.
    private String describeRosterProblem(IngestException exception) {
        Throwable cause = exception.getCause();
        if (!(cause instanceof IllegalArgumentException)
                || cause.getMessage() == null) {
            return "It could not be opened.";
        }
        String reason = cause.getMessage().trim();
        if (!reason.endsWith(".")) {
            reason = reason + ".";
        }
        return reason;
    }

    // Says how few of the uploads are on a sheet.
    private String describeStudentsOnSheet(int onSheet, int total) {
        if (total == 1) {
            return "The upload is not on this score sheet.";
        }
        if (onSheet == 0) {
            return "None of the " + total + " uploads are on this score "
                    + "sheet.";
        }
        String verb = " are";
        if (onSheet == 1) {
            verb = " is";
        }
        return "Only " + onSheet + " of the " + total + " uploads" + verb
                + " on this score sheet.";
    }

    // Says how many of the uploads a sheet lists, such as "40 of the 42
    // uploads".
    private String describeShare(int count, int total) {
        if (total == 1) {
            return "the upload";
        }
        if (count == total) {
            return "all " + total + " uploads";
        }
        return count + " of the " + total + " uploads";
    }

    // --------------------------------------------------------- testers

    /**
     * Checks that a tester folder is for this lab test: its testers call
     * methods the uploads declare. When most of them do not, it looks
     * for a tester folder nearby whose testers do.
     *
     * <p>Tier: SHOULD.
     *
     * @param folder the chosen tester folder
     * @param uploads the chosen zip files and folders
     * @return the problem and a better folder, either of which may be
     *         missing
     */
    public ClassFileMatch checkTesters(Path folder, List<Path> uploads) {
        readUploads(uploads);
        if (filesRead.isEmpty()) {
            return ClassFileMatch.fits();
        }

        List<String> tested = new ArrayList<>();
        List<String> unknownNames = new ArrayList<>();
        int fitting = countFittingTesters(folder, tested, unknownNames);
        if (tested.isEmpty() && !holdsTester(folder)) {
            // Set up already says that the folder holds no tester
            return ClassFileMatch.fits();
        }
        if (!tested.isEmpty() && fitting * HALF >= tested.size()) {
            return ClassFileMatch.fits();
        }
        String problem = describeOtherTesters(tested, unknownNames);

        Path configured = readConfiguredPath(ConfigKeys.TESTERS_DIR);
        Path best = null;
        List<String> bestTested = new ArrayList<>();
        int bestFitting = fitting;
        for (Path candidate : listCandidateFolders(folder, uploads,
                configured)) {
            List<String> candidateTested = new ArrayList<>();
            int candidateFitting = countFittingTesters(candidate,
                    candidateTested, new ArrayList<>());
            if (candidateFitting > bestFitting
                    && candidateFitting * HALF >= candidateTested.size()) {
                best = candidate;
                bestTested = candidateTested;
                bestFitting = candidateFitting;
            }
        }
        if (best == null) {
            return new ClassFileMatch(problem, null, "");
        }
        return new ClassFileMatch(problem, best, ", "
                + describeWhere(best, folder, configured)
                + ", matches these uploads. It has a tester for "
                + joinWords(bestTested) + ".");
    }

    // Counts the questions whose tester in a folder calls a method most
    // uploads declare. Adds each question that has a tester to tested,
    // and the methods testers call that no upload declares to
    // unknownNames.
    private int countFittingTesters(Path folder, List<String> tested,
            List<String> unknownNames) {
        String suffix = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX);
        int fitting = 0;
        for (Question question : assessment.getQuestions()) {
            String id = question.getId();
            Path tester = folder.resolve(id + suffix + JAVA_EXTENSION);
            String source = readSmallFile(tester);
            if (countFilesRead(id) > 0 && source != null) {
                tested.add(id);
                List<String> tokens = tokenize(source);
                List<String> called = findCalls(tokens,
                        classNameOf(question));
                called.removeAll(findStaticMethods(tokens));
                if (sharesCommonMethod(id, called)) {
                    fitting++;
                } else {
                    addUnknownNames(id, called, unknownNames);
                }
            }
        }
        return fitting;
    }

    // Says whether a folder holds any tester file, for any lab test.
    private boolean holdsTester(Path folder) {
        String ending = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX)
                + JAVA_EXTENSION;
        try {
            for (Path file : FileUtils.listFiles(folder, JAVA_EXTENSION)) {
                if (file.getFileName().toString().endsWith(ending)) {
                    return true;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    // Says why the testers seem to be for another lab test.
    private String describeOtherTesters(List<String> tested,
            List<String> unknownNames) {
        if (tested.isEmpty()) {
            return OTHER_LAB_TESTERS + " None of them is for "
                    + joinWords(listQuestionIds(), " or ") + ".";
        }
        if (unknownNames.isEmpty()) {
            return OTHER_LAB_TESTERS + " They test none of the methods in "
                    + "the uploads.";
        }
        return OTHER_LAB_TESTERS + " They test "
                + joinWords(firstNames(unknownNames))
                + ", which none of the uploads have.";
    }

    // --------------------------------------------------------- starter

    /**
     * Checks that a starter folder is for this lab test: its question
     * files declare methods the uploads declare too. When most of them do
     * not, or the folder holds no starter file, it looks for a starter
     * folder nearby whose files do.
     *
     * <p>Tier: SHOULD.
     *
     * @param folder the chosen starter folder
     * @param uploads the chosen zip files and folders
     * @return the problem and a better folder, either of which may be
     *         missing
     */
    public ClassFileMatch checkStarter(Path folder, List<Path> uploads) {
        readUploads(uploads);
        if (filesRead.isEmpty()) {
            return ClassFileMatch.fits();
        }

        List<String> checked = new ArrayList<>();
        List<String> unknownNames = new ArrayList<>();
        int fitting = countFittingStarters(folder, checked, unknownNames);
        if (!checked.isEmpty() && fitting * HALF >= checked.size()) {
            return ClassFileMatch.fits();
        }
        // With no starter file at all, Set up already says so; only a
        // better folder is added.
        String problem = null;
        if (!checked.isEmpty()) {
            problem = describeOtherStarter(unknownNames);
        }

        Path configured = readConfiguredPath(ConfigKeys.STARTER_DIR);
        Path best = null;
        List<String> bestChecked = new ArrayList<>();
        int bestFitting = fitting;
        for (Path candidate : listCandidateFolders(folder, uploads,
                configured)) {
            List<String> candidateChecked = new ArrayList<>();
            int candidateFitting = countFittingStarters(candidate,
                    candidateChecked, new ArrayList<>());
            if (candidateFitting > bestFitting
                    && candidateFitting * HALF >= candidateChecked.size()) {
                best = candidate;
                bestChecked = candidateChecked;
                bestFitting = candidateFitting;
            }
        }
        if (best == null) {
            return new ClassFileMatch(problem, null, "");
        }
        String holds = "It has the starter file of every question.";
        if (bestChecked.size() < assessment.getQuestions().size()) {
            holds = "It has the starter files for " + joinWords(bestChecked)
                    + ".";
        }
        return new ClassFileMatch(problem, best, ", "
                + describeWhere(best, folder, configured)
                + ", matches these uploads. " + holds);
    }

    // Counts the questions whose starter file in a folder declares a
    // method most uploads declare. Adds each question with a starter file
    // to checked, and the starter's methods no upload declares to
    // unknownNames.
    private int countFittingStarters(Path folder, List<String> checked,
            List<String> unknownNames) {
        int fitting = 0;
        for (Question question : assessment.getQuestions()) {
            String id = question.getId();
            Path file = folder.resolve(question.getFolderName())
                    .resolve(question.getFileName());
            String source = readSmallFile(file);
            if (countFilesRead(id) > 0 && source != null) {
                checked.add(id);
                List<String> declared = findStaticMethods(tokenize(source));
                if (sharesCommonMethod(id, declared)) {
                    fitting++;
                } else {
                    addUnknownNames(id, declared, unknownNames);
                }
            }
        }
        return fitting;
    }

    // Says why the starter files seem to be for another lab test.
    private String describeOtherStarter(List<String> unknownNames) {
        if (unknownNames.isEmpty()) {
            return OTHER_LAB_STARTER + " They share no methods with the "
                    + "uploads.";
        }
        return OTHER_LAB_STARTER + " They have "
                + joinWords(firstNames(unknownNames))
                + ", which none of the uploads have.";
    }

    // --------------------------------------------------------- uploads

    // Learns, from up to MAX_UPLOADS_READ uploads, which methods each
    // question's file declares.
    private void readUploads(List<Path> uploads) {
        uploadMethods.clear();
        filesRead.clear();
        for (Path upload : listStudentUploads(uploads)) {
            Map<String, String> sources = readQuestionSources(upload);
            for (Question question : assessment.getQuestions()) {
                String source = sources.get(question.getId());
                if (source != null) {
                    countMethods(question.getId(),
                            findStaticMethods(tokenize(source)));
                }
            }
        }
    }

    // Adds one upload's methods for a question to the counts.
    private void countMethods(String questionId, List<String> methods) {
        filesRead.put(questionId, countFilesRead(questionId) + 1);
        Map<String, Integer> counts = uploadMethods.get(questionId);
        if (counts == null) {
            counts = new HashMap<>();
            uploadMethods.put(questionId, counts);
        }
        for (String method : new HashSet<>(methods)) {
            Integer count = counts.get(method);
            if (count == null) {
                count = 0;
            }
            counts.put(method, count + 1);
        }
    }

    // Returns how many uploads had a question's file.
    private int countFilesRead(String questionId) {
        Integer count = filesRead.get(questionId);
        if (count == null) {
            return 0;
        }
        return count;
    }

    // Returns how many uploads declare a method in a question's file.
    private int countUploadsWith(String questionId, String method) {
        Map<String, Integer> counts = uploadMethods.get(questionId);
        if (counts == null || !counts.containsKey(method)) {
            return 0;
        }
        return counts.get(method);
    }

    // Says whether any of the names is declared by at least half of the
    // uploads that had the question's file.
    private boolean sharesCommonMethod(String questionId,
            List<String> names) {
        for (String name : names) {
            if (countUploadsWith(questionId, name) * HALF
                    >= countFilesRead(questionId)) {
                return true;
            }
        }
        return false;
    }

    // Adds the names no upload declares for a question, once each.
    private void addUnknownNames(String questionId, List<String> names,
            List<String> unknownNames) {
        for (String name : names) {
            if (countUploadsWith(questionId, name) == 0
                    && !unknownNames.contains(name)) {
                unknownNames.add(name);
            }
        }
    }

    // Turns the chosen paths into one path per student: a zip, or a
    // folder unzipped by hand. A folder of zips gives its zips.
    private List<Path> listStudentUploads(List<Path> uploads) {
        List<Path> students = new ArrayList<>();
        for (Path upload : uploads) {
            if (isStudentUpload(upload)) {
                addUpTo(students, upload);
            } else if (Files.isDirectory(upload, LinkOption.NOFOLLOW_LINKS)) {
                for (Path child : listVisibleChildren(upload)) {
                    if (isStudentUpload(child)) {
                        addUpTo(students, child);
                    }
                }
            }
        }
        return students;
    }

    // Adds a path unless MAX_UPLOADS_READ paths are already there.
    private void addUpTo(List<Path> students, Path upload) {
        if (students.size() < MAX_UPLOADS_READ) {
            students.add(upload);
        }
    }

    // Says whether a path is one student's upload: a zip file, or a
    // folder that holds a question folder, directly or inside its only
    // sub-folder.
    private boolean isStudentUpload(Path path) {
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return hasEnding(path, ZIP_EXTENSION);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (holdsQuestionFolder(path)) {
            return true;
        }
        List<Path> folders = new ArrayList<>();
        for (Path child : listVisibleChildren(path)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                folders.add(child);
            }
        }
        return folders.size() == 1 && holdsQuestionFolder(folders.get(0));
    }

    // Says whether a folder holds one of the question folders.
    private boolean holdsQuestionFolder(Path folder) {
        for (Question question : assessment.getQuestions()) {
            if (Files.isDirectory(folder.resolve(question.getFolderName()),
                    LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    // Reads the question files of one upload, by question id. An upload
    // that cannot be read gives none.
    private Map<String, String> readQuestionSources(Path upload) {
        Map<String, String> sources = new HashMap<>();
        try {
            if (Files.isDirectory(upload, LinkOption.NOFOLLOW_LINKS)) {
                readFolderSources(upload, 0, sources);
            } else {
                readZipSources(upload, sources);
            }
        } catch (IOException exception) {
            // A damaged upload adds no methods; grading reports it
        }
        return sources;
    }

    // Reads the question files inside a zip, without unzipping it.
    private void readZipSources(Path zipFile, Map<String, String> sources)
            throws IOException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile.toFile());
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Question question = findQuestion(lastPart(entry.getName()));
                if (!entry.isDirectory() && question != null
                        && !isIgnoredEntry(entry.getName())
                        && !sources.containsKey(question.getId())) {
                    sources.put(question.getId(), readEntry(zip, entry));
                }
            }
        } finally {
            FileUtils.closeIfOpen(zip);
        }
    }

    // Reads up to MAX_SOURCE_BYTES of one zip entry as text.
    private String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        InputStream input = null;
        try {
            input = zip.getInputStream(entry);
            return new String(input.readNBytes(MAX_SOURCE_BYTES),
                    StandardCharsets.UTF_8);
        } finally {
            FileUtils.closeIfOpen(input);
        }
    }

    // Reads the question files in an unzipped upload, down to
    // MAX_FOLDER_DEPTH folders.
    private void readFolderSources(Path folder, int depth,
            Map<String, String> sources) {
        for (Path child : listVisibleChildren(folder)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                if (depth < MAX_FOLDER_DEPTH) {
                    readFolderSources(child, depth + 1, sources);
                }
            } else {
                Question question = findQuestion(child.getFileName()
                        .toString());
                String source = readSmallFile(child);
                if (question != null && source != null
                        && !sources.containsKey(question.getId())) {
                    sources.put(question.getId(), source);
                }
            }
        }
    }

    // Returns the question whose file has this name, in any case, or
    // null.
    private Question findQuestion(String fileName) {
        for (Question question : assessment.getQuestions()) {
            if (question.getFileName().equalsIgnoreCase(fileName)) {
                return question;
            }
        }
        return null;
    }

    // Says whether a zip entry is macOS metadata or a hidden file.
    private boolean isIgnoredEntry(String entryName) {
        return entryName.contains(MAC_FOLDER + ENTRY_SEPARATOR)
                || lastPart(entryName).startsWith(HIDDEN_PREFIX);
    }

    // Returns the part of a zip entry name after its last folder.
    private String lastPart(String entryName) {
        int separator = entryName.lastIndexOf(ENTRY_SEPARATOR);
        return entryName.substring(separator + 1);
    }

    // ------------------------------------------------------ java source

    // Splits Java source into names and single symbols, leaving out
    // comments, strings and characters.
    private List<String> tokenize(String source) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            char next = ' ';
            if (index + 1 < length) {
                next = source.charAt(index + 1);
            }
            if (current == '/' && next == '/') {
                index = skipPast(source, index, "\n");
            } else if (current == '/' && next == '*') {
                index = skipPast(source, index + 2, "*/");
            } else if (current == '"' || current == '\'') {
                index = skipQuoted(source, index);
            } else if (Character.isJavaIdentifierStart(current)) {
                int end = index + 1;
                while (end < length
                        && Character.isJavaIdentifierPart(
                        source.charAt(end))) {
                    end++;
                }
                tokens.add(source.substring(index, end));
                index = end;
            } else {
                if (!Character.isWhitespace(current)) {
                    tokens.add(String.valueOf(current));
                }
                index++;
            }
        }
        return tokens;
    }

    // Returns the index just after the next end text, or the length.
    private int skipPast(String source, int from, String end) {
        int found = source.indexOf(end, from);
        if (found < 0) {
            return source.length();
        }
        return found + end.length();
    }

    // Returns the index just after a quoted string or character.
    private int skipQuoted(String source, int start) {
        char quote = source.charAt(start);
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\\') {
                index = index + 2;
            } else if (current == quote || current == '\n') {
                return index + 1;
            } else {
                index++;
            }
        }
        return source.length();
    }

    // Lists the static methods a class declares, except main: the
    // methods a question asks for, such as getIsogramWords.
    private List<String> findStaticMethods(List<String> tokens) {
        List<String> methods = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).equals(STATIC_KEYWORD)) {
                String name = findDeclaredName(tokens, index + 1);
                if (name != null && !name.equals(MAIN_METHOD)
                        && seen.add(name)) {
                    methods.add(name);
                }
            }
        }
        return methods;
    }

    // Returns the method name declared after "static", or null when
    // "static" starts a field, a block or a nested class instead. It
    // looks at no more than MAX_DECLARATION_TOKENS tokens and stops at
    // the next "static", so the whole file is read in one pass.
    private String findDeclaredName(List<String> tokens, int start) {
        int stop = Math.min(tokens.size(), start + MAX_DECLARATION_TOKENS);
        for (int index = start; index < stop; index++) {
            String token = tokens.get(index);
            if (token.equals(STATIC_KEYWORD)) {
                return null;
            }
            if (token.equals("(")) {
                String name = tokens.get(index - 1);
                if (index > start && isName(name)) {
                    return name;
                }
                return null;
            }
            if (token.equals("=") || token.equals(";") || token.equals("{")
                    || token.equals(")")) {
                return null;
            }
        }
        return null;
    }

    // Lists the methods a tester calls that could be the student's:
    // calls with no object in front, or with the student's class name in
    // front, such as Q1a.getIsogramWords(...).
    private List<String> findCalls(List<String> tokens, String className) {
        List<String> calls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index + 1 < tokens.size(); index++) {
            String name = tokens.get(index);
            String before = "";
            if (index > 0) {
                before = tokens.get(index - 1);
            }
            boolean onClass = before.equals(".") && index > 1
                    && tokens.get(index - 2).equals(className);
            if (tokens.get(index + 1).equals("(") && isName(name)
                    && !isNotCall(name) && !name.equals(MAIN_METHOD)
                    && !before.equals("new")
                    && (!before.equals(".") || onClass)
                    && seen.add(name)) {
                calls.add(name);
            }
        }
        return calls;
    }

    // Says whether a token is a Java name.
    private boolean isName(String token) {
        return !token.isEmpty()
                && Character.isJavaIdentifierStart(token.charAt(0));
    }

    // Says whether a word before "(" is a keyword, not a method.
    private boolean isNotCall(String word) {
        for (String keyword : NOT_CALLS) {
            if (keyword.equals(word)) {
                return true;
            }
        }
        return false;
    }

    // Returns a question's class name: its file name without ".java".
    private String classNameOf(Question question) {
        String fileName = question.getFileName();
        if (fileName.endsWith(JAVA_EXTENSION)) {
            return fileName.substring(0,
                    fileName.length() - JAVA_EXTENSION.length());
        }
        return fileName;
    }

    // ---------------------------------------------------- suggestions

    // Lists the CSV files that may be the right score sheet: in the
    // chosen sheet's folder, the folder above it, the uploads' folders
    // and the folders above them, then the one in Settings.
    private List<Path> listCandidateFiles(Path chosen, List<Path> uploads,
            Path configured) {
        List<Path> candidates = new ArrayList<>();
        for (Path folder : listSearchFolders(chosen, uploads, true)) {
            try {
                for (Path file : FileUtils.listFiles(folder, CSV_EXTENSION)) {
                    addCandidate(candidates, file, chosen);
                }
            } catch (IOException exception) {
                // A folder that cannot be read offers no file
            }
        }
        if (configured != null && Files.isRegularFile(configured)) {
            addCandidate(candidates, configured, chosen);
        }
        return candidates;
    }

    // Lists the folders that may be the right tester or starter folder:
    // the folders beside the chosen one and beside its folder, the
    // folders beside the uploads' folders, then the one in Settings. The
    // uploads' own folders are left out, as they hold students' work.
    private List<Path> listCandidateFolders(Path chosen, List<Path> uploads,
            Path configured) {
        List<Path> candidates = new ArrayList<>();
        Set<Path> uploadPaths = new HashSet<>();
        for (Path upload : uploads) {
            uploadPaths.add(normalise(upload));
        }
        for (Path folder : listSearchFolders(chosen, uploads, false)) {
            for (Path child : listVisibleChildren(folder)) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        && !uploadPaths.contains(normalise(child))) {
                    addCandidate(candidates, child, chosen);
                }
            }
        }
        if (configured != null && Files.isDirectory(configured)) {
            addCandidate(candidates, configured, chosen);
        }
        return candidates;
    }

    // Lists the folders to look in, nearest first and each once: the
    // chosen file's folder and the one above it, then for each upload
    // the folder above it. withUploadFolders also adds the folder that
    // holds each upload, where a score sheet is often kept.
    private List<Path> listSearchFolders(Path chosen, List<Path> uploads,
            boolean withUploadFolders) {
        List<Path> folders = new ArrayList<>();
        Path parent = parentOf(chosen);
        addFolder(folders, parent);
        addFolder(folders, parentOf(parent));
        for (Path upload : uploads) {
            Path holder = parentOf(upload);
            if (withUploadFolders) {
                if (Files.isDirectory(upload, LinkOption.NOFOLLOW_LINKS)) {
                    addFolder(folders, upload);
                }
                addFolder(folders, holder);
            }
            addFolder(folders, parentOf(holder));
        }
        return folders;
    }

    // Adds a folder to look in, unless it is missing or already listed.
    private void addFolder(List<Path> folders, Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        Path normalised = normalise(folder);
        if (!folders.contains(normalised)) {
            folders.add(normalised);
        }
    }

    // Adds a candidate unless it is the chosen one, already listed, or
    // MAX_CANDIDATES are listed.
    private void addCandidate(List<Path> candidates, Path candidate,
            Path chosen) {
        Path normalised = normalise(candidate);
        if (candidates.size() < MAX_CANDIDATES
                && !normalised.equals(normalise(chosen))
                && !candidates.contains(normalised)) {
            candidates.add(normalised);
        }
    }

    // Says where a suggestion is, from the chosen file's point of view.
    private String describeWhere(Path suggestion, Path chosen,
            Path configured) {
        Path folder = parentOf(suggestion);
        if (folder != null && folder.equals(parentOf(normalise(chosen)))) {
            return "in the same folder";
        }
        if (configured != null
                && suggestion.equals(normalise(configured))) {
            return "the one in Settings";
        }
        if (folder == null || folder.getFileName() == null) {
            return "nearby";
        }
        return "in " + folder.getFileName();
    }

    // Returns the path set for a key in config.properties, or null.
    private Path readConfiguredPath(String key) {
        String value = config.getString(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return normalise(Paths.get(value.trim()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // --------------------------------------------------------- helpers

    // Lists a folder's children, without hidden files and macOS folders,
    // sorted by name. A folder that cannot be read has none.
    private List<Path> listVisibleChildren(Path folder) {
        List<Path> children = new ArrayList<>();
        try {
            addVisibleChildren(folder, children);
        } catch (IOException exception) {
            return new ArrayList<>();
        }
        Collections.sort(children);
        return children;
    }

    // Adds a folder's children, without hidden files and macOS folders.
    private void addVisibleChildren(Path folder, List<Path> children)
            throws IOException {
        DirectoryStream<Path> entries = null;
        try {
            entries = Files.newDirectoryStream(folder);
            for (Path child : entries) {
                String name = child.getFileName().toString();
                if (!name.startsWith(HIDDEN_PREFIX)
                        && !name.equals(MAC_FOLDER)) {
                    children.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(entries);
        }
    }

    // Reads a file of at most MAX_SOURCE_BYTES as text, or returns null
    // when it is missing, too big or cannot be read.
    private String readSmallFile(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > MAX_SOURCE_BYTES) {
                return null;
            }
            return new String(Files.readAllBytes(file),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    // Says whether a file name ends with an ending, in any case.
    private boolean hasEnding(Path path, String ending) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(ending);
    }

    // Returns the folder that holds a path, or null.
    private Path parentOf(Path path) {
        if (path == null) {
            return null;
        }
        return normalise(path).getParent();
    }

    // Returns a path made absolute and without "." or "..".
    private Path normalise(Path path) {
        return path.toAbsolutePath().normalize();
    }

    // Lists the question ids, such as Q1a.
    private List<String> listQuestionIds() {
        List<String> ids = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            ids.add(question.getId());
        }
        return ids;
    }

    // Returns the first MAX_NAMES_SHOWN names.
    private List<String> firstNames(List<String> names) {
        List<String> first = new ArrayList<>();
        for (String name : names) {
            if (first.size() < MAX_NAMES_SHOWN) {
                first.add(name);
            }
        }
        return first;
    }

    // Joins words as "A", "A and B" or "A, B and C".
    private String joinWords(List<String> words) {
        return joinWords(words, " and ");
    }

    // Joins words with commas and a last joining word, such as " or ".
    private String joinWords(List<String> words, String last) {
        String joined = "";
        for (int index = 0; index < words.size(); index++) {
            if (index > 0 && index == words.size() - 1) {
                joined = joined + last;
            } else if (index > 0) {
                joined = joined + ", ";
            }
            joined = joined + words.get(index);
        }
        return joined;
    }
}
