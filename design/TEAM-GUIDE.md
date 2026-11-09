# GradeZZY team guide

Everything about GradeZZY that is not the grading backend: how to build
and test it on each platform, the window's packages, each teammate's
part, the coding rules, the Java used beyond the CS102 syllabus, the
feature checklist and the steps before submission. The backend itself
is explained in `README.md`.

## Prerequisites

- **JDK 25**, with `java` and `javac` on the PATH. Check with
  `java -version`.
- Nothing else to install. JavaFX 25 and the other libraries are already
  in `lib/` (about 40 MB). See [Libraries](#libraries).
- Windows (64-bit Intel or AMD) or macOS (Intel or Apple Silicon). Linux
  is not supported because its JavaFX jars are not included.

## How to compile and run

Run every command from the `GradeZZY` folder.

### macOS (Terminal)

```sh
sh compile.sh            # compile everything into classes/
sh run.sh                # open the window
sh run.sh --demo         # the same window with synthetic data only
sh run.sh --check        # open every tab, step and sheet once and exit
sh run.sh --selftest     # run the eight demo flows, print PASS or FAIL
sh run.sh console        # grade from the terminal, without JavaFX
sh run.sh console G2     # the same, for section G2
sh run.sh console CS102 G2   # the same, for module CS102
sh compile.sh core       # compile only the grading core, without JavaFX
sh clean.sh              # empty classes/ before submission
```

### Windows (Command Prompt)

```bat
compile.bat
run.bat
run.bat --demo
run.bat --check
run.bat --selftest
run.bat console
run.bat console G2
run.bat console CS102 G2
compile.bat core
clean.bat
```

In PowerShell, which is the default terminal in VS Code, put `.\` in
front of each script, for example `.\run.bat console G2`. Without it
PowerShell says the script "is not recognized".

The `.sh` scripts are also written to work in Git Bash on Windows.

Two more developer options exist for checking the screens:
`--snapshot [folder]` saves a picture of every screen, and
`--windowtest` drives the real window with the mouse and keyboard (on
macOS, allow the app that runs java under Accessibility and Screen
Recording first). Both are explained in `design/UI-INTEGRATION.md`.

`compile.sh core` empties `classes/` before it compiles, so run
`compile.sh` again before `run.sh`.

### What has been tested

| Check | macOS Apple Silicon, JDK 25.0.4 | macOS Intel | Windows |
|---|---|---|---|
| `compile.sh`, zero errors and warnings | Passed | Not tested | Passed (Git Bash, JDK 25.0.4, 2026-11-03) |
| `compile.sh core` (no JavaFX) | Passed | Not tested | Passed (Git Bash, JDK 25.0.4, 2026-11-03) |
| `run.sh console` | Passed | Not tested | Ran with no error (Git Bash, JDK 25.0.4, 2026-11-03). Five totals match; xing.yan.2023 gets 1.0, not 15: its Q2a tester times out on test 2, also with a 60 s limit |
| `run.sh --check` (every tab and step, live and demo, both themes, two sizes) | Passed (2026-11-09) | Not tested | Passed (Git Bash, JDK 25.0.4, 2026-11-03) |
| `run.sh --selftest` (8 demo flows) | Passed, 8 of 8 (2026-11-09) | Not tested | Passed, 7 of 7 (Git Bash, JDK 25.0.4, 2026-11-03) |
| `run.sh` (window opens) | Opened off screen by `--check`; not clicked through by hand | Not tested | Opened off screen by `--check`; not clicked through by hand |
| `run.sh --snapshot` | Passed (2026-11-09, 240 pictures) | Not tested | Passed (Git Bash, JDK 25.0.4, 2026-11-03) |
| `run.sh --windowtest` | Not run: macOS blocked the robot's clicks (needs Accessibility and Screen Recording permission) | Not tested | Not run |
| `.bat` scripts | Not applicable | Not applicable | Passed (Command Prompt, JDK 25.0.4, 2026-11-03): `compile.bat`, `compile.bat core`, `run.bat --check`, `run.bat --selftest` (7 of 7), `run.bat console G2` (same totals as `run.sh console`) |

**A teammate on Windows and one on an Intel Mac must run the scripts and
report back.** The brief takes up to 50% off if the code does not compile
or run on the instructor's laptop.

## Folder structure

| Folder or file | What it holds |
|---|---|
| `compile.sh`, `compile.bat` | Compile all sources into `classes/` and copy the `.css` file and the logo pictures. |
| `run.sh`, `run.bat` | Run the window, the demo, the screen checks or the console. |
| `clean.sh`, `clean.bat` | Empty `classes/` and remove `workspace/`. |
| `config.properties` | Every setting of the application. |
| `src/` | All Java source files, plus the stylesheet of the `ui` package (`ui/css/flow.css`) and the logo as PNG files (`ui/images/`). |
| `classes/` | Compiled classes. Created by the compile script, not in git, and must be empty at submission. |
| `lib/` | The library jars. `lib/javafx/<platform>/` holds JavaFX for each platform. |
| `media/` | Images and audio used by the application. Empty for now. |
| `design/` | `redesign/`, the design of the current window and its decisions; `UI-INTEGRATION.md`, how each backend method reaches the screens; `NIELSEN-REVIEW.md`, the window checked against Nielsen's 10 usability heuristics; `gradezzy-design.html` and `UI-REVIEW.md`, the design and review of the earlier 16-screen window, kept for reference; `concept/`, Damien's design concept and its handoff notes. |
| `README.md` | How the grading backend works. |
| `design/TEAM-GUIDE.md` | This file: build, team parts, coding rules, checklist. |
| `docs/` | The project brief and coding style PDFs. Not in git. |
| `samples/` | The provided testers, score sheet, starter folder and six sample submissions. Only `Tester-Files/` and `e2e/` are in git; the score sheet, starter folder and submissions are not. |
| `samples/e2e/` | The end-to-end test set: 42 students for section G4, with duplicate, damaged, late and copied uploads. `make_e2e.py` rebuilds it; its `README.md` lists the expected marks. Graded with `samples/Tester-Files`. |
| `workspace/` | Created while grading: working copies, exports, database. Not in git. |

Two things to know:

- The brief calls the media folder `media files`. The team chose `media`.
  Rename it before submission if the instructors insist on the brief's
  name.
- `docs/` and most of `samples/` are kept out of git because the
  documents are marked restricted and the samples hold student names.
  Only `samples/Tester-Files/` and the synthetic `samples/e2e/` set are
  tracked. Each teammate copies the rest in from eLearn: `samples/` must
  also contain `student-submission/`, `RenameToYourUsername/` and
  `IS442-ScoreSheet.csv`, as `config.properties` expects.

## The window's packages

The backend packages are listed in `README.md`. These are the window's.

| Package | Responsibility | Main classes |
|---|---|---|
| `ui` | JavaFX only: starting the window and small shared helpers. | `MainApp`, `ThemeManager`, `GradingTask`, `DragDropHandler` |
| `ui.flow` | The window: tabs Home (a card per section), Grading (Set up, Grade, Review, Export), Statistics, History (runs, backups and activity) and Settings, with their sheets and charts. Plain JavaFX code, no FXML. | `FlowWindow`, `HomeScreen`, `AssessmentsSheet`, `AssessmentEditor`, `SetUpScreen`, `GradeScreen`, `ReviewScreen`, `ExportScreen`, `StatisticsScreen`, `RunsScreen`, `SettingsScreen`, `BellCurveChart`, `MarkStats`, `Parts`, the review table cells `Review...Cell`, and comparators such as `StudentNameComparator` |
| `ui.backend` | The one interface the screens call, and its live version that calls the services. | `DashboardBackend`, `LiveBackend`, `Capability`, small view classes such as `RepairItem` and `RunRecord` |
| `ui.demo` | Synthetic data for `--demo`. Never reads, grades or saves real files. | `DemoBackend`, `DemoData`, `DemoGrader` |
| `ui.check` | Developer checks behind the `--check`, `--selftest`, `--snapshot` and `--windowtest` options. Not used when grading. | `FlowCheck`, `FlowSelfTest`, `FlowWindowTest`, `RobotDriver` |

## Where to add your code

Search for your name to find your work:

```sh
grep -rn "TODO(Zach)" src
```

Each TODO sits inside a method that already has its signature, Javadoc and
a harmless return value, so the project keeps compiling while you work.
Replace the TODO comment and the placeholder return with real code. Do not
change a method signature without telling whoever calls it.

When a method you finish makes a screen show real data, change that
feature's status from `WAITING` to `WORKS_NOW` in
`src/gradezzy/ui/backend/Capability.java`, then run `sh run.sh --check`
and `sh run.sh --selftest`. `design/UI-INTEGRATION.md` lists which screen
shows each service method.

Owners and TODO counts (0 left). The split is a starting point;
the team can swap areas. The next section explains each person's work in
detail.

| Owner | Packages | TODOs |
|---|---|---|
| Damien | `FileUtils`, `TextUtils`, `ingest`, one stage | 0 |
| Seth | `validation`, `InputValidator`, one stage; looks after `ui` | 0 |
| Jun Sheng | `fix`, `detection`, `plagiarism`, two stages | 0 |
| Zach | `exec`, `testcase`, three stages, the score parser | 0 |
| Jing Yuan | `app`, `config`, `model`, `service`, scoring | 0 |
| Huxley | `report`, `stats`, `persistence`, one stage | 0 |

Jun Sheng built the whole `ui` package, so on 2026-10-16 the work was
split again: Damien took the helpers and the reading of submissions
(`FileUtils`, `TextUtils`, `ingest`, `ExtractStage`), and Seth took the
checking of submissions (`validation`, `ValidateStage`), the form checks
(`InputValidator`) and looking after the screens.

### What is already real code

These parts are written, not stubs, because the skeleton cannot run
without them: `AppConfig`, `GradingPipeline`, `CancellationToken`,
`AssessmentFactory`, `PipelineFactory`, `GradingService.startGrading`,
`ApplicationContext`, `ConsoleMain`, `ConsoleProgressListener`, the
getters and setters of the model classes, and the two constructors of
each exception class. Read them before the presentation; they are short.

The whole `ui` package is also real code: every screen, the live and
demo backends, and the developer checks. So are the `fix`, `detection` and
`plagiarism` packages with `FixStage` and `DetectStage`.

### How to add a new validator

1. Create a class in `gradezzy.validation` that extends
   `AbstractValidator`.
2. Pass its name to `super(...)` and write `validate(...)`. Return the
   anomalies you find; return an empty list when there are none. Do not
   throw for bad student input.
3. Register it in `PipelineFactory.createValidationRunner()`:
   `runner.addValidator(new MyValidator());`

### How to add a new fixer

1. Create a class in `gradezzy.fix` that extends `AbstractFixer`.
2. Write `canFix`, `apply` and `undo`. `apply` may only touch the working
   copy. Before changing a file's content it calls
   `backup(submission, file)`, and `undo` restores that copy and calls
   `discardBackup`. A move or rename is undone by moving back and needs no
   backup. Keep no fields that change after the constructor: one fixer is
   shared by every grading thread.
3. Decide from the working copy on disk where you can; use the anomaly's
   message only to tell which file it is about, as the file name and
   header fixers do. If the disk shows nothing to do, `apply` returns
   null. If the fix cannot be done, throw a `FixException` whose message
   tells the instructor why: `FixManager.getFailureReason` keeps it for
   the screens.
4. Register it in `ApplicationContext.createFixManager()`:
   `fixManager.addFixer(new MyFixer());`. Fixers run in the order they are
   added, so folder fixes come before header fixes.

### How to add a new exporter

1. Create a class in `gradezzy.report` that implements `Exporter`.
2. Write `getFormatName`, `getFileExtension` and `export`.
3. Register it in `ApplicationContext.createExportService()`:
   `exportService.registerExporter(new MyExporter());`

### How to add a new test-case source

1. Create a class in `gradezzy.testcase` that implements `TestCaseSource`.
2. `prepareTesters(...)` must return one `TesterFile` per question. The
   tester it points to must print in the same format as the provided
   testers, so `TesterOutputScoreParser` can read it.
3. Pass an instance to `GradingService.startGrading(...)`. The console
   uses `UploadedTesterSource`; the test case screen will choose between
   that and `TypedTestCaseSource`.

### How to add a detector, a stage or a screen

- **Detector:** implement `Detector`, add it in
  `PipelineFactory.createDetectors()`.
- **Stage:** implement `GradingStage`, add it at the right position in
  `PipelineFactory.createPipeline(...)`.
- **Screen:** add a class in `src/gradezzy/ui/flow/` that extends
  `FlowScreen`, build it from the pieces in `Parts`, style it in
  `css/flow.css`, and show it from `FlowWindow` (a step of Grading or a
  tab). Anything it needs from a service goes through a new
  `DashboardBackend` method, written in both `LiveBackend` and
  `DemoBackend`. Add it to `FlowCheck` so `run.sh --check` opens it.
- **Package:** add its folder to `CORE_SOURCES` in both `compile.sh` and
  `compile.bat`, or to `UI_SOURCES` for a package under `ui`.

### The tester output format

Checked against the five provided tester files. Each tester extends the
student's class, has no package and must be compiled and run inside the
student's `Qx` folder. For each test case it prints `Passed`, or a line
starting with `Failed`. The last line of its output is the total score as
a decimal number, for example `3.0`.

## What each teammate needs to do

Each person owns a slice of the pipeline. The slices are listed in the
order the data flows, which is also the order they unblock each other.
For every slice you get: what to build, the facts you need from the
provided files, how to know you are done, and prompts you can paste into
an AI coding assistant.

### Ground rules for everyone

- Work only in your own packages. If you need a change in someone else's
  class or in a method signature, ask that person first.
- After every change run `sh compile.sh` and `sh run.sh console`. Both
  must finish with no error.
- The final target for the whole team: `sh run.sh console` prints these
  totals for the six sample submissions (from `samples/groundtruth.txt`):

  | Student | Q1a | Q1b | Q2a | Q2b | Q3 | Total |
  |---|---|---|---|---|---|---|
  | chee.teo.2022 | 3 | 3 | 5 | 1 | 0 | 12 |
  | david.2024 | 3 | 3 | 4 | 0 | 0 | 10 |
  | fen.lai.2022 | 1 | 0 | 5 | 4 | 0 | 10 |
  | jing.lim.2021 | 0 | 0 | 0 | 0 | 0 | 0 |
  | ping.lee.2023 | 3 | 3 | 5 | 5 | 4 | 20 |
  | xing.yan.2023 | 0 | 1 | 5 | 5 | 4 | 15 |

  The ground truth file prints 1.0 as the total for xing.yan.2023; the
  team treats that as a typing mistake for 15.0.
- You must be able to explain every line you submit. The brief says so,
  and the instructors may interview anyone.

### How to prompt an AI assistant on this project

A good prompt names the files, states the rules, limits the scope and
says how to check the result. Start every session with this, then add one
of the task prompts from your section:

```text
You are helping me with GradeZZY, a CS102 (SMU) Java group project.
Before writing anything, read README.md,
docs/CS102_Coding_Style.pdf and docs/CS102-Project-Auto_Grading_System.pdf.
Rules: follow the CS102 coding style exactly (80 columns, 4 spaces, K&R
braces on every block, no magic numbers, early returns). Use only Java
taught in CS102 where possible and tell me if you need anything else.
Read settings through AppConfig and ConfigKeys, never hard-code a value.
Keep the header comment and Javadoc on everything, no emojis, no em
dashes. Do not change any method signature or any file outside the
package I name. Do not guess a file format: open the file in samples/ and
check. When you finish, run "sh compile.sh" and "sh run.sh console" and
show me the output. Explain your approach before you write code.
```

Ask for one class at a time, read what comes back, and ask "explain this
method line by line" for anything you could not have written yourself.

### 1. Damien: helpers, then reading submissions

**Packages:** `FileUtils` and `TextUtils` in `util`, `ingest`, plus
`ExtractStage` in `grading`. 19 TODOs. Everyone else is blocked until
`SubmissionLoader` and `WorkingCopyManager` work, so do the two helper
classes quickly and move on to those two.

Helpers first (9 TODOs). Huxley and Seth both need `TextUtils` and
`FileUtils`:

1. `FileUtils`: copy and delete folders, read and write text, list files.
2. `TextUtils`: `isBlank`, `splitCsvLine`, `joinCsvLine`,
   `normaliseLineEndings`.

You are done with the helpers when the methods give the right answers
for the cases below.

| Method | Input | Expected |
|---|---|---|
| `splitCsvLine` | `#01400001,#ping.lee.2023,_,PING LEE,x@y.sg,,20,#` | 8 values, the sixth empty |
| `splitCsvLine` | `a,"b,c",d` | 3 values: `a`, `b,c`, `d` |

Example prompts for the helpers:

```text
Implement gradezzy.util.TextUtils.splitCsvLine and joinCsvLine without a
library and without String.split, so quoted values that contain commas
work. Show the result for these lines: [paste the table above]. An empty
value between two commas must come back as an empty string, not be
dropped.
```

Then the submissions. What to build, in order:

1. `SubmissionLoader.loadSubmissions`: accept a list of paths, each a zip
   file or a folder that holds zip files. Create one `Submission` per zip
   with the section it was uploaded for. Skip the names in
   `ingest.ignored.file.names`. Done, with these rules:
   - A folder that holds a question folder is one student (a submission
     unzipped by hand).
   - In any other folder, each `.zip` directly inside is one student, and
     so is each sub-folder that holds a question folder, directly or in
     its only sub-folder. Other files and folders are skipped. A folder
     with no student stops grading with a message, which names a
     sub-folder that holds zips if there is one. A sub-folder named in
     `assessment.placeholder.folder.names` (the starter folder) is
     skipped.
   - A chosen zip is a bulk zip when it holds at least one zip that is
     not named like a question folder and not inside one (a zip inside
     `Q3/` is part of an answer), holds no question folder at its top or
     one folder down, and its own name does not start with the student
     prefix. It is unpacked into `workspace/working/.batch-*`, each such
     zip inside is one student, and other files in it are skipped.
   - One file per username, in any case, is graded: the one without a
     copy number, else the lowest copy number; on a tie, the one whose
     username is already in lower case, as on the roster, else the first
     chosen. The other files are not graded; the student gets a
     `DUPLICATE_SUBMISSION` warning naming each of them. The score sheet
     export also matches usernames in any case.
   - An upload without a username cannot be graded. The first one stays
     in the run as a failed submission; each other one is named in a
     warning on it, so no two submissions share the empty username.
   - `listCandidates`, `listSkipped`, `isBatchArchive`,
     `listBatchEntries` and `listBatchSkipped` show the same rules
     without unpacking, so the Set up step can list the students, and
     what is skipped, before grading starts.
2. `SubmissionLoader.extractUsername`: remove the prefix matched by
   `ingest.zip.name.prefix.pattern` and the `.zip` ending, so
   `2023-2024-ping.lee.2023.zip` gives `ping.lee.2023`. A browser's copy
   number is removed too, so `2023-2024-ping.lee.2023 (1).zip` also gives
   `ping.lee.2023`.
3. `ZipExtractor.extract` and `isSafeEntry`: unzip with `java.util.zip`.
   Refuse entries that would land outside the target folder (names with
   `..`), and stop at the size and entry limits from the configuration.
4. `WorkingCopyManager`: unzip into `workspace/working/<username>`. Never
   write to the original zip. `removeStudentClassFiles` deletes `.class`
   files except those in `assessment.provided.class.files`.
5. `RosterReader`: read `samples/IS442-ScoreSheet.csv` into `Student`
   objects and remove the `#` in front of the id and username.
6. `ExtractStage`, which calls the classes above.

Facts from the provided files:

- Zip names look like `2023-2024-<username>.zip`.
- The score sheet header is `OrgDefinedId,Username,Last Name,First
  Name,Email,Calculated Final Grade Numerator,Calculated Final Grade
  Denominator,End-of-Line Indicator`, with Windows line endings.

You are done when `sh run.sh console` lists all six students, each with
a working copy under `workspace/working/<username>` and its roster
entry, and an empty zip or a file that is not a zip is reported without
stopping the batch.

Example prompts:

```text
Implement gradezzy.ingest.SubmissionLoader (both TODO methods). List the
zips in samples/student-submission first and show me their names, then
write the code. A path may be one zip or a folder of zips. Use
ZIP_NAME_PREFIX_PATTERN and IGNORED_FILE_NAMES from ConfigKeys. Only edit
files in gradezzy.ingest.
```

```text
Implement gradezzy.ingest.ZipExtractor using java.util.zip. Explain what
a "zip slip" attack is and how isSafeEntry prevents it. Enforce
MAX_ZIP_SIZE_MB and MAX_ZIP_ENTRIES. Throw IngestException with a message
an instructor can act on. Then write a small dummy zip that should be
rejected and show me that it is.
```

### 2. Seth: checking submissions, form checks and the screens

**Packages:** `validation`, `InputValidator` in `util`, plus
`ValidateStage` in `grading`. 12 TODOs. Jun Sheng built the whole `ui`
package; Seth looks after it from here. Jun Sheng's fixers have
nothing to fix until the validators report anomalies, so start with
those. Until Damien's `WorkingCopyManager` works, test against a sample
zip unzipped by hand.

What to build, in order:

1. `InputValidator`: section code, username, readable file, readable
   folder, positive whole number. The brief takes up to 10% off for
   missing input validation, and the Set up step reports every
   chosen file as unreadable until `isReadableFile` and
   `isReadableDirectory` work.
2. `AbstractValidator.createAnomaly`, then the four validators
   (`StructureValidator`, `FileNameValidator`, `RequiredFilesValidator`,
   `HeaderNameValidator`) and `ValidationRunner.runAll`. Each validator
   returns anomalies and never throws for bad student input.
3. `ValidateStage`, which calls `ValidationRunner`.
4. Dummy submissions for the cases the samples do not cover: a missing
   `Q2` folder, a missing `Q1b.java`, a file named `q1a.java`, an empty
   zip, a file that is not a zip, and a `Q1a.java` holding
   `while (true) { }`. Share them with the team; they test everyone's
   stage, and the brief rewards extra edge cases. Done: the end-to-end
   set in `samples/e2e/` covers all six and many more (see its
   `README.md`). `python3 -I samples/e2e/make_e2e.py .` rebuilds it from
   the provided samples. None of its uploads stops the batch.
5. As each teammate finishes a service, open its screen in live mode
   (`sh run.sh`) and check that the real data looks right.
   `design/UI-INTEGRATION.md` says which screen shows which service;
   `design/UI-REVIEW.md` records the design and what was verified.
6. Make sure the scripts are run on Windows and on an Intel Mac, on
   your own laptop or a teammate's: `compile`, `run --check`,
   `run --selftest`, `run console` and `run --windowtest`. Record the
   results in the tested table above. The brief takes up to 50% off if
   the code does not run on the instructor's laptop.

Facts from the provided files:

- A correct zip holds one folder named after the username, with `Q1`,
  `Q2` and `Q3` inside. The required files are in `config.properties`
  (`Q1/Q1a.java`, `Q1/Q1b.java`, `Q2/Q2a.java`, `Q2/Q2b.java`,
  `Q3/Q3.java`).
- Each Java file starts with a comment holding `Name:` and `Email ID:`.
  Some students wrote only the username after `Email ID:`, others the
  full address.

You are done when `sh run.sh console` reports at least these anomalies:

| Student | Expected anomaly |
|---|---|
| chee.teo.2022 | `MISSING_WRAPPER_FOLDER`; `MISSING_HEADER_NAME` in `Q3.java` |
| david.2024 | `WRONG_FOLDER_NAME` (folder is `01400003`); `MISSING_HEADER_NAME` in `Q3.java` |
| fen.lai.2022 | `WRONG_FOLDER_NAME` (folder is `RenameToYourStudentID`) |
| jing.lim.2021 | `MISSING_WRAPPER_FOLDER`; `MISSING_HEADER_NAME` in all five required files |
| ping.lee.2023 | none |
| xing.yan.2023 | none |

The table covers the five required files only. Some zips also hold
extra files with no name in the header (`ShapeComparator.java`,
`ShapeComparators.java`); decide with the team whether those are checked.
None of the dummy submissions may stop the batch.

Rules that matter for the screens:

- Screens hold no grading logic. A screen calls `DashboardBackend`, and
  `LiveBackend` passes the call to one service method.
- Grading runs on a background thread. A `ProgressListener` is called on
  that thread, so every change to a control is wrapped in
  `Platform.runLater`.
- Every `javafx` import stays in the `ui` package and its subpackages.
- After any change to a screen, run `sh run.sh --check` and
  `sh run.sh --selftest`.

Example prompts:

```text
Implement gradezzy.util.InputValidator. No method may throw an exception
for any input, including null. List the edge cases you considered for
each method before writing code.
```

```text
Implement gradezzy.validation.StructureValidator. Unzip each sample in
samples/student-submission into a temporary folder and show me the top
level of each, so we agree on the cases, before you write code. Report
MISSING_WRAPPER_FOLDER, WRONG_FOLDER_NAME and MISSING_QUESTION_FOLDER
through createAnomaly. It must never throw for a badly formed submission.
```

### 3. Jun Sheng: corrections, flags and plagiarism

**Packages:** `fix`, `detection`, `plagiarism`, plus `FixStage` and
`DetectStage` in `grading` and the detector list in
`PipelineFactory.createDetectors`. **Done: 0 TODOs.**

How the fixes work:

- `FixStage` runs after the validators and calls `FixManager.applyAll`,
  unless `fix.auto.apply.enabled` is false. Each fixer whose `canFix`
  matches an anomaly fixes the working copy and returns a `FixRecord`, and
  the anomaly becomes `APPLIED`. Fixers run in the order they are
  registered: `EncodingFixer`, `FolderHierarchyFixer`, `FolderNameFixer`,
  `CaseNameFixer`, then `HeaderNameFixer`, so a file is converted where
  the problem found it, and the folders are in place before names and
  headers are fixed. A fix that fails is skipped and its anomaly stays
  for the instructor, with the reason from `getFailureReason`. An anomaly
  already fixed is not offered to a later fixer.
- After a fix that moved or renamed something, `FixStage` runs the file
  name, required file and header checks again on the working copy, adds
  only the problems that are new, and fixes those with
  `FixManager.applyTo`, which keeps the earlier fixes. So a blank header
  inside a folder that was one level too deep is still filled in.
- `FolderHierarchyFixer` moves everything visible at the top of the
  working copy (the Q folders and any other files) into a new username
  folder. It also fixes question folders one level too deep, such as a
  username folder inside the username folder. The k-th missing-folder
  anomaly belongs to the k-th question folder still missing, in question
  order (the order the validator reports them), so each anomaly moves its
  own folder up out of the one extra folder that holds question folders,
  correcting its letter case on the way. Other files and folders stay
  where they are, the extra folder is removed once empty, and a folder
  that is truly missing is left reported. `FolderNameFixer` renames the
  only top folder to the username, including a change of letter case
  only. Both are undone by moving back.
- `CaseNameFixer` renames a question folder or a required file whose name
  differs only in letter case, such as `q3` or `q1a.java`. Windows and
  macOS ignore case, so the rename goes through a temporary name. Without
  it a `q3` folder scores 0 on that question.
- Every undo finds its folder or file as the working copy is now, so the
  fixes of one student can be undone in any order.
- Before the fixes, `FixStage` reads every `.java` file in the working
  copy (hidden folders such as `.gradezzy-backup` aside) and adds a
  `WRONG_ENCODING` warning for each one that starts with a UTF-8 byte
  order mark (Notepad's "UTF-8 with BOM") or is not valid UTF-8 (checked
  with a `CharsetDecoder` set to `REPORT`). javac rejects both, so the
  question would score 0. The warning is added even when
  `fix.auto.apply.enabled` is false, so the instructor sees why. Its
  message starts with the file's path in the working copy, such as
  `ping.lee.2023/Q1/Q1a.java`, and `EncodingFixer` finds the file by
  taking the fixed ending off the message. `EncodingFixer` backs the file
  up, removes the byte order mark, and rewrites a file that is still not
  UTF-8 as UTF-8, reading it as Windows-1252. Undo copies the original
  bytes back; if a folder fix moved the file since, it finds the file of
  that name that still holds what the fix wrote.
- `HeaderNameFixer` finds the file named at the start of the header
  anomaly's message, such as `Q1/Q1a.java`, among the configured answer
  files. It copies the file into the hidden `.gradezzy-backup` folder of
  the working copy, then writes the roster name and email after the blank
  `Name:` and `Email ID:` labels. A file that is not UTF-8 (such as one
  saved in Windows-1252) is read and written byte for byte as
  ISO-8859-1. Undo restores the copy. If the student is not on the
  roster, or the header cannot be completed (for example it has no
  `Name:` line), the file is left unchanged and the reason is kept.
- `FixManager` is shared by the grading threads, so its public methods
  are `synchronized`. `findRecord(anomaly)` returns the fix for an
  anomaly, and `getFailureReason(anomaly)` why a fix could not be made.
  `approve` turns an applied fix into `APPROVED`; `undo` reverses an
  applied or approved fix and sets `UNDONE`.

How the flags work (never a change of mark):

- `UntouchedStarterDetector` compares each answer file with the starter
  file, ignoring the header comment, blank lines and line endings. If
  every file is unchanged it raises one flag for the whole submission (the
  original resource folder was submitted); otherwise one flag per
  unchanged file.
- `EmptyAnswerDetector` flags an answer method whose body, without
  comments and spaces, is still the starter's body. Every overload of the
  method is checked. A wholly unchanged file is left to the untouched
  check when that check is on.
- `HardCodedAnswerDetector` flags an answer method with 2 or more `if` or
  `case` branches that compare a value with a fixed literal, such as
  `s.equals("cat")` or `s.contains("cat")`. Common values (0, 1 and -1),
  separators with no letter or digit (such as `","` or `""`) and a
  number inside a calculation (the `2` in `i % 2 == 0`) do not count.
  Every overload is checked, and so is a method with an annotation. It
  can still flag honest code with many such checks, and misses answers
  hidden in arrays or maps.
- The starter folder is the one chosen on the Set up step, or
  `paths.starter.dir` when none is chosen.
- `DetectStage` runs each detector that is switched on
  (`detection.*.enabled`). If one fails, it adds a warning saying the
  check could not run; it never fails a submission.

How the similarity check works: `plagiarism.algorithm` picks the
detector in `ApplicationContext`. `WinnowingSimilarityDetector`
(`plagiarism.algorithm=winnowing`) splits each answer into tokens, ignoring spacing and comments, turns
every name into `ID` and every text literal into `STR`, so renaming
changes nothing, and hashes every run of 5 such tokens. Winnowing
(`Winnowing.selectFingerprints`) keeps the smallest hash of every 4 in a
row as a fingerprint, so a shared run of 8 tokens is found wherever it
is. Fingerprints that are also in the starter file are removed, and two
students are compared with the Jaccard measure (shared fingerprints
divided by all fingerprints). A pair is only compared when both answers
have at least 25 fingerprints of their own (`MIN_OWN_FINGERPRINTS`):
short correct answers look alike even when written separately. The
first version and the default, `TokenSimilarityDetector`
(`plagiarism.algorithm=trigrams`), compares runs of three tokens as written instead, with at least 50 of
them (`MIN_OWN_TRIGRAMS`), so renaming variables lowers its score. Pairs
at or above `plagiarism.similarity.threshold` are reported. The starter
code comes from the run's starter folder, or from `paths.starter.dir`
when there is none. README "Similar code check" compares the two.

Checked on the six samples: chee.teo and jing.lim get a username folder;
david's and fen.lai's folders are renamed; the blank headers (Q3 for
chee.teo and david, all five files for jing.lim) are filled in; every fix
shows `APPLIED`; undoing every fix restores each working copy byte for
byte; the totals and the original zips are unchanged.

### 4. Zach: compiling, running and reading scores

**Packages:** `exec`, `testcase`, plus `InstallTestersStage`,
`CompileStage`, `ExecuteStage` and `TesterOutputScoreParser` in
`grading`. **Done: 0 TODOs.** This is where "safely terminate programs
that exceed the time limit" lives, which the brief lists as a core
requirement.

What to build, in order:

1. `ProcessRunner.run` and `terminate`: start a command with
   `ProcessBuilder` in a given folder, capture standard output and error,
   wait up to the time limit, and kill the process (and its children) if
   it is still running. Cap the captured output at
   `exec.max.output.characters`, because a student loop can print forever.
2. `SourceCompiler.compile` and `TesterRunner.runTester`: build the
   `javac` and `java` commands from the configuration.
3. `UploadedTesterSource.prepareTesters`: match `Q1aTester.java` and the
   others in the testers folder to the questions.
4. `TesterInstaller`: copy each tester into the question folder of the
   working copy, plus the data files in `assessment.tester.data.files`.
5. `InstallTestersStage`, `CompileStage`, `ExecuteStage`.
6. `TesterOutputScoreParser.parse`.
7. `TypedTestCaseSource` and `TesterGenerator` (SHOULD tier): turn test
   cases typed into the app into a tester file in the same output format.

Facts from the provided files:

- Each tester `extends` the student's class and has no package, so it
  must be compiled and run with the question folder as the working
  directory (`javac Q1aTester.java`, then `java Q1aTester`, inside `Q1`).
- The Q2 testers open `personstester.txt` and `studentstester.txt` by
  relative name. The instructor supplies them in the tester folder
  (`Tester-Files` has them). A student's own copy is deleted before
  grading, so an edited data file cannot change a mark.
- `Q2` needs the provided `DataException.class`, and `Q3` needs
  `Shape.class`, `Circle.class` and `Rectangle.class`, in the folder.
- For each test case a tester prints `Passed` or a line starting with
  `Failed`. The last line of output is the total as a decimal, such as
  `3.0`. An exception inside a test prints a stack trace to standard
  error and the tester carries on.
- Maximum scores are 3, 3, 5, 5 and 4. They are not a setting: each
  question's maximum is the sum of the `score += <number>` lines in its
  tester (`testcase.TesterMarks`), read from the batch's tester folder.
  Comments and quoted text are ignored, and every `score +=` on a line
  counts. Start grading is stopped, with the reason, when a question has
  no tester, its tester never adds to the score, or it changes the score
  in a way that cannot be counted (`score = score + 1`, `score++`,
  `score += MARK`, or a `score +=` inside a loop). Set up then asks the
  instructor to confirm the marks, warning when they do not add up to the
  score sheet's `Calculated Final Grade Denominator`; the console prints
  the same marks and warning. Old `max.score` lines in a personal
  `config.properties` are ignored, and the console says so.

Rules that matter here:

- One tester, one process. A hanging tester must not block the next one.
- If a question does not compile, record `COMPILE_ERROR`, give that
  question 0 and continue with the other questions.
- On a timeout, record `TIMEOUT` and give the number of `Passed` lines
  printed before the process was stopped.

You are done when ping.lee.2023 scores 3, 3, 5, 5, 4, and a dummy
submission whose `Q1a.java` contains `while (true) { }` ends with a
`TIMEOUT` anomaly after about `exec.run.timeout.seconds`, with no `java`
process left running afterwards.

Example prompts:

```text
Implement gradezzy.exec.ProcessRunner.run and terminate with
ProcessBuilder. Requirements: run in a given working directory, capture
stdout and stderr without the process blocking when its output buffer
fills, stop waiting after timeoutSeconds and kill the process and its
descendants, cap output at MAX_OUTPUT_CHARACTERS. Explain how you avoid
the deadlock that happens when nobody reads the output. Then prove it
with a tiny Java program that loops forever.
```

```text
Read all five files in samples/Tester-Files and tell me exactly what each
prints for a pass, a fail, an exception and the final score. Then
implement gradezzy.grading.TesterOutputScoreParser to match, using
PASS_MARKER and FAIL_MARKER from ConfigKeys. Handle a run that timed out
before the score line was printed.
```

```text
Implement gradezzy.grading.CompileStage. A question that fails to compile
gets a COMPILE_ERROR anomaly with the first compiler message, and the
other questions must still be compiled. Do not throw GradingException for
a student's compile error; explain when throwing is appropriate.
```

### 5. Jing Yuan: scoring, services and putting it together

**Packages:** `app`, `config`, `model`, `service`, plus `ScoreStage` and
`ScoreCalculator` in `grading`. All 39 TODOs are done. You
also own the wiring (`PipelineFactory`, `ApplicationContext`), so you
are the person who checks that the slices fit together.

What to build, in order:

1. The three small model methods: `Assessment.findQuestion`,
   `Assessment.getMaxTotal` and `Submission.getTotalScore`.
2. `ScoreCalculator` and `ScoreStage`. A score can never exceed the
   question's maximum.
3. In `GradingService.startGrading`: read the roster into the context and
   save the run before grading starts.
4. The fraction done and the time left, now worked out on the Grade
   step (`ui.flow.GradeScreen`).
5. `ExportService`: find an exporter by name and write to the output
   folder.
6. `AnomalyService`, `StatisticsService`,
   `AuditService`, `SettingsService`, `HistoryService`,
   `PlagiarismService`. Most of these are thin: validate the input, call
   one class from another package, record an audit entry.
7. `GradingService.retryFailed` and `resume` (after Huxley's repository
   works).
8. Grading several submissions at once using `exec.thread.count`. Do this
   last, and only once the single-threaded run gives the right totals.
   Threads are not taught in CS102, so keep the code small and be ready to
   explain it.
9. `ConsoleMain`: reject an invalid section code with a clear message.

You are done when `sh run.sh console` prints the table of totals at the
top of this section, Cancel stops a run after the current submission, and
Retry grades only the failed submissions again.

Example prompts:

```text
Implement gradezzy.grading.ScoreCalculator and ScoreStage, and the TODO
methods in gradezzy.model.Assessment and Submission. A question score is
never above Question.getMaxScore(). Then run "sh run.sh console" and
compare the totals with samples/groundtruth.txt; list every difference
and which stage you think causes it, without changing other packages.
```

```text
Implement gradezzy.service.SettingsService. validate() returns an empty
string for a good value and a message for a bad one. Go through every key
in config.properties and tell me what rule each should have (positive
whole number, existing folder, true or false, and so on) before coding.
save() must keep the comments in config.properties.
```

```text
I want to grade THREAD_COUNT submissions at the same time in
GradingService. Explain two ways to do this in Java, with the risks of
each (shared lists, progress events arriving out of order, cancelling).
Recommend the simplest one that I can explain to an instructor. Do not
write code until I pick.
```

### 6. Huxley: exports, statistics and saved state

**Packages:** `report`, `stats`, `persistence`, plus `PersistStage` in
`grading` and the TODO in `ApplicationContext` that opens the database.
All 28 TODOs are done. Every run is saved to SQLite through
`PersistStage` and `GradingService`, so History can list it after a
restart once `HistoryService` reads the repository.

What to build, in order:

1. `CsvScoreSheetExporter`: the one export the brief requires. Copy the
   provided score sheet and fill in `Calculated Final Grade Numerator`.
2. `JsonWriter` and `JsonExporter`: per-question scores, test case
   results and anomalies, with no library.
3. `AnomalyReportExporter`, `StudentReportExporter`, `AuditLogExporter`.
4. `StatisticsCalculator`: mean, median, pass rate, mark bands, pass rate
   per question, most failed test, common compile errors, filtered by
   module and section.
5. `SqliteResultRepository`: create the tables, then save and load runs
   (with the module and section of each run), submissions, anomalies,
   fix status and audit entries.
6. `PersistStage` and the `repository.open()` call in
   `ApplicationContext`.
7. `BackupManager` (COULD tier).

Rules that matter here:

- The CSV export must match the provided file exactly apart from the
  numerator: same columns in the same order, `#` in front of the id and
  username, `#` in the last column, Windows line endings. Compare your
  output with the original using a diff tool.
- Decide with the team how totals are written (`12` or `12.0`) and how a
  student with no submission appears (blank or `0`). The provided file
  does not say; do not guess silently.
- Only `persistence` may contain SQL or import `java.sql`:
  `SqliteResultRepository` owns the connection and the transactions, and
  its package-private table classes hold the SQL. Always use
  `PreparedStatement` with `?` placeholders, never text joined into SQL.
- A failed save must not lose the grading results in memory.

You are done when the exported CSV differs from
`samples/IS442-ScoreSheet.csv` only in the numerator column; the JSON
opens in a JSON viewer without errors; mean and median for the six sample
totals (12, 10, 10, 0, 20, 15) are 11.17 and 11; and after closing and
reopening the application, History lists the earlier run with the same
scores.

Example prompts:

```text
Implement gradezzy.report.CsvScoreSheetExporter. First print
samples/IS442-ScoreSheet.csv with its invisible characters shown, and
tell me its line ending, its columns and which values carry a # prefix.
The export must be identical to that file except that Calculated Final
Grade Numerator is filled in. Use CSV_LINE_ENDING, CSV_ID_PREFIX and
CSV_END_OF_LINE_MARKER from ConfigKeys. Afterwards show me a diff between
the original and your output.
```

```text
Design the SQLite tables for gradezzy.persistence.SqliteResultRepository:
runs, submissions, question results, test case results, anomalies and
audit entries. Show me the CREATE TABLE statements and explain each
relationship before implementing anything. JDBC is not taught in CS102,
so explain Connection, PreparedStatement and ResultSet in two sentences
each, and keep all SQL inside this one class.
```

```text
Implement gradezzy.stats.StatisticsCalculator.mean, median, passRate and
markBands with plain loops (no streams). Handle an empty list without
dividing by zero. Show the results for the scores 12, 10, 10, 0, 20, 15
out of 20 and check them by hand.
```

### Who is waiting on whom

| If you are | You need first | From |
|---|---|---|
| Seth | A working copy to check; `FileUtils` and `TextUtils` | Damien |
| Jun Sheng | Anomalies from the validators; the student on the submission | Seth; Damien |
| Zach | A working copy to compile in | Damien |
| Jing Yuan | Question results from `ExecuteStage` | Zach |
| Huxley | Nothing to start; real runs later | Jing Yuan |

Nobody has to sit idle: every class can be written and tried against a
folder unzipped by hand or a `GradingRun` filled in by hand.

## Libraries

All are free and open source, as the brief requires, and were downloaded
from Maven Central.

| Library | Version | Licence | Why it was chosen |
|---|---|---|---|
| JavaFX (`javafx-base`, `-graphics`, `-controls`, `-fxml`) | 25.0.4 | GPL v2 with Classpath Exception | The desktop window. It matches JDK 25 and has tables, shapes for the charts, file pickers and drag and drop. The screens are plain JavaFX code; `javafx-fxml` stays on the module path but no screen uses FXML any more. |
| AtlantaFX (`atlantafx-base`) | 3.0.0 | MIT | Modern themes for JavaFX. Its Cupertino Light and Dark themes give light and dark mode with one line of code. |
| ControlsFX (`controlsfx`) | 11.2.5 | BSD 3-Clause | Extra controls JavaFX lacks. It was used by the earlier window's search fields; the current window does not use it, and it stays on the classpath with its run flags. |
| SQLite JDBC (`sqlite-jdbc`) | 3.53.4.0 | Apache 2.0 | A database in a single file with no server to install, for saved state, history, resume and backup. |

Notes:

- The JavaFX jars differ per platform, so there are three copies, in
  `lib/javafx/mac`, `lib/javafx/mac-aarch64` and `lib/javafx/win`. The
  scripts choose one.
- JSON export uses a small hand-written `JsonWriter` instead of a library,
  so there is one jar fewer and every line can be explained.
- No Excel library is included. Exports are CSV and JSON only.
- The run scripts pass the five `--add-exports` and `--add-opens` flags
  that the ControlsFX documentation lists for its controls, and
  `--enable-native-access` for JavaFX and the SQLite driver.

### Java beyond the CS102 syllabus

Every team member must be able to explain every line, so the code uses the
Java taught in CS102 (weeks 1 to 11). Two kinds of untaught Java are
treated differently:

- **Language features** that were not taught are not used. They have
  been rewritten into taught Java; the rules are under "Java taught in
  CS102" in the coding rules below.
- **Library classes** with no taught substitute are kept, each confined to
  the classes named here. The last column is a one-sentence explanation
  for the presentation.

`ui.check` (the developer checks) was left as it was and is not covered
by this section.

| Construct | Where | Why | How to explain it |
|---|---|---|---|
| JavaFX | `ui` package only | The window. | A library of window parts (buttons, tables, charts) that we arrange in code. |
| One-line lambdas `event -> exportResults()` | 26 files in `ui` | Button handlers, table cells and callbacks. | Short for a small class that implements the handler interface (like `AgeComparator implements Comparator`) and whose one method calls `exportResults()`. Every lambda is one line that calls one method. |
| `Runnable`, `Consumer` | `ui.flow` (`ActionBar`, `Parts`, `StepBar`, `TabBar`, ...), `exec.TrustedFrameCheck` | The type of a lambda parameter, for example "what to do when this button is pressed". | Interfaces with one method, so a screen can pass "run this later" to another class. |
| `java.nio.file` (`Path`, `Paths`, `Files`, `DirectoryStream`, `StandardCopyOption`, `LinkOption`) | About 60 files | Every file and folder the system reads, copies, moves or writes. | `Path` is a file or folder location (like `File`); `Files` has static methods to read, copy, move and list them, and can refuse to follow links. |
| `javafx.concurrent.Task`, `new Thread`, `Platform.runLater` | `ui.GradingTask`, `ui.backend.LiveBackend`, `ui.flow.GradeScreen`, `ui.flow.SimilarityCheck`, `ui.flow.FileInspection`, `ui.flow.ExportJob`, `ui.flow.TesterCheck`, `ui.flow.MissingStudentsLookup`, `ui.flow.UndoFixJob`, `ui.flow.RegradeJob`, `ui.flow.SetUpScreen`, `ui.flow.ExportScreen`, `ui.flow.FlowWindow`, `ui.flow.StudentPanel`, `ui.flow.RunsScreen`, `ui.flow.CodeSheet`, `ui.flow.HomeScreen`, `ui.flow.ScoreSheetSearch`, `ui.flow.ScoreSheetChange` | Grading (with the similar code check at the end of `GradingTask`), checking chosen files, the class files and the tester folder, reading the score sheet for students with no submission, grading a question again, grading one student, one question or the ticked students again, exports, backups, and undoing a fix or applying a rejected one again (each grades the student again) run off the window's thread so the window does not freeze. | The slow work runs on a second thread; `Platform.runLater` hands each screen update back to the window's thread. |
| `ConcurrentHashMap` | `ui.backend.LiveBackend` | The last run of each section, which a restore clears from a background thread, and the review decisions on flags. | A `HashMap` that two threads can use at the same time. |
| `javafx.animation` (`PauseTransition`, `Timeline`) | `ui.flow.FlowWindow`, `ui.flow.ReviewScreen`, `ui.demo.DemoGrader` | How long a notice stays, the pause before a code search, the demo grader's pace. | Timers that call a method after a delay. |
| Parallel grading: `Thread`, `Runnable`, `synchronized` | `grading.GradingPipeline`, `GradingWorker`, `SubmissionHandout`, `SynchronizedProgressListener`, `CancellationToken`, `fix.FixManager`, `persistence.SqliteResultRepository`, `service.HistoryService`, `service.GradingService`, `app.ConsoleProgressListener`, `exec.TrustedPrintStream` | Grades `exec.thread.count` submissions at the same time. | Each `GradingWorker` takes the next submission from the `SubmissionHandout`; `synchronized` lets only one thread at a time touch shared data. |
| `CharsetDecoder`, `CodingErrorAction`, `ByteBuffer` | `fix.EncodingFixer`, `report.CsvScoreSheetExporter` | Tells whether a Java file is valid UTF-8, which javac needs, and whether a score sheet is UTF-8 or Windows-1252, so the export is written the same way. | A decoder turns bytes into characters; set to `REPORT`, it throws instead of guessing when the bytes are not UTF-8. |
| `java.util.Properties` | `config.AppConfig` | Reads `config.properties`. | Reads a file of `key=value` lines into a lookup table. |
| JDBC (`Connection`, `PreparedStatement`, `ResultSet`) | `persistence` only: `SqliteResultRepository` and its table classes (`SqliteSchema`, `RunTables`, `SubmissionTables`, `ReviewTables`, `AdjustmentTable`, `AuditTable`, `JdbcSupport`) | Saves runs, review decisions, checked 0s and changed marks to SQLite so History can list them after a restart. | `Connection` is the open database, `PreparedStatement` is one SQL command with `?` blanks we fill in, `ResultSet` is the rows that come back; each is closed in a `finally` block. |
| `Thread.sleep`, `InterruptedException` | `util.FileUtils` | Waits briefly before retrying a move or delete that Windows blocked, such as when antivirus holds a new file open. | Pauses for half a second, then tries again, up to five times. |
| `Files.getFileStore`, `Files.setAttribute(path, "dos:readonly", false)` | `util.FileUtils` | A read-only file cannot be deleted on Windows, so the flag is cleared before a delete is tried again. Elsewhere a failed delete is reported at once. | Asks which kind of disk a file is on, and on a Windows disk switches off the file's read-only tick box. |
| `ProcessBuilder`, `Process`, `ProcessHandle`, `Thread.sleep` | `exec.ProcessRunner` | Runs `javac` and `java` as separate programs and stops a run and its child programs at the time limit. | Starts another program like typing a command in the terminal; we check every 50 milliseconds whether it has finished or run out of time. |
| `ProcessHandle.onExit().get` with `TimeUnit`, `TimeoutException`, `ExecutionException` | `exec.ProcessRunner` | On Windows a stopped `java.exe` holds its folder for a moment, so the runner waits up to 1 second (shared by all the children it stopped) for it to end. | `onExit()` is a result that arrives when the program ends; `get` waits for it, but gives up after the time given. |
| `default` methods in an interface | `ui.backend.DashboardBackend` | A few methods, such as `getScoreSheet` and `setZeroChecked`, work the same in the live and demo backends, so they are written once in the interface. | An interface method with a body: each class that implements the interface gets it unless it writes its own. |
| `InputStream.available()` | `exec.ProcessRunner` | Tells whether a running program has output waiting, so the runner reads it at once instead of pausing first. | Asks a stream how many bytes can be read right now without waiting; 0 means nothing has arrived yet. |
| Shutdown hook (`Runtime.addShutdownHook`), a daemon `Thread` | `exec.ProcessRunner`, `exec.RunningProcesses`, `exec.TesterHarness`, `exec.HarnessWatchdog` | No tester keeps running after GradeZZY closes or crashes. | A shutdown hook is a thread Java starts as the program ends; ours stops every tester still running. The watchdog is a second thread inside each tester run that sleeps until the time limit plus 2 seconds, then ends the run, in case GradeZZY is gone. |
| `PrintStream` subclass, `System.setOut`, `StackWalker` | `exec.TesterHarness`, `exec.TrustedPrintStream`, `exec.TrustedFrameCheck` | Marks only the lines the tester itself prints, so a student printing `Passed` is not believed. | All printing goes through our `PrintStream`, which checks who called it and marks a line only when no student method is on the call stack. |
| `Throwable.setStackTrace`, an overridden `toString` | `exec.SkippedCaseException` | The provided testers print the stack trace of any exception in a case, so a case GradeZZY skips prints one plain line instead of a GradeZZY stack trace. | Our exception keeps an empty list of call stack lines and prints as its message alone. |
| Varargs `format(String, Object...)` | `exec.TrustedPrintStream` only | It overrides `PrintStream.format`, whose signature Java fixes. | `printf` calls this method, so ours must have exactly the same parameters to catch `printf` output too. |
| Reflection (`Class.forName`, `Method.invoke`, `Class<?>`) | `exec.TesterHarness`, `exec.TrustedPrintStream`, `exec.TrustedFrameCheck` | Starts the tester's `main` from inside the harness. | Loads a class by its name at run time and calls its `main`, the way `java Q1aTester` would. |
| `Runtime.halt`, the `jdk.lang.Process.launchMechanism` property | `exec.TesterHarness` | Ends the run without waiting for student threads, and stops student code starting other programs (macOS and Linux). | A hard stop for the tester's program, and a switch that blocks it from launching others. |
| `java` options `--limit-modules`, `--illegal-native-access=deny`; `UUID` | `exec.TesterRunner` (`UUID` also for anomaly ids) | Student code gets only core Java and no native code; a new random token for every tester run. | Command-line options that shrink what student code can reach; a `UUID` is a random id nobody can guess. |
| `java.util.zip` (`ZipFile`, `ZipEntry`, `Enumeration`, `CRC32`) | `ingest.ZipExtractor` | Unzips submissions safely. | Opens a zip and hands us its entries one at a time, like an `Iterator`. |
| `java.util.regex.Pattern`, `matches`, `replaceFirst` | `service.SettingsService`, `ingest.SubmissionLoader`, `ingest.ZipExtractor.isSafeEntry` | The zip name setting is itself a pattern; unsafe Windows file names are a pattern too. | A pattern describes a family of strings, such as "2023-2024-" followed by a username. |
| `split("\\R")` | `grading.CompileStage`, `grading.TesterOutputScoreParser`, `exec.TesterRunner`, `exec.TrustedPrintStream` | Splits program output into lines whatever the line ending. | `split` as taught; `\R` means "any kind of line break". |
| `StandardCharsets.ISO_8859_1` | `testcase.TesterMarks` | Opens a tester file whatever encoding it was saved in, to read its `score +=` lines. | A way of turning bytes into characters that accepts every byte, so a tester saved in Windows-1252 still opens. |
| `StandardCharsets.UTF_16LE`, `UTF_16BE` | `util.TextUtils` (used by `fix.EncodingFixer` and `validation.HeaderNameValidator`) | Reads a source file Notepad saved as "Unicode" (UTF-16), so it can be saved again as UTF-8 and compiled. | Names for the two byte orders of UTF-16; the first two bytes of the file (its byte order mark) say which one it is. |
| `Collections.reverseOrder()` | `testcase.TesterMarks` | Sorts a tester's case marks from largest to smallest, to find the most a number of passed cases can be worth when its cases cannot be matched to its marks one for one. | `Collections.sort` as taught, given a comparator that puts the larger number first. |
| `java.time` (`LocalDateTime`, `DateTimeFormatter`) | `model`, `persistence`, `report.AuditLogExporter`, `service`, `ui.demo` | Run and audit times. | The current date and time, and how to print it. |
| `java.util.Random` | `ui.demo.DemoData` | Synthetic marks for `--demo`, always from the same seed. | Gives the same "random" numbers every time, so the demo looks the same. |

`String.format` is used for numbers such as `%.1f`; it takes the same
format codes as `printf` and returns the text instead of printing it.

## Coding rules

Follow `docs/CS102_Coding_Style.pdf` and the Oracle Java Code Conventions.
In short:

- **Lines:** at most 80 characters. Break after a comma or before an
  operator, and align the next line or indent it 8 spaces.
- **Indentation:** 4 spaces, never tabs. K&R braces. Braces on every
  block, even a single statement.
- **Statements:** one per line. One declaration per line, always
  initialised, declared close to where it is first used.
- **Names:** `lowerCamelCase` for variables and methods, `UpperCamelCase`
  for classes, `UPPER_SNAKE_CASE` for constants, lowercase package names.
  Meaningful names; no one-character names except loop counters; no
  unclear abbreviations.
- **No magic numbers:** use a named constant or a setting from
  `config.properties`.
- **Spacing:** spaces around binary operators, none after a unary
  operator. Write `if (condition) {` with those spaces.
- **Conditions:** use `if` / `else if` / `else` instead of nested ifs. Do
  not write `== true` or `== false`. No empty branches and no code that
  does nothing.
- **Loops:** no `break` or `continue` inside an infinite loop, no
  unrelated statements in a `for` header, and no `if` inside a loop just
  to treat the first or last round differently.
- **Methods:** return a boolean expression directly. Check for invalid
  values first and return early.
- **Blank lines:** one between logical chunks of code.

Team rules on top of that:

- A header comment at the top of every file, Javadoc on every public
  class, interface and method, and a short comment wherever the intent is
  not obvious.
- No emojis and no em dashes anywhere, including commit messages.
- Keep `javafx` imports inside the `ui` package.
- Read settings through `AppConfig`; do not hard-code a value in logic.

### Java taught in CS102

Use the Java taught in CS102. These language features were not taught, so
they are written the taught way instead:

| Instead of | Write |
|---|---|
| `a ? b : c` | `if` / `else` |
| A nested, inner or anonymous class | A top-level class in its own file, given what it needs through its constructor (for example `ReviewMarkCell`, `GradingWorker`) |
| `try (Resource r = ...)` | Declare it `null` before `try`, open it inside, close it in `finally` (`FileUtils.closeIfOpen`) |
| `catch (A \| B e)` | Two `catch` blocks |
| `Type... values` | An array parameter; callers pass `new Node[] {a, b}` |
| `StringBuilder`, `String.join` | `+` / `+=`, or `TextUtils.join`; a `char[]` when building long text one character at a time |
| `List.of`, `Map.of` | `new ArrayList<>()` and `add` |
| `entrySet` | A loop over `keySet()` with `get` |
| `LinkedHashMap` | An `ArrayList` of keys beside a `HashMap` (for example `report.JsonFields`) |
| Streams, method references `X::y` | Loops; an `Iterator` when a JDK method returns a stream |
| Lambdas outside `ui` | A named class that implements the interface, like week 5's `AgeComparator` |
| A lambda in `ui` with more than one method call | `event -> namedMethod()`, with the logic in a private method |

Anything else that was not taught must be a library from the table in
"Java beyond the CS102 syllabus" above. Add a row there before using a new
one.

## Feature checklist

`[ ]` means a place exists in the skeleton but the feature is not
implemented. `[x]` means it works now.

### MUST (brief section 4)

- [x] Batch upload of zips or many files (`ingest.SubmissionLoader`,
  `ui.flow.SetUpScreen`)
- [x] Batch grading loop with no per-submission clicking
  (`grading.GradingPipeline`, `service.GradingService`)
- [x] Unzip submissions (`ingest.ZipExtractor`,
  `ingest.WorkingCopyManager`)
- [x] Validate structure, file names and required files
  (`validation.StructureValidator`, `FileNameValidator`,
  `RequiredFilesValidator`)
- [x] Check student names in file headers
  (`validation.HeaderNameValidator`)
- [x] A failing submission is recorded as an anomaly and does not stop
  the batch (`grading.GradingPipeline`)
- [x] Auto-correct folder names, folder hierarchy and missing header
  names on a working copy (`fix.FolderNameFixer`,
  `FolderHierarchyFixer`, `HeaderNameFixer`, `FixManager`)
- [x] Compile and run with testers (`exec.SourceCompiler`,
  `exec.TesterRunner`, `testcase.TesterInstaller`)
- [x] Stop programs that exceed the time limit (`exec.ProcessRunner`)
- [x] Per-question and per-student scores
  (`grading.TesterOutputScoreParser`, `ScoreCalculator`)
- [x] Export in the provided CSV format (`report.CsvScoreSheetExporter`)
- [x] Separate classes for validation, compilation, execution, grading
  and reporting (the package structure)
- [x] Configuration in an external file (`config.properties`,
  `config.AppConfig`)
- [x] Screens that show progress, errors and results
  (`ui.flow.GradeScreen`, `ReviewScreen`, `StatisticsScreen`)
- [x] Input validation everywhere: chosen files and folders
  (`util.InputValidator` through `LiveBackend`), each setting
  (`SettingsService.validate`), changed marks (0 to the question's
  maximum, with a reason), typed test cases (one line each) and the
  console's module and section

### SHOULD

- [x] Anomaly log with action buttons; approve and undo, singly and in
  bulk (`service.AnomalyService`, `ui.flow.ReviewScreen`; undo needs
  the run graded in this session, because fix records are kept in
  memory). Undo grades the student again without that fix, so the marks
  match the files as submitted (kenneth.yeo.2024 in the e2e cohort goes
  from 20 to 16); the undone fix keys stay on `Submission`, so Retry and
  Resume skip them too, also after a restart, because the database
  saves them (column `submissions.rejected_fixes`). "Apply fix again"
  (`AnomalyService.reapplyFix`) grades the student again with the fix
  ("Fix applied again. Marks 16 to 20."); the window offers it for a
  fix rejected since GradeZZY was opened, or in a run resumed since
  (`canReapplyFix`)
- [x] Flag untouched starter files, empty answers and hard-coded answers
  (`detection` package)
- [x] Set up checks that the score sheet, tester folder and starter
  folder belong with the uploads, and offers a better one nearby with
  "Use this file instead" (`service.ClassFileMatcher`,
  `ui.flow.ClassFileMatchCheck`). A score sheet fits when at least half
  the uploads are on it; testers and starter files fit when they use the
  methods the students' files declare. It only warns: grading can start
- [x] Export warns when graded students are not on the score sheet.
  When most of them are not, it offers a sheet nearby that lists them
  with "Use this file instead", which matches the students to that sheet
  and saves the run without grading again
  (`GradingService.changeScoreSheet`, `ui.flow.ScoreSheetSearch`,
  `ui.flow.ScoreSheetChange`); otherwise it points back to Set up
- [x] Test cases typed into the app (`testcase.TypedTestCaseSource`,
  `TesterGenerator`, `ui.flow.TestCasesSheet`): each case is worth 1
  mark, and the generated testers are kept in
  `workspace/output/generated-testers`
- [x] Progress bar and estimated time of completion
  (`ui.flow.GradeScreen`)
- [x] Statistics dashboard filtered by module and section (`stats`
  package, `service.StatisticsService`, `ui.flow.StatisticsScreen`)
- [x] Search, filter and sort (`ui.flow.ReviewScreen`, by student name)
- [x] Select one, several or all (`ui.flow.ReviewScreen`)
- [x] Next, Back and Cancel (`ui.flow.ActionBar`)
- [x] Cancel, retry and resume grading (`service.GradingService`,
  `grading.CancellationToken`; Cancel stops each student being graded
  at its next step; a run from an earlier session is resumed with its
  own score sheet, tester and starter folders; a run saved before the
  database kept the folders uses the ones in the settings, with a
  warning on every student graded that way)
- [x] A late batch joins the section's run (`GradingService.findNewestRun`
  and `addToRun`): a new student is added, a student graded before is
  graded again and the new result replaces the old one, with the old
  total kept in the audit log. It is refused when the tester folder or
  score sheet is not the run's. Set up asks whether to add the students
  to that run (`ui.flow.SetUpScreen`)
