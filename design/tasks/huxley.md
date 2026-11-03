# Huxley: tasks for 3 November 2026

Your area: `report`, `stats`, `persistence` and `PersistStage`.

These came out of a full test of `main` (421d2f6) on 3 November. The CSV
export keeps the provided format byte for byte: CRLF, the `#` prefixes,
a blank numerator for students with no submission, and `(1)` copies
matched to the roster. The items below are where saved results or
exports can still be lost or wrong. Do the P1 items first.

## Before you start

- Branch from `main` once the pull request from `jingyuan/taught-java`
  is merged. Until then, branch from `jingyuan/taught-java`. Line numbers
  below are from that branch (commit 957ec97); if a line moved, search
  for the method name.
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
  - Only `SqliteResultRepository` may contain SQL.
- Check before you push, from the GradeZZY folder: `sh compile.sh core`,
  then `sh compile.sh` (no error or warning), `sh run.sh --check`,
  `sh run.sh --selftest`, and `sh run.sh console G2`. The console must
  still print 12.0, 10.0, 10.0, 0.0, 20.0, 1.0.
- Test files: `samples/` is not in git. Ask Jing Yuan for
  `samples/e2e-cohort` and `samples/e2e-weird`.

## P1

### 1. Save the tester and starter folders with each run

- Why: Resume after the app is restarted must use the testers the run
  started with. Jing Yuan's side is done in 957ec97:
  - `GradingRun` has `getTestersDirectory()` and `setTestersDirectory()`
    (and `getStarterDirectory()`, which was already there);
  - `GradingService` sets the folder at start and uses it on Resume.
  The database does not store either folder, so after a restart Resume
  uses `paths.testers.dir` and warns on every student.
- Where: `SqliteResultRepository`. Do it the way `score_sheet_file` was
  added:
  - the `CREATE TABLE runs` text (line 71);
  - `SCORE_SHEET_COLUMN` and `ADD_SCORE_SHEET_COLUMN` (lines 135 to
    141), the `ALTER TABLE` for old databases;
  - the `INSERT` (line 146) and `insertRunRow` (line 742);
  - the `SELECT` (line 194) and the code that builds the `GradingRun`
    when loading.
- Add `testers_dir TEXT` and `starter_dir TEXT`.
- Done when: in a new process, Resume of a cancelled run uses the saved
  folder, and the students it grades have no warning whose id starts
  with `resume-`. If the saved folder is deleted, Resume stops with "is
  no longer there". That check is already in `GradingService`.

### 2. Print scores without float noise or exponents

- Where: `ScoreFormatter.format` (line 28) returns
  `String.valueOf(score)`.
- What happens: a tester that gives 0.1 marks per case puts
  `0.30000000000000004` or `0.9999999999999999` in the CSV numerator, and
  a tiny value prints as `1.0E-5`. Totals are now rounded to 2 decimal
  places in `Submission.getTotalScore`, but question scores and other
  values still go through this method.
- Fix: round to 2 decimal places, print whole numbers as before (`15`,
  not `15.0`), and never print an exponent.
- Done when: 0.1 + 0.2 prints `0.3`, 15.0 prints `15`, and 0.00001
  prints `0`.

## P2

### 3. One unreadable saved row empties History and Statistics

- Where: `SqliteResultRepository`, the `valueOf` calls at lines 1054,
  1158, 1162 and 1171, and `listRuns`.
- What happens: verified. A saved anomaly type that no longer exists in
  `AnomalyType` throws, and `listRuns` stops at the first bad run.
  History then shows nothing, and Statistics shows mean 0. This will
  happen: the taught-Java branch edits `AnomalyType`.
- Fix: map an unknown anomaly type to `INTERNAL_ERROR`, and an unknown
  status to `FAILED`. In `listRuns`, skip a run that still cannot be
  read, and keep the others.

### 4. A second GradeZZY writing at the same moment fails after 2 ms

- Where: the connection is opened at line 290 with no busy timeout.
- Fix: run `PRAGMA busy_timeout = 5000` right after connecting. The four
  grading threads are already safe, because every repository method is
  `synchronized`.

### 5. Save review decisions

- Where: decisions (Reviewed, Rejected) are kept only in memory, in
  `LiveBackend`, so they are lost when the app is closed.
- Fix: add a `decision` column next to `fix_status`, with save and load
  methods on `ResultRepository`. Tell Seth, who will call them from
  `LiveBackend`.

### 6. A late batch joins the section's run (decided)

- Decided on 3 November: when a section that already has a run is graded
  again, the instructor can add the new students to that run. A student
  who was graded before is graded again, and the newer result replaces
  the older one. So each section keeps one run, and Statistics
  (`newestRunPerSection`, line 208) stays as it is.
- Example: G3 graded as 16 zips at 2:04 pm, then olivia.sim.2024's late
  zip at 4:10 pm. Today the second run's CSV has 1 mark and 17 blanks,
  and the G3 average shows 20.0 (Olivia alone). After the change, both
  are in one run: 16 marks, 2 blanks, average 16.44.
- Your side:
  - Check that `saveRun` (line 336) of a run whose list of students grew,
    or where one student's result was replaced, leaves exactly one row
    per student. It deletes and rewrites the students (line 180), which
    should already do it; confirm it with a test.
  - Check that History shows the new student count and time for that
    run after the late batch is added.
- Jing Yuan writes the service method that adds students to a run.
  Seth asks the question on Set up.

### 7. Save the fixes the instructor undid

- With the Undo decision (Jun Sheng's file, item 5), each submission
  keeps a list of undone fixes (`Submission.getRejectedFixes()`).
- Add a `rejected_fixes TEXT` column to `submissions`, holding the keys
  joined with commas, so Resume after a restart still skips them.

## P3

- `CsvScoreSheetExporter`:
  - `splitLine` (line 246) and `fillRow` (line 226) rewrite `"TAN"` as
    `TAN`;
  - a name in quotes that spans two lines makes the export fail with
    "Unclosed CSV quote", though `RosterReader` accepts it.
  - Fix: change only the numerator field, and copy the rest of the line
    as it is.
- Damien is making `RosterReader` accept Windows-1252 score sheets. Write
  the export in the encoding the template was read with.
- `StudentReportExporter` and `AuditLogExporter` are written but not
  registered. Add them in `ApplicationContext.createExportService`
  (line 177); you may add those lines. The audit log now records cancel,
  retry, resume, approve, undo, settings saves, backups and restores, so
  the audit export is worth having.
