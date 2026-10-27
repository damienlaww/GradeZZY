#!/bin/sh
# ----------------------------------------------------------------------
# make-dummies.sh: builds dummy submissions for the cases the six sample
# submissions do not cover, in samples/dummy-submissions/.
#
# Usage (macOS Terminal, or Git Bash on Windows), from the GradeZZY
# folder:
#   sh test/make-dummies.sh
#
# The dummies are built from the provided files in samples/, which are
# kept out of git because they are restricted and hold student names.
# For the same reason only this script is in git, not the zips it makes.
# It needs nothing but the JDK: the jar tool writes the zip files.
#
# Each dummy is the correct ping.lee.2023 submission with one thing
# broken, so any change in its result comes from that one thing:
#   dummy.noq2.2025       the Q2 folder is missing
#   dummy.noq1b.2025      Q1/Q1b.java is missing
#   dummy.lowercase.2025  Q1a.java is saved as q1a.java
#   dummy.infinite.2025   Q1a never returns: while (true) { }
#   dummy.double.2025     a username folder inside a username folder
#   dummy.starter.2025    the starter folder submitted untouched
#   dummy.empty.2025      a zip file with nothing in it
#   dummy.notzip.2025     a text file named .zip
# None of them may stop the batch.
# ----------------------------------------------------------------------

cd "$(dirname "$0")/.." || exit 1

SAMPLE_ZIP="samples/student-submission/2023-2024-ping.lee.2023.zip"
SAMPLE_USER="ping.lee.2023"
STARTER_DIR="samples/RenameToYourUsername"
OUTPUT_DIR="samples/dummy-submissions"
ZIP_PREFIX="2023-2024-"

if [ ! -f "$SAMPLE_ZIP" ] || [ ! -d "$STARTER_DIR" ]; then
    echo "Copy the provided samples into samples/ first (see README.md)."
    exit 1
fi

if ! command -v jar > /dev/null 2>&1; then
    echo "The jar tool was not found. Install JDK 25 and put it on the PATH."
    exit 1
fi

WORK_DIR="$(mktemp -d)" || exit 1
trap 'rm -rf "$WORK_DIR"' EXIT

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
OUTPUT_PATH="$(cd "$OUTPUT_DIR" && pwd)"

# Unpack the correct submission once; every dummy starts from a copy.
mkdir -p "$WORK_DIR/base"
SAMPLE_PATH="$(pwd)/$SAMPLE_ZIP"
(cd "$WORK_DIR/base" && jar xf "$SAMPLE_PATH") || exit 1
BASE="$WORK_DIR/base/$SAMPLE_USER"

# Copies the correct submission into a folder named after the dummy.
# Prints the path of the copy's question folders.
start_dummy() {
    mkdir -p "$WORK_DIR/$1"
    cp -R "$BASE" "$WORK_DIR/$1/$1"
    echo "$WORK_DIR/$1/$1"
}

# Zips everything inside the dummy's work folder.
finish_dummy() {
    jar --create --no-manifest --file "$OUTPUT_PATH/$ZIP_PREFIX$1.zip" \
        -C "$WORK_DIR/$1" . || exit 1
    echo "Built $ZIP_PREFIX$1.zip"
}

# The Q2 folder is missing.
NAME="dummy.noq2.2025"
DIR="$(start_dummy "$NAME")"
rm -rf "$DIR/Q2"
finish_dummy "$NAME"

# Q1/Q1b.java is missing, together with its compiled file.
NAME="dummy.noq1b.2025"
DIR="$(start_dummy "$NAME")"
rm -f "$DIR/Q1/Q1b.java" "$DIR/Q1/Q1b.class"
finish_dummy "$NAME"

# Q1a.java is saved as q1a.java. The rename goes through a temporary
# name because Windows and macOS ignore case in file names.
NAME="dummy.lowercase.2025"
DIR="$(start_dummy "$NAME")"
mv "$DIR/Q1/Q1a.java" "$DIR/Q1/renaming.tmp"
mv "$DIR/Q1/renaming.tmp" "$DIR/Q1/q1a.java"
rm -f "$DIR/Q1/Q1a.class"
finish_dummy "$NAME"

# Q1a never returns. The loop sits inside an if, because code after a
# bare while (true) { } is unreachable and would not compile; this way
# the tester compiles and then runs until the time limit stops it.
NAME="dummy.infinite.2025"
DIR="$(start_dummy "$NAME")"
awk '
    { print }
    /public static .*getIsogramWords\(/ && !inserted {
        print "        if (inputs != null) {"
        print "            while (true) { }"
        print "        }"
        inserted = 1
    }
' "$DIR/Q1/Q1a.java" > "$DIR/Q1/Q1a.java.tmp"
mv "$DIR/Q1/Q1a.java.tmp" "$DIR/Q1/Q1a.java"
rm -f "$DIR/Q1/Q1a.class"
finish_dummy "$NAME"

# A username folder inside a username folder.
NAME="dummy.double.2025"
mkdir -p "$WORK_DIR/$NAME/$NAME"
cp -R "$BASE" "$WORK_DIR/$NAME/$NAME/$NAME"
finish_dummy "$NAME"

# The starter folder submitted untouched, without its api documentation.
NAME="dummy.starter.2025"
mkdir -p "$WORK_DIR/$NAME"
cp -R "$STARTER_DIR" "$WORK_DIR/$NAME/"
rm -rf "$WORK_DIR/$NAME/RenameToYourUsername/Q3/api"
finish_dummy "$NAME"

# A zip file with nothing in it: only the 22-byte end record, which is
# its 4-byte signature followed by 18 zero bytes (9 on each line).
NAME="dummy.empty.2025"
EMPTY_ZIP="$OUTPUT_PATH/$ZIP_PREFIX$NAME.zip"
printf 'PK\005\006\000\000\000\000\000\000\000\000\000' > "$EMPTY_ZIP"
printf '\000\000\000\000\000\000\000\000\000' >> "$EMPTY_ZIP"
echo "Built $ZIP_PREFIX$NAME.zip"

# A text file named .zip.
NAME="dummy.notzip.2025"
echo "This is not a zip file." > "$OUTPUT_PATH/$ZIP_PREFIX$NAME.zip"
echo "Built $ZIP_PREFIX$NAME.zip"

echo "Done. Add $OUTPUT_DIR on the Submissions screen to grade them."
