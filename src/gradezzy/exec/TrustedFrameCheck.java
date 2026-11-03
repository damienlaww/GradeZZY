/*
 * File: TrustedFrameCheck.java
 *
 * Purpose: Checks the frames of a call stack, one at a time, and remembers
 * whether every one of them belongs to Java, the harness or the tester.
 *
 * Where it fits: TrustedPrintStream hands one of these to a StackWalker
 * each time it must decide whether a line was printed by the tester.
 */
package gradezzy.exec;

import java.util.function.Consumer;

/**
 * Checks the frames of a call stack, one at a time. Once one frame is
 * found that is not trusted, the rest are skipped and the answer stays
 * false.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
class TrustedFrameCheck implements Consumer<StackWalker.StackFrame> {

    // The tester class; its own code is trusted
    private final Class<?> tester;

    // False as soon as one frame is not trusted
    private boolean allTrusted = true;

    /**
     * Creates a check for one call stack.
     *
     * @param tester the tester class
     */
    TrustedFrameCheck(Class<?> tester) {
        this.tester = tester;
    }

    /**
     * Checks the class of one frame. Called by the StackWalker for each
     * frame, from the top of the stack down.
     *
     * @param frame one frame of the call stack
     */
    @Override
    public void accept(StackWalker.StackFrame frame) {
        if (!allTrusted) {
            return;
        }
        if (!isTrustedClass(frame.getDeclaringClass())) {
            allTrusted = false;
        }
    }

    /**
     * Says whether every frame checked so far was trusted.
     *
     * @return true if no untrusted frame was found
     */
    boolean isAllTrusted() {
        return allTrusted;
    }

    /**
     * Says whether a class is part of Java, this harness or the tester.
     * Classes nested in the tester count as the tester; student classes
     * and classes from any other class loader do not. The harness's own
     * classes are named one by one.
     *
     * @param type the class of one frame in the call stack
     * @return true if the class is trusted
     */
    private boolean isTrustedClass(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null
                || loader == ClassLoader.getPlatformClassLoader()) {
            return true;
        }
        Class<?> host = type.getNestHost();
        return loader == TesterHarness.class.getClassLoader()
                && (host == tester || isHarnessClass(type));
    }

    /**
     * Says whether a class is one of the harness's own classes.
     *
     * @param type the class
     * @return true for TesterHarness, TrustedPrintStream and this class
     */
    private boolean isHarnessClass(Class<?> type) {
        return type == TesterHarness.class
                || type == TrustedPrintStream.class
                || type == TrustedFrameCheck.class;
    }
}
