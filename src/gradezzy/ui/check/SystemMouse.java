/*
 * File: SystemMouse.java
 *
 * Purpose: Drags the real mouse between two windows.
 *
 * Where it fits: Used by RobotDriver. A drag between windows holds the
 * JavaFX thread, which the JavaFX robot needs, so it cannot release the
 * button; this robot may be called from the test's own thread.
 *
 * Note on untaught Java: java.awt.Robot sends operating-system mouse
 * events, and java.awt.event.InputEvent names the mouse button.
 */
package gradezzy.ui.check;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 * Drags the mouse with the operating system's robot.
 */
public final class SystemMouse {

    // The steps of a drag
    private static final int DRAG_STEPS = 12;

    // The pause between two steps of a drag
    private static final int STEP_MILLIS = 100;

    // The pause over the target before the button is released
    private static final int HOVER_MILLIS = 350;

    // The operating system's robot
    private final Robot hand;

    /**
     * Creates the mouse.
     *
     * @throws AWTException if the system does not allow a robot
     */
    public SystemMouse() throws AWTException {
        hand = new Robot();
    }

    /**
     * Drags with the left button from one point to another, in steps.
     *
     * @param start the screen point pressed, as x and y
     * @param end the screen point released on, as x and y
     */
    public void drag(double[] start, double[] end) {
        hand.mouseMove((int) start[0], (int) start[1]);
        hand.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int step = 1; step <= DRAG_STEPS; step++) {
            double share = (double) step / DRAG_STEPS;
            int pointX = (int) (start[0] + (end[0] - start[0]) * share);
            int pointY = (int) (start[1] + (end[1] - start[1]) * share);
            hand.mouseMove(pointX, pointY);
            hand.delay(STEP_MILLIS);
        }
        hand.delay(HOVER_MILLIS);
        hand.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
}
