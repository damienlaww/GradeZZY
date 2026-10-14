#!/bin/sh
# ----------------------------------------------------------------------
# clean.sh: empties classes/ and removes files created while grading.
# Run this before zipping the project for submission: the brief requires
# the classes folder to be empty.
# ----------------------------------------------------------------------

cd "$(dirname "$0")" || exit 1

rm -rf classes workspace
mkdir -p classes

echo "classes/ is empty and workspace/ is removed."