- [x] Light and dark theme switch (`ui.ThemeManager`, `ui.theme`)
- [x] Settings page backed by `config.properties`
  (`service.SettingsService`, `ui.flow.SettingsScreen`)
- [x] SQLite persistence behind `ResultRepository`
  (`persistence.SqliteResultRepository`); review decisions and checked
  0s are saved with the run too, so they are there after a restart
  (`AnomalyService.saveDecision`, `saveZeroCheck`)
- [x] Change a question's mark by hand, with a reason, and undo it
  (`service.ScoreAdjustmentService`, `ui.flow.AdjustMarkSheet`): a mark
  outside 0 to the question's maximum or a blank reason is refused, only
  the newest change of a question can be undone, changes are saved and
  shown in the export. Grading the student or the question again from
  Review keeps them (`ScoreAdjustmentService.keepAfterRegrade`); undoing
  a fix, which grades the student again, still drops them
- [x] Grade one student, one question, or the ticked students again from
  Review (`GradingService.regradeSubmission`, `regradeQuestion`,
  `ui.flow.RegradeJob`). One question is compiled and run again in the
  student's working copy, and only its result, its compile or tester
  problems and the total change; other marks, fixes and flags stay.
  "Grade N ticked students again" asks once, runs one background job
  with a progress bar and ends with a summary of whose totals changed.
  A refused upload stays refused, a mark set by hand is kept, and each
  student gets a line in the activity log with the old and new marks
