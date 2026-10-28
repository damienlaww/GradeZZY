# GradeZZY UI redesign: Guided steps

Date: 2026-10-28. Status: draft for approval.

## Why

The current window has 16 screens, a sidebar, a context bar, a toolbar,
tables and an inspector on almost every page. The job it supports is a
straight line: choose the class, add the zips, grade, check what needs
checking, export the score sheet. Instructors cannot tell where each step
lives, so the app feels large and cramped even though each screen works.

The brief marks usability, not looks (section 4.3: "Usability is more
important than visual aesthetics", "must clearly communicate grading
progress, detected errors, and final results"), takes up to 10% off for
usability issues, and asks for Nielsen's 10 heuristics.

## Goal

A clean, simple window that a tutor who has never seen GradeZZY can use
without help: one step at a time, one obvious next button, details only
when asked for.

Success looks like:

- From opening the app to an exported score sheet in four screens and
  four presses of the blue button, with no other navigation needed.
- Every MUST, SHOULD and COULD feature the current UI offers is still
  reachable, but anything outside the four steps is one click away, not
  on the main path.
- The `ui` package is much smaller than today (about 29,000 lines across
  `ui` and its subpackages). Target: the screens, components and CSS
  together under about 6,000 lines.

## Non-goals

- No change to grading behaviour. Every package outside `gradezzy.ui` is
  untouched.
- No change to `DashboardBackend`, `LiveBackend`, `DemoBackend` and the
  other classes in `ui.backend` and `ui.demo`, except small additions if a
  new screen needs data the interface does not yet give. Any addition goes
  into both backends.
- No new libraries. JavaFX 25 (with FXML and CSS), AtlantaFX 3.0.0 and
  ControlsFX 11.2.5 only.

## Anti-goals (a failure even if it works)

- Reproducing the old layout: no permanent sidebar, no context bar, no
  inspector on every screen, no 16-item navigation.
- More than one blue (primary) button on a screen.
- Colour used for anything except status and the one primary button.
- Jargon on screen: "anomaly", "capability", "evidence", "repair",
  "pipeline", "stage" are not shown to the instructor. Use "needs review",
  "fixed for you", "flag", "did not compile", "timed out".
- A screen that needs scrolling to reach its primary button at the
  default window size (1200 x 800).

## Constraints

- AGENTS.md and the coding style apply to every new file: 80 columns,
  4 spaces, K&R braces, Javadoc with tier, header comment, no emojis, no
  em dashes, untaught constructs listed in the README table.
- Every `javafx` import stays in `gradezzy.ui` and its subpackages.
- `sh compile.sh core`, `sh compile.sh`, `sh run.sh --check`,
  `sh run.sh --selftest` and `sh run.sh console` must pass at the end.
- Works in light and dark (AtlantaFX Cupertino Light and Dark) and at
  window sizes down to 960 x 640.
- The mockups in Figma ("GradeZZY Redesign Directions", row A,
  https://www.figma.com/design/D5J4Lu7KM7JcCll7aJALHh) are the visual
  reference. Where this document and the mockup disagree, this
  document wins.

## The design

### Window frame (same on every step)

- **Top bar** (56 px): logo on the left; the step bar in the middle
  (`1 Set up`, `2 Grade`, `3 Review`, `4 Export`; finished steps show a
  green tick, the current step a filled blue number, later steps grey);
  on the right three text buttons with icons: History, Settings, Help.
  A finished step can be clicked to go back to it. A later step cannot
  be clicked until the steps before it are done.
- **Content area**: one step at a time. Set up and Export use a centred
  column about 760 px wide; Grade and Review use the full width.
- **Action bar** (68 px, bottom): Back on the left, an optional short
  status sentence, optional secondary buttons, and the one blue pill
  button on the right that moves to the next step. When the blue button
  is disabled, the status sentence says why ("Add at least one zip file").

History, Settings and Help replace the content area while open and show
a "Done" button that returns to the step the instructor was on. The step
bar stays visible but greyed while they are open.

### Step 1: Set up

What the instructor sees, top to bottom:

1. Heading "Set up a grading run" and one line of help.
2. **Class** card: Module, Section and Assessment drop-downs side by side.
   The assessment shows its question count and total marks. A "Test
   cases" text link opens a sheet to choose the tester folder or type
   test cases (the current Assessments screen's features).
3. **Submissions** card: one large drop area ("Drop the zip files or
   their folder here, or choose files"). After files are added, the drop
   area shrinks and a list shows each item with a status pill (ready,
   or the problem in plain words, with Remove and Replace links). One
   summary line: "6 zip files added. All 6 match a student on the score
   sheet." This replaces the Submissions and Validation screens. (Builder's
   call: matching zips to the score sheet only happens during grading, so
   the line says "6 zip files ready" and unknown students show up on
   Review.)
4. **Class files** card: three rows, Score sheet (required), Tester files
   (required) and Starter files (optional, used for the untouched-answer
   flag). Each is filled from `config.properties` when Set up first opens
   (the first sample file in demo mode) and has a Change link.

Blue button: **Start grading**. Enabled when `ImportBatch.isReady()`:
at least one usable submission, a score sheet and the tester files. When
disabled, the action bar shows the first blocking reason.

If an earlier run was stopped part way, a quiet banner above the heading
says "The G1 run stopped at 24 of 38. Resume" (resume and retry live
here and in History).

### Step 2: Grade

- Large progress bar, "24 of 38 students", "about 1 minute left".
- The student being graded now and what is happening to them in plain
  words ("Compiling Q2", "Running tests for Q3").
- A compact list of students with a status pill each (waiting, grading,
  done, needs review, failed). It fills in live.
- Secondary button **Cancel grading**, with a confirmation.
- When grading ends the app moves to Review by itself. If it was
  cancelled, the screen offers **Resume** (blue) and the list keeps what
  was graded.

### Step 3: Review

Full width, two parts side by side.

**Left: the results table.**

- Heading "Review the results" and one line: module, section,
  assessment, average.
- A three-part filter (All, Needs review, Failed, each with a count), a
  search field (name or username; a "Search inside code" option for the
  code search), and a check box column for selecting one, several or all.
- Columns: Student (name and username), one column per question with the
  mark, Total, Status pill. Sortable by any column.
- A banner when submissions failed: "1 submission did not finish.
  Retry failed".

**Right: the student panel** (opens when a row is clicked, closes with
Esc or the X; collapsible, so the table can take the full width).

- Name, username, section; large total "12 out of 20"; status pill.
- One row per question: mark, a thin bar, a short reason ("4 of 5 tests
  failed", "Did not compile", "Timed out"). Clicking a question expands
  it to show one small square per test case (pass or fail) and the time
  it took.
- **Fixed for you** cards, one per automatic fix: what was changed in
  plain words, the before and after values, and Approve and Undo
  buttons. An approved or undone card shows its state and can be
  reversed.
- **Flags** cards (untouched starter, empty answer, hard-coded answer):
  what was noticed, and "Looks fine" and "Keep flagged" buttons. Flags
  never change a mark. "Looks fine" saves `ReviewDecision.REJECTED` (the
  flag does not apply); "Keep flagged" saves `REVIEWED` (the flag stands,
  no action needed). Both settle the flag, so the student leaves the
  Needs review filter; each can be reset to open.
- A "More" menu: Adjust mark, Retry this question, Retry this student,
  Export this student's report.
- A link "Open code and test output", which opens a large sheet with the
  file tree, the source file and the tester output.

Action bar: Back, secondary **Approve all fixes** (approves every
selected student's open fixes, or all if none selected), blue **Continue
to export**. Continuing with open fixes or flags asks once: "3 fixes are
not approved yet. Continue anyway?"

### Step 4: Export

- Heading "Export the results" and a one-line summary.
- **Results at a glance**: four figures (average, median, passed,
  highest) and a small bar chart of the mark bands, plus pass rate per
  question. A drop-down switches between "This section" and other
  sections or the whole module (the statistics filter).
- **Score sheet** card: where the CSV will be saved, a choice of all
  students or only the ones selected in Review, and check boxes for the
  extra files (JSON, student reports, issues report).
- **Similar code** card: "Check for copied code" runs the similarity
  check when it is switched on in Settings; the pairs found are listed
  with their similarity, and Compare opens the two files side by side.

Blue button: **Export score sheet**. After it succeeds the card shows
"Saved to workspace/output" with "Show in folder", and the blue button
becomes **Grade another section**, which returns to Set up.

### History

Three tabs in one screen:

- **Runs**: every earlier run (module, section, assessment, students,
  date, state). Open loads it into Review; Resume continues a stopped run.
- **Backups**: Back up now, restore from a backup, with a preview of what
  will be restored.
- **Activity**: the audit log with search, and Export.

### Settings

One scrolling page with grouped sections (General, Files and folders,
Grading, Checks, Similar code, Export, Saved data). Each field is
checked as it is typed, with the message under the field. Save and
Cancel in the action bar. Theme (light or dark) and animations are under
General.

### Help

A sheet over the current step: the four steps in one sentence each,
keyboard shortcuts, and where files are saved.

### Keyboard

- Ctrl (Cmd on macOS) + Enter: the blue button.
- Esc: close the open panel or sheet.
- Ctrl/Cmd + F: search on Review.
- Ctrl/Cmd + 1 to 4: go to a finished step.
- Tab order follows reading order on every screen.

### Look

- Base theme: AtlantaFX Cupertino Light and Dark. A new `app.css` holds
  only colour tokens and a handful of classes; dark mode overrides tokens
  only.
- Background light grey, white cards with a 1 px border and 12 to 14 px
  corners, no heavy shadows.
- One accent blue for the primary pill button, links, the current step
  and selection. Status colours: green (done), amber (needs review), red
  (failed or did not compile), grey (waiting). Pills use a pale fill and
  dark text of the same hue, so they stay readable in both themes.
- System font. Sizes 12, 13, 15, 17 and 26 px. Spacing in steps of 4.
- Icons: a small set of SVG paths (`SVGPath`) drawn in one place.

### Where today's 16 screens go

| Today | New home |
|---|---|
| Overview | Removed (Set up is the start) |
| Modules, Assessments, Submissions, Validation | Set up |
| Grading Progress | Grade |
| Review Anomalies, Results, Student Evidence | Review (table and student panel) |
| Statistics, Similarity Review | Export |
| History | History, Runs tab |
| Audit Log | History, Activity tab |
| Settings | Settings |
| Help | Help sheet |

### Nielsen's heuristics, in short

1. Visibility of status: the step bar, the progress screen, status pills.
2. Match with the real world: plain words, instructor vocabulary.
3. User control: Back on every step, Undo on every fix, Cancel grading.
4. Consistency: same frame, same blue button position on every step.
5. Error prevention: Start grading disabled until ready, with the reason
   shown; confirmation before continuing with open fixes.
6. Recognition over recall: everything visible on the step; no hidden
   modes.
7. Flexibility: keyboard shortcuts, bulk approve, select several.
8. Minimalist design: one job per screen, details on demand.
9. Error recovery: each problem says what happened and what to do.
10. Help: the Help sheet and the one-line help under every heading.

## How it is built

- New code replaces the screens of `gradezzy.ui`: one FXML file and one
  controller per screen (Set up, Grade, Review, Export, History,
  Settings), the window frame, the sheets (test cases, code viewer,
  compare, adjust mark, help) and a few small components (step bar,
  status pill, drop area, student panel, score bar, empty state).
- Controllers call only `DashboardBackend`, as now. Grading runs off the
  JavaFX thread and updates the screen through `Platform.runLater`.
- `MainApp` keeps its command-line options (`--demo`, `--check`,
  `--selftest`, `--snapshot`, `--windowtest`).
- The work stays uncommitted in the working folder until the user
  decides to commit; no branch is created, pushed or opened as a pull
  request unless the user asks.

## Phases

**Phase 1: prototype to try.** The window frame and the four steps,
wired to `DashboardBackend` so they work in `--demo` and live mode, plus
History, Settings and Help in a simple form. The old screen classes stay
in place (unused) so the build and the existing checks keep compiling.
The user runs `sh run.sh --demo`, tries it and gives feedback.

**Phase 2: complete.** After the user approves Phase 1: every remaining
feature from the table above, the sheets, the old screen classes
deleted, `ScreenCheck`, `SelfTest` and the snapshot and window tests
rewritten for the new screens (the same seven demo flows), README,
`design/UI-INTEGRATION.md` and `AGENTS.md` updated.

## Features the live backend does not provide yet

`DashboardBackend.supports(Capability)` says which features work. A
control whose feature is not supported is hidden in that mode, never
shown broken and never labelled with internal words. Demo mode supports
everything, so it shows the full design. The "continue with open fixes"
question counts only what the instructor can actually settle.

## Decisions made while building (builder's calls)

- Screens are built in Java code with one stylesheet, not FXML: one
  readable class per screen and nothing to keep in sync.
- The new code lives in `gradezzy.ui.flow` during Phase 1 so the old
  screens still compile beside it; `sh run.sh --classic` opens the old
  window until Phase 2 removes it.
- The 6,000-line figure is a goal, not a limit. Keeping every feature
  wins over it.
- Phase 2 (deleting the old screens) waits for the user's approval of
  the prototype and agreement from the teammates who own the `ui`
  package.

## Left to the builder

- Exact pixel sizes, icon shapes and wording beyond what is given here,
  as long as the anti-goals hold.
- How controllers and components are split into classes.
- Which AtlantaFX and ControlsFX controls to use for each part.
