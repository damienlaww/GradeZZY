/*
 * File: JdkCommand.java
 *
 * Purpose: Turns the settings exec.java.command and exec.javac.command
 * into the program to start.
 *
 * Where it fits: Used by TesterRunner and SourceCompiler, so testers and
 * javac run on the JDK that runs GradeZZY, not on whatever java comes
 * first on the PATH.
 */
package gradezzy.exec;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns a java or javac setting into the program to start. The bare word
 * "java" or "javac" means the program of the JDK running GradeZZY: an
 * older java first on the PATH would reject the options the testers are
 * run with, and every student would get 0. Any other value, such as a
 * full path, is used as it is.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public final class JdkCommand {

    /**
     * The setting value that means the running JDK's java.
     */
    public static final String JAVA = "java";

    /**
     * The setting value that means the running JDK's javac.
     */
    public static final String JAVAC = "javac";

    // The system property holding the folder of the running JDK
    private static final String JAVA_HOME_PROPERTY = "java.home";

    // The folder inside the JDK that holds its programs
    private static final String BIN_FOLDER = "bin";

    // The ending of a program on Windows
    private static final String WINDOWS_EXTENSION = ".exe";

    // Utility class: never created
    private JdkCommand() {
    }

    /**
     * Returns the program to start for a java or javac setting.
     *
     * @param setting the value of exec.java.command or exec.javac.command
     * @return the full path of the running JDK's program for the bare
     *         word java or javac when that program exists, otherwise the
     *         setting itself
     */
    public static String resolve(String setting) {
        boolean bareWord = JAVA.equals(setting) || JAVAC.equals(setting);
        String javaHome = System.getProperty(JAVA_HOME_PROPERTY);
        if (!bareWord || javaHome == null) {
            return setting;
        }

        Path bin = Path.of(javaHome, BIN_FOLDER);
        // Windows names the program java.exe; ProcessBuilder is given the
        // whole name so it never has to guess the ending
        Path windowsProgram = bin.resolve(setting + WINDOWS_EXTENSION);
        if (Files.isRegularFile(windowsProgram)) {
            return windowsProgram.toString();
        }
        Path program = bin.resolve(setting);
        if (Files.isRegularFile(program)) {
            return program.toString();
        }
        return setting;
    }
}
