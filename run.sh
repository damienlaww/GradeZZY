#!/bin/sh
# ----------------------------------------------------------------------
# run.sh: runs GradeZZY. Run compile.sh first.
#
# Usage (macOS Terminal, or Git Bash on Windows):
#   sh run.sh               open the window
#   sh run.sh --check       load every screen once, then exit (no window)
#   sh run.sh console       grade from the terminal, without JavaFX
#   sh run.sh console G2    the same, for section G2
# ----------------------------------------------------------------------

cd "$(dirname "$0")" || exit 1

# Pick the JavaFX jars and the classpath separator for this computer.
case "$(uname -s)" in
    Darwin)
        SEPARATOR=":"
        if [ "$(uname -m)" = "arm64" ]; then
            JAVAFX_DIR="lib/javafx/mac-aarch64"
        else
            JAVAFX_DIR="lib/javafx/mac"
        fi
        ;;
    MINGW*|MSYS*|CYGWIN*)
        SEPARATOR=";"
        JAVAFX_DIR="lib/javafx/win"
        ;;
    *)
        echo "Unsupported system: $(uname -s). Use Windows or macOS."
        exit 1
        ;;
esac

if [ ! -d classes/gradezzy ]; then
    echo "Nothing is compiled yet. Run: sh compile.sh"
    exit 1
fi

CLASSPATH_VALUE="classes${SEPARATOR}lib/*"

if [ "$1" = "console" ]; then
    shift
    # --enable-native-access: the SQLite driver loads a native library.
    exec java -cp "$CLASSPATH_VALUE" \
        --enable-native-access=ALL-UNNAMED \
        gradezzy.app.ConsoleMain "$@"
fi

# --module-path / --add-modules: JavaFX is not part of the JDK.
# --enable-native-access: JavaFX and the SQLite driver load native code.
# --add-exports / --add-opens: the complete list ControlsFX documents for
#   its controls (AutoCompletionBinding, BreadCrumbBar, NotificationPane,
#   GlyphFont, GridView, FilterPanel, TableRowExpanderColumn). ControlsFX
#   is on the classpath, so the target is ALL-UNNAMED.
exec java \
    --module-path "$JAVAFX_DIR" \
    --add-modules javafx.controls,javafx.fxml \
    --enable-native-access=javafx.graphics,ALL-UNNAMED \
    --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED \
    --add-exports=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED \
    --add-exports=javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED \
    --add-exports=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED \
    --add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED \
    -cp "$CLASSPATH_VALUE" \
    gradezzy.ui.MainApp "$@"
