# Seth: tasks for 3 November 2026

Your area: `validation`, `ValidateStage`, `InputValidator`, looking after
the window (`ui.flow`, `ui.backend.LiveBackend`), and the Windows runs.

These came out of a full test of `main` (421d2f6) on 3 November. That
included 148 window snapshots and a live run of the 17 student e2e
cohort through the window. The window works; the items below are where
an instructor can miss a problem, get stuck, or read a confusing message.
Do the P1 items first. P3 items only if you have time.

## Before you start

- Branch from `main` once the pull request from `jingyuan/taught-java`
  is merged. Until then, branch from `jingyuan/taught-java`. Line numbers
  below are from that branch (commit 957ec97); if a line moved, search
  for the method name.
- That branch rewrote the window in taught Java. The Review table cells
  are their own classes again (`ReviewRow`, `ReviewTickCell`,
  `ReviewStudentCell`, `ReviewMarkCell`, `ReviewStatusCell`).
- Project rules (the local rules file is not in git, so here they are):
  - Only Java taught in CS102: no ternary, nested or anonymous classes,
    try-with-resources, multi-catch, varargs parameters, `StringBuilder`,
    `String.join`, `List.of`, streams or method references. In `ui`, a
    lambda is one line calling one method (`event -> exportResults()`).
    The README section "Java taught in CS102" shows the taught way for
    each. Leave `ui/check` alone.
  - 80 columns, 4 spaces, braces on every block, no magic numbers, a
    header comment on every file, Javadoc with its MUST/SHOULD/COULD tier
    on every public type and method.
  - No em dashes and no emojis. No co-author or attribution lines in
    commits.
  - Window rules:
    - no shadow on the page (only sheets, menus and tooltips);
    - icons in a fixed 20 px column, hairlines only between rows;
    - spacing 4/8/12/16/20/24/32;
    - forms in the centred 760 px column (`Parts.narrowPage`), tables
      and charts full width (`Parts.widePage`);
    - colour means status, plus the accent blue for the one primary
      button, the current tab and step, and chart marks.
  - Words on screen: never "anomaly", "capability", "evidence",
    "repair", "pipeline" or "stage".
  - Anything that looks new needs a draft that Jing Yuan approves first.
    Items 1, 2 and 3 below were drafted and approved on 3 November.
    Build them as described; for anything else new, send a sketch
    first.
- Check before you push, from the GradeZZY folder: `sh compile.sh core`,
  then `sh compile.sh` (no error or warning), `sh run.sh --check`,
  `sh run.sh --selftest` (all 7 flows pass), and `sh run.sh --snapshot`
  to look at the screens you changed.
- Test files: `samples/` is not in git. Ask Jing Yuan for
  `samples/e2e-cohort` (17 zips, an 18-student roster, its own
  `testers/` folder) and `samples/e2e-weird`.

## P1: the instructor misses a problem

### 1. Check the testers on Set up, before grading (approved)

- What happens:
  - Pick "Use Configured" testers with the cohort, and Set up says
    "Tester-Files · Folder found. Ready".
  - After grading, all 17 students have lost 7 marks on Q2, each with
    two "A required file is missing" cards: 34 cards for one problem.
  - Export then shows "Highest 13 / 20" with no warning.
- Fix:
  - On the tester row of Set up, call Zach's new check (he is adding a
    method that lists questions with no tester, missing data files and
    testers that do not compile). Agree its name with him this morning.
    Show a warning pill that names the files and the questions they
    affect.
  - On Review, show one banner for a problem every student shares,
    instead of a card on each student.
- Done when: `w15-missing-tester`, `w16-no-tester-data` and
  `w21-broken-tester` each show the warning on Set up before grading.

### 2. Show who did not submit, and blank marks before export (approved)

- What happens: with the cohort, the score sheet has 18 students and
  there are 17 zips.
  - Review lists 17 rows, and Export says "Every student (17)".
  - rachel.tan.2024, who submitted nothing, appears nowhere, and her
    mark goes out blank without a word.
  - The same happens to a student whose bad zip was removed on Set up.
- Fix:
  - A "No submission" filter with its count on Review.
  - A line on the Export score sheet card: "16 marks filled, 2 left
    blank: 1 could not be graded, 1 no submission". Repeat it in the
    message after exporting.
- Done when: the cohort shows rachel.tan.2024 as "No submission", and
  Export names the two blank rows.

### 3. Undo and late batches in the window (decided)

Two decisions from 3 November change what the window does.

- **Undo grades the student again.**
  - When the instructor clicks Undo on a fix, `LiveBackend` calls
    `AnomalyService.undoFix`. That now grades the student again, which
    takes a few seconds, so run it in a `Task` and show "Grading again"
    on the card.
  - Then show the change on the card, for example "Fix undone. Marks 20
    to 16". The Review row updates with the new marks.
