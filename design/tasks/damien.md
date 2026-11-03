# Damien: tasks for 3 November 2026

Your area: `FileUtils`, `TextUtils`, `ingest` (`ZipExtractor`,
`SubmissionLoader`, `WorkingCopyManager`, `RosterReader`) and
`ExtractStage`.

These came out of a full test of `main` (421d2f6) on 3 November: the 17
student e2e cohort, 21 odd uploads, 14 cheating attempts and a 200
student batch. Reading uploads is solid: zip slip, zip bombs,
duplicates, `(1)` copies, `.ZIP`, missing prefixes, unzipped folders,
backslash paths, double wrappers and Excel rosters all work. The items
below are the uploads that still go wrong. Do the P1 items first.

## Before you start

- Branch from `main` once the pull request from `jingyuan/taught-java`
  is merged. Until then, branch from `jingyuan/taught-java`. Line numbers
  below are from that branch (commit 957ec97); if a line moved, search
  for the method name.
- That branch rewrote parts of `ZipExtractor`, `SubmissionLoader` and
  `ExtractStage` in taught Java (no try-with-resources, no multi-catch).
  The behaviour is the same as `main`.
- Project rules (the local rules file is not in git, so here they are):
  - Only Java taught in CS102: no ternary, nested or anonymous classes,
    try-with-resources, multi-catch, varargs parameters, `StringBuilder`,
    `String.join`, `List.of`, streams, method references, or lambdas
    outside `ui`. The README section "Java taught in CS102" shows the
    taught way for each.
  - 80 columns, 4 spaces, braces on every block, no magic numbers, a
    header comment on every file, Javadoc with its MUST/SHOULD/COULD tier
    on every public type and method.
  - No em dashes and no emojis. No co-author or attribution lines in
    commits.
  - Originals are never changed: everything happens in the working copy.
- Check before you push, from the GradeZZY folder: `sh compile.sh core`,
  then `sh compile.sh` (no error or warning), `sh run.sh --check`,
  `sh run.sh --selftest`, and `sh run.sh console G2`. The console must
  still print 12.0, 10.0, 10.0, 0.0, 20.0, 1.0.
- Test files: `samples/` is not in git. Ask Jing Yuan for
  `samples/e2e-cohort` (hand-worked marks in its README, column
  "Correct") and `samples/e2e-weird` (one folder per odd upload, each
  with `subs/`, `testers/` and `roster.csv`). Every cohort mark must
  stay as in that column.

## P1: uploads that lose all marks

### 1. A zip inside a zip is graded as one student with 0

- Where: `SubmissionLoader` and `ZipExtractor.unpack` (line 211) treat an
  inner `.zip` as an ordinary file.
- What happens: two real cases.
  - `samples/e2e-weird/w01-batch-zip`: the instructor uploads the bulk
    download (`G3-all-submissions.zip`, holding every student's zip). It
    is graded as one student called `G3-all-submissions`, with 0/20 and
    "not on the roster".
  - A student zips their zip (`2024-2025-rachel.tan.2024.zip` holding
    `2024-2025-rachel.tan.2024.zip`). They get 0/20 with "The ZIP file
    holds no folder".
- Fix:
  - When a chosen zip holds only `.zip` files whose names match the
    student zip pattern, treat it as a folder of submissions and load
    each inner zip as a student.
  - When a student's zip holds nothing but one zip, unpack that one. Go
    one level deep only, and keep the size and entry limits.
- Done when: w01 loads every student inside it, and the zip-in-a-zip
  student gets their real marks.

### 2. Provided `.class` files are taken from the zip, not from the starter

- Where: `WorkingCopyManager.removeClassFiles` (line 186) keeps any file
  named in `assessment.provided.class.files` (`DataException.class`,
  `Shape.class`, `Circle.class`, `Rectangle.class`) exactly as the
  student sent it.
- What happens: a zip without `DataException.class` gets a compile error
  on Q2b (15/20 instead of 20/20). A student who edits `Shape.class` is
  trusted.
- Fix: after removing the student's `.class` files, copy each provided
  class file from the starter folder into the matching question folder,
  replacing any copy that is there. The starter folder is
  `paths.starter.dir`, or the batch's folder if `ExtractStage` is given
  one.
- Done when: a zip with no `DataException.class`, or with an edited one,
  gets the same marks as the model answer.

### 3. One odd entry rejects the whole zip

- Where: `ZipExtractor.unpack`, lines 228 to 257. `isSafeEntry` (line
  239) runs before `isIgnored` (line 244), and `extractFile` opens files
  with `CREATE_NEW` (line 276).
- What happens: each of these turns the submission into "Grading stopped
  early" with 0 marks:
  - the macOS `Icon\r` file, or any name with `?` in it, even inside
    `__MACOSX/`;
  - two entries whose names differ only in case (`Q1/` and `q1/`), or a
    duplicate entry (`CREATE_NEW` fails, and macOS ignores case);
  - file names saved by Windows Explorer that are not UTF-8
    (`invalid CEN header`).
- Fix:
  - Check the ignored names (`ingest.ignored.file.names`) and hidden
    `._` files before the safety check.
  - For an unsafe name outside the question folders, skip it and add a
    warning instead of failing.
  - Name the clashing entry in the message.
  - On a `ZipException`, open the file again with
    `new ZipFile(file, Charset.forName("CP437"))`.
- Done when: a zip with an `Icon\r` file, and one made by Windows
  Explorer with a non-English file name, both grade normally.

## P2: clearer results

### 4. A damaged, unsafe or oversized zip is reported as GradeZZY's own failure

- Where: `ExtractStage.process` (line 93) wraps the `IngestException` in
  a `GradingException`. The pipeline then records `INTERNAL_ERROR`
  "Grading stopped early: Could not prepare ...: Could not create the
  working copy ...: Could not unpack /full/path: zip END header not
  found Check the ZIP file". A full stop is missing before "Check".
- Fix: add an `INVALID_ARCHIVE` problem (the type exists, and
  `SubmissionLoader` line 604 already uses it) with a plain message:
  "This zip file is damaged. Ask the student for a new copy." Use a
  similar message for the unsafe and too-big cases. Then stop the
  submission as now.
- Done when: `w04-zip-slip`, `w05-zip-bomb` and the cohort's
  `priya.nair.2024` show `INVALID_ARCHIVE` with a one-line message and
  no file paths.

### 5. Score sheets saved by Excel on Windows, and short rows

- Where: `FileUtils` (line 91) reads UTF-8 only. `RosterReader` (line 83)
  throws "Wrong column count in roster record 2".
- What happens:
  - A score sheet saved as "CSV" by Excel in Windows-1252, with a name
    such as José, fails with "Input length = 4".
  - `samples/e2e-weird/w13-roster-no-hash` has no `#` prefix and no
    End-of-Line column, and is refused completely.
- Fix:
  - On `MalformedInputException`, read the file as `windows-1252`. Tell
    Huxley, so the CSV export writes in the same encoding.
  - Accept a row that is short only by the trailing End-of-Line column,
    or name the missing column in the message.
- Done when: both files load.

## P3: small

- For duplicate downloads, the lowest copy number wins
  (`SubmissionLoader`, around line 106), which is the oldest file. A
  student who uploads a fixed version as `name (1).zip` gets the old one
  graded. Prefer the newest file (highest copy number, or the latest
  modified time), and keep the duplicate warning.
