#!/bin/sh
# ----------------------------------------------------------------------
# compile.sh: compiles every Java source file in src/ into classes/.
#
# Usage (macOS Terminal, or Git Bash on Windows):
#   sh compile.sh         compile the whole application
#   sh compile.sh core    compile only the grading core, without JavaFX
#
# javac does not copy resource files, so this script also copies the
# .css, font and logo files of the ui package into classes/.
#
# When you add a new package, add its folder to CORE_SOURCES below and
# to compile.bat.
# ----------------------------------------------------------------------

cd "$(dirname "$0")" || exit 1

# Pick the JavaFX jars that match this computer.
case "$(uname -s)" in
    Darwin)
        # Ask the JDK, not the shell: an Intel JDK on an Apple silicon Mac
        # (or a shell under Rosetta) needs the other JavaFX folder.
        JAVA_ARCH=$(java -XshowSettings:properties -version 2>&1 \
            | sed -n 's/^ *os.arch = //p')
        if [ "$JAVA_ARCH" = "aarch64" ]; then
            JAVAFX_DIR="lib/javafx/mac-aarch64"
        else
            JAVAFX_DIR="lib/javafx/mac"
        fi
        ;;
    MINGW*|MSYS*|CYGWIN*)
        JAVAFX_DIR="lib/javafx/win"
        ;;
    *)
        echo "Unsupported system: $(uname -s). Use Windows or macOS."
        exit 1
        ;;
esac

# Every package except ui. None of these may import javafx.
CORE_SOURCES="src/gradezzy/app/*.java
    src/gradezzy/config/*.java
    src/gradezzy/detection/*.java
    src/gradezzy/exec/*.java
    src/gradezzy/fix/*.java
    src/gradezzy/grading/*.java
    src/gradezzy/ingest/*.java
    src/gradezzy/model/*.java
    src/gradezzy/modelanswer/*.java
    src/gradezzy/persistence/*.java
    src/gradezzy/plagiarism/*.java
    src/gradezzy/report/*.java
    src/gradezzy/service/*.java
    src/gradezzy/stats/*.java
    src/gradezzy/testcase/*.java
    src/gradezzy/util/*.java
    src/gradezzy/validation/*.java"
UI_SOURCES="src/gradezzy/ui/*.java src/gradezzy/ui/backend/*.java
    src/gradezzy/ui/check/*.java src/gradezzy/ui/demo/*.java
    src/gradezzy/ui/flow/*.java"

# Start from an empty classes folder so no old class file is left behind.
rm -rf classes
mkdir -p classes

if [ "$1" = "core" ]; then
    # No JavaFX and no jar on the classpath: proves the core stands alone.
    javac --release 25 -encoding UTF-8 -d classes $CORE_SOURCES || exit 1
    echo "Compiled the grading core into classes/ (no JavaFX)."
    exit 0
fi

javac --release 25 -encoding UTF-8 -d classes \
    --module-path "$JAVAFX_DIR" \
    --add-modules javafx.controls,javafx.fxml \
    -cp "lib/*" \
    $CORE_SOURCES $UI_SOURCES || exit 1

# Copy the resources javac leaves behind.
mkdir -p classes/gradezzy/ui/css
cp src/gradezzy/ui/css/*.css classes/gradezzy/ui/css/ || exit 1
mkdir -p classes/gradezzy/ui/fonts
cp src/gradezzy/ui/fonts/*.ttf src/gradezzy/ui/fonts/OFL.txt \
    classes/gradezzy/ui/fonts/ || exit 1
mkdir -p classes/gradezzy/ui/images
cp src/gradezzy/ui/images/*.png classes/gradezzy/ui/images/ || exit 1

echo "Compiled GradeZZY into classes/ using $JAVAFX_DIR."