- **A late batch joins the section's run.** On Set up, when the chosen
  module and section already have a run, show a question before Start
  grading:

  > G3 was graded at 2:04 pm (16 students). Add these students to that
  > run? A student graded before is graded again, and the new result
  > replaces the old one.
  >
  > [Add to that run] [Start a new run]

  "Add to that run" is the primary button. Review and Export then show
  the whole section.
- **Also add a guard on Export.** If the run on screen has fewer students
  than the score sheet, and another run of the same section exists, say
  so: "This run has 1 of 18 students. Another G3 run has 15 more."
- Jing Yuan is writing the service methods; agree their names with her
  this morning.

## P2

### 4. One unreadable zip blocks the whole section

- Where: `ImportBatch.isReady` (line 325) and its message (line 305).
  The snapshot `demo-light-1200-2-set-up-files.png` shows "Start
  grading" disabled by "1 chosen file or folder cannot be used".
- What happens: a damaged zip picked on its own blocks everyone, while
  the same zip inside a chosen folder does not (cohort
  `priya.nair.2024`). The only way out, Remove, makes the student vanish
  (see item 2).
- Fix: let grading start with the usable files, carry the bad one into
  Review as "Could not open", and list problem rows first on Set up.

### 5. "Could not fix" cards can never be settled

- Where: `StudentPanel`, around line 478: a blocked fix gets only a note
  and no button.
- What happens: Review never empties ("17 students still need review"),
  and every Continue asks again. This trains the instructor to click
  through real warnings.
- Fix: add "Mark as checked" (`ReviewDecision.REVIEWED`) to these cards.

### 6. Closing the window while grading loses the run without asking

- Where: there is no `setOnCloseRequest` anywhere in `ui`, so the window
  closes at once. `FlowState.isGrading()` (line 166) knows when a run is
  going on.
- Fix: ask first ("Grading is still running. Stop and close?"). Also ask
  when Settings has unsaved changes.

### 7. Messages with full paths and error chains

- What happens: the student panel shows
  - `MISSING_HEADER_NAME` ending in "Working copy file:
    /private/tmp/.../workspace/working/.../Q1a.java";
  - a damaged zip as "Grading stopped early: Could not prepare ...:
    Could not create the working copy ...: Could not unpack /full/path:
    zip END header not found".
- Fix: show the file name relative to the student's folder. Damien is
  giving damaged zips their own type (`INVALID_ARCHIVE`) with a one-line
  message. Make sure `Problems` shows it in plain words.

### 8. Slow work on the window's thread

- Where:
  - `SetUpScreen` calls `getBackend().inspect` for every dropped file on
    the window thread (lines 406, 455 and 467). 300 zips on OneDrive will
    freeze it.
  - The code search in `ReviewScreen`, and the export in `ExportScreen`,
    run there too.
- Fix: run them in a `Task` like grading does, and update the screen
  with `Platform.runLater`.

### 9. The validator's wording after a fix

- Where: `validation`, the messages for a question folder found one
  level too deep.
- What happens: after the folder fix has worked, the card still says
  "The Q1 folder is missing" at error level, once for each of Q1, Q2 and
  Q3.
- Fix: word it "Q1 was one level too deep and has been moved", at warning
  level, when the fix applied. Jun Sheng is making it one fix instead of
  three.
- Also `AbstractValidator`, line 43: "Approve the repair if ..." uses a
  banned word. Make it "fix".

### 10. Run the `.bat` scripts on Windows

- They have never been run. On Windows with JDK 25:
  - `compile.bat` and `compile.bat core`;
  - `run.bat --check`, `run.bat --selftest`, `run.bat console G2`.
- Record each result in the README test table (the `run.sh console`
  row and the others). If something fails, write down the exact error
  and tell Jing Yuan.

## P3: if you have time

- Banned word "repair" in on-screen messages: `LiveBackend` lines 415,
  418, 432, 440, 998 and 1019 to 1027 ("Approved the repair for ...",
  "The repair for ... was not approved"). Say "fix".
- Review and Export disagree on the average (10.3 against 12.4 in the
  demo). `ReviewScreen` (around line 575) averages every row, with failed
  and waiting students as 0. `MarkStats` averages graded students only.
  Use `MarkStats` on both, and say "average of N graded".
- Flag cards never show `getSuggestedAction()`, so they lose advice
  such as "Add the data file to the tester folder, then grade again".
- A waiting row shows Total "0" in bold. Show "-".
- Long names such as "Devan Alexandra-Rose Mont..." are cut off with no
  tooltip.
- At 960 x 640, Review shows about 5 rows. Move the "Could not grade"
  banner into the filter row.
- The accent blue fills the segmented toggles and the drop area
  (`flow.css` lines 540 to 542 and 1037). By the colour rule it belongs
  only to the primary button, the current tab and step, and chart marks.
- Accessibility:
  - the row check boxes, the tick-all box and the search field have no
    accessible names;
  - Ctrl or Cmd+F does not focus the search (`FlowWindow`).
