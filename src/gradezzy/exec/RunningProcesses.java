/*
 * File: RunningProcesses.java
 *
 * Purpose: Keeps the programs GradeZZY started that are still running,
 * and stops them all when GradeZZY closes.
 *
 * Where it fits: ProcessRunner adds each program it starts and removes it
 * when it ends. Java runs this class as a shutdown hook when GradeZZY
 * exits, is closed or gets Ctrl+C, so no tester is left running.
 */
package gradezzy.exec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The programs started by any ProcessRunner that have not ended yet. The
 * grading threads add and remove programs at the same time, so every
 * method is synchronized.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class RunningProcesses implements Runnable {

    // The programs still running
    private final List<Process> processes = new ArrayList<>();

    /**
     * Remembers a program that was just started.
     *
     * @param process the program
     */
    public synchronized void add(Process process) {
        processes.add(process);
    }

    /**
     * Forgets a program that has ended or was stopped.
     *
     * @param process the program
     */
    public synchronized void remove(Process process) {
        processes.remove(process);
    }

    /**
     * Stops every program still running, and the programs each started.
     * Java calls this once, as GradeZZY shuts down.
     */
    @Override
    public synchronized void run() {
        for (Process process : processes) {
            Iterator<ProcessHandle> descendants =
                    process.descendants().iterator();
            while (descendants.hasNext()) {
                descendants.next().destroyForcibly();
            }
            process.destroyForcibly();
        }
    }
}