- [x] Audit log, exportable (`service.AuditService`,
  `report.AuditLogExporter`; the log records grading started, cancelled,
  retried and resumed, students and questions graded again with their
  old and new marks, fixes approved and undone, settings saved,
  backups made and restored, and exports; History shows it, and Export
  saves it with "Also save the activity log")
- [x] Individual student report, exportable
  (`report.StudentReportExporter`; Export saves one text file with a
  report on each chosen student with "Also save a report on each
  student"; choose one student for a single report)
- [x] JSON export (`report.JsonExporter`, `JsonWriter`)
- [x] Nielsen's 10 usability heuristics, reviewed screen by screen:
  https://www.nngroup.com/articles/ten-usability-heuristics/. The review
  and its gaps are in `design/NIELSEN-REVIEW.md`

### COULD

- [x] Backup and recovery of a previous grading state
  (`persistence.BackupManager`, `service.HistoryService`)
- [x] Subtle animations (`ui`, `ui.animations.enabled`)
- [x] A report of the problems found, one row per problem
  (`report.AnomalyReportExporter`; Export saves it with "Also save a
  list of the problems found")
- [x] Plagiarism detection (`plagiarism` package,
  `service.PlagiarismService`; runs when `plagiarism.enabled` is true,
  straight after grading, in the console and the window;
  `PlagiarismService.listPairs` reads the pairs back from the flags)

## Before submission

1. Run `sh clean.sh` (or `clean.bat`) so `classes/` is empty.
2. Remove `docs/`, `samples/`, `workspace/` and `.git/` from the copy you
   zip, unless the instructors want them.
3. Point the `paths.*` settings in `config.properties` at folders that
   exist in the submitted copy.
4. Add the slides (`GX-TY.pptx` or `.pdf`).
5. Test `compile` and `run` on a clean copy, on Windows and on macOS.
