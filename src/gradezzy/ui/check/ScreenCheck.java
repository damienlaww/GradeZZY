/*
 * File: ScreenCheck.java
 *
 * Purpose: Loads every screen in both modes and both themes, without a
 * window, and reports any that fails.
 *
 * Where it fits: Run by "sh run.sh --check" after a change.
 */
package gradezzy.ui.check;

import java.io.IOException;

import javafx.scene.Parent;
import javafx.scene.Scene;

import gradezzy.service.ServiceRegistry;
import gradezzy.ui.Screen;
import gradezzy.ui.ShellBuilder;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.demo.DemoBackend;

/**
 * Checks that every screen loads.
 */
public class ScreenCheck {

    // The window size the screens are laid out in
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;

    // Every service
    private final ServiceRegistry services;

    /**
     * Creates a screen check.
     *
     * @param services every service
     */
    public ScreenCheck(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Loads every screen in both modes and both themes.
     *
     * @return 0 when everything loaded, 1 otherwise
     */
    public int run() {
        int problems = check(new LiveBackend(services), "live");
        problems += check(new DemoBackend(services), "demo");
        problems += new FlowCheck(services, null).run();

        if (problems == 0) {
            System.out.println("All " + Screen.values().length
                    + " screens loaded in live and demo mode, with both "
                    + "themes.");
            return 0;
        }
        System.out.println(problems + " problem(s) found. See above.");
        return 1;
    }

    // Loads and shows every screen with one backend.
    private int check(DashboardBackend backend, String mode) {
        UiState state = ShellBuilder.createState(services.getConfig());
        state.animationsEnabledProperty().set(false);
        ThemeManager themeManager = new ThemeManager();
        ShellBuilder builder = new ShellBuilder(services, backend, state,
                themeManager);

        try {
            builder.build(WIDTH, HEIGHT);
        } catch (IOException exception) {
            System.out.println("FAILED: main window (" + mode + "): "
                    + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            System.out.println("FAILED: main window (" + mode + "): "
                    + exception);
            return 1;
        }
        System.out.println("Loaded screen: " + Screen.MAIN + " (" + mode
                + ")");

        int problems = 0;
        for (Screen screen : Screen.values()) {
            if (screen != Screen.MAIN) {
                problems += checkScreen(builder, screen, mode);
            }
        }
        problems += checkThemes(builder, themeManager, mode);
        return problems;
    }

    // Loads one screen from its file, then shows it in the window.
    private int checkScreen(ShellBuilder builder, Screen screen,
            String mode) {
        try {
            builder.getNavigator().load(screen);
            builder.getController().navigate(screen);
            Parent root = builder.getScene().getRoot();
            root.applyCss();
            root.layout();
            System.out.println("Loaded screen: " + screen + " (" + mode
                    + ")");
            return 0;
        } catch (IOException exception) {
            System.out.println("FAILED: " + screen + " (" + mode + "): "
                    + exception.getMessage());
        } catch (RuntimeException exception) {
            System.out.println("FAILED: " + screen + " (" + mode + "): "
                    + exception);
            exception.printStackTrace(System.out);
        }
        return 1;
    }

    // Applies both themes to the window.
    private int checkThemes(ShellBuilder builder, ThemeManager manager,
            String mode) {
        Scene scene = builder.getScene();
        int problems = 0;
        boolean[] themes = {true, false};
        for (boolean dark : themes) {
            try {
                manager.applyTo(scene, dark);
                scene.getRoot().applyCss();
                System.out.println("Loaded stylesheets: "
                        + ThemeManager.themeName(dark) + " theme (" + mode
                        + ")");
            } catch (IOException exception) {
                System.out.println("FAILED: " + ThemeManager.themeName(dark)
                        + " theme (" + mode + "): "
                        + exception.getMessage());
                problems++;
            }
        }
        return problems;
    }
}
