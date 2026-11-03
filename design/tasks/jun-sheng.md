# Jun Sheng: tasks for 3 November 2026

Your area: `fix`, `detection`, `plagiarism`, `FixStage` and `DetectStage`.

These came out of a full test of `main` (421d2f6) on 3 November: the 17
student e2e cohort, 21 odd uploads, 14 cheating attempts and a 200
student batch. Your PR #17 fixed three cohort marks (kenneth.yeo is 20
now, grace.chua is flagged as hard-coded, olivia.sim is matched). The
items below are the cases that still lose a student's marks. Do the P1
items first.

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
  - Fixes change only the working copy, and each one waits for Approve
    or Undo.
  - Words on screen: never "anomaly", "capability", "evidence", "repair",
    "pipeline" or "stage". Say "fix" and "step".
- Check before you push, from the GradeZZY folder: `sh compile.sh core`,
  then `sh compile.sh` (no error or warning), `sh run.sh --check`,
  `sh run.sh --selftest`, and `sh run.sh console G2`. The console must
  still print 12.0, 10.0, 10.0, 0.0, 20.0, 1.0.
- Test files: `samples/` is not in git. Ask Jing Yuan for
  `samples/e2e-cohort` (hand-worked marks in its README, column
  "Correct") and `samples/e2e-weird`. Every cohort mark must stay as in
  that column.

## P1: correct answers that score 0

### 1. A UTF-8 BOM or a Windows-1252 file does not compile

- Where: no fixer handles either. `SourceCompiler` passes no `-encoding`
  (Zach's file).
- What happens: both verified on the model answer.
  - Notepad's "UTF-8 with BOM" gives `illegal character: '﻿'` and
    0 marks for that question.
  - A Windows-1252 file with one `é` in a comment gives `unmappable
    character (0xE9) for encoding UTF-8` and 0 marks.
- Fix: a new fixer, for example `EncodingFixer extends AbstractFixer`.
  - It strips a leading BOM, and rewrites a `.java` file that is not
    valid UTF-8 as UTF-8, decoding it as `windows-1252`.
  - Like the other fixers, it is undoable and waits for Approve.
  - Register it in `ApplicationContext.createFixManager` (line 164), the
    one registration site; you may add that line.
- Done when: both files get full marks, and Undo puts back the original
  bytes.

### 2. A wrapper folder plus one extra folder scores 0/20

- Where: `AbstractFixer.findContentRoot` (line 220), and Zach's
  `QuestionFolderFinder.findContentRoot` (line 65).
- What happens: a zip holding `RenameToYourUsername/` and `bin/` at the
  top (an IDE build folder) gets a content root of null. All three
  question folders are then reported missing, nothing is fixed, and the
  student gets 0/20.
- Fix: when no top folder matches the username, use the single top
  folder that holds the question folders, and ignore the rest. Agree the
  rule with Zach so both `findContentRoot` methods give the same answer.
- Done when: that zip grades like the model answer, with the wrapper fix
  waiting for Approve.

## P2

### 3. The similarity check uses the starter from the settings

- Where: `TokenSimilarityDetector`, line 169, reads `paths.starter.dir`.
- What happens: if the instructor picks another starter at Set up, the
  starter code is not removed from the comparison. Every student then
  looks alike on the lines they all received.
- Fix: use `gradingRun.getStarterDirectory()`, and fall back to the
  setting when it is null.

### 4. Show what a fix changed

- Where: `Capability.REPAIR_VALUES` is still PROPOSED, so
  `StudentPanel` (line 467) hides Before and After in live mode. The demo
  shows "Before: RenameToYourUsername/ After: learner.bravo/".
- Fix: fill a before and after value on each `FixRecord` (folder names
  for the folder fixers, the old and new header lines for
  `HeaderNameFixer`). Then mark the capability as working.
- Done when: in the live window, a folder fix shows both names before
  the instructor approves it.

### 5. Undo grades the student again without that fix (decided)

- Decided on 3 November: when the instructor undoes a fix, the student
  is graded again with that fix skipped, so the marks match the files
  they submitted.
- Example: kenneth.yeo.2024 (cohort). With the `q3` to `Q3` fix he gets
  20/20; with fixes off he gets 16/20. Today, after Undo, he keeps 20.
  After this change, Undo gives 16 and the card says "Fix undone. Marks
  20 to 16".
- Your side, the fixing step:
  - Give every fix a key that names what it changed and stays the same
    when the student is graded again: the fixer's name plus the path it
    changed, relative to the working copy. For example
    `CaseNameFixer:q3`.
  - Jing Yuan is adding `List<String> getRejectedFixes()` to
    `Submission`. `GradingService.resetForRetry` keeps that list.
  - In `FixManager` (or `FixStage`), skip a fix whose key is in that
    list. Still add the problem card, with the fix status `UNDONE`, so
    the instructor sees why the question scored 0.
  - Give `FixRecord` (or the anomaly) a way to read that key, so
    `AnomalyService.undoFix` can add it to the list.
- Jing Yuan builds the rest: after Undo, she adds the key and grades
  that one student again. Agree the method names with her this morning.
- Done when: undo both fixes on kenneth.yeo.2024 and his total becomes
  16, with Q3 at 0. Approve them instead and he stays at 20.

## P3

- One wrapper moved one level up gives three separate cards ("The Q1
  folder is missing", Q2, Q3), each at error level, though the fix
  worked. Make it one fix for the move. Seth is changing the
  validator's wording to match.
