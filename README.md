# GradeZZY

CS102 group project (Singapore Management University): an Auto Grading
System for Java lab tests.

> **Status: screens built, grading logic in progress.** The project
> compiles and runs. Every screen is built and can be tried with
> synthetic data (`sh run.sh --demo`). Most grading stages are not
> written yet, so live mode shows an empty state wherever a service is
> still a stub. Every unfinished method holds a
> `// TODO(owner): implement: ...` comment. See
> [Where to add your code](#where-to-add-your-code) and
> `design/UI-INTEGRATION.md`.

## What the application does

GradeZZY grades a whole section's lab test submissions in one go. The
instructor chooses a section (G1, G2, ...), uploads the students' zip
files and the tester files, and presses one button. For every submission
the application:

1. unzips it into a working copy (the original upload is never changed);
2. checks the folder structure, file names, required files and the name
   in each file header;
3. corrects common mistakes on the working copy and records each
   correction so the instructor can approve or undo it;
4. flags suspicious answers (untouched starter file, empty answer,
   hard-coded answer) for review, without changing any mark;
5. compiles the code and runs each tester with a time limit, stopping
   programs that hang;
6. works out the score per question and per student;
7. exports the results in the provided score sheet CSV format, and as
   JSON.

A problem with one submission is written to the anomaly log and grading
carries on with the next submission.

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
sh run.sh --demo         # the same screens with synthetic data only
sh run.sh --check        # load every screen once and exit, no window
sh run.sh --selftest     # run the seven demo flows, print PASS or FAIL
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

The `.sh` scripts are also written to work in Git Bash on Windows.

Two more developer options exist for checking the screens:
`--snapshot` saves a picture of every screen, and `--windowtest` drives
the real window with the mouse and keyboard. Both are explained in
`design/UI-INTEGRATION.md`.

`compile.sh core` empties `classes/` before it compiles, so run
`compile.sh` again before `run.sh`.

### What has been tested

| Check | macOS Apple Silicon, JDK 25.0.4 | macOS Intel | Windows |
|---|---|---|---|
| `compile.sh`, zero errors and warnings | Passed | Not tested | Passed (Git Bash, JDK 25.0.4) |
| `compile.sh core` (no JavaFX) | Passed | Not tested | Passed (Git Bash, JDK 25.0.4) |
| `run.sh console` | Passed | Not tested | Passed (Git Bash, JDK 25.0.4) |
| `run.sh --check` (16 screens, live and demo, both themes) | Passed | Not tested | Passed (Git Bash, JDK 25.0.4) |
| `run.sh --selftest` (7 demo flows) | Passed | Not tested | Passed (Git Bash, JDK 25.0.4) |
| `run.sh` (window opens) | Passed before the new screens; not run since | Not tested | Passed (JDK 25.0.4) |
| `run.sh --snapshot`, `run.sh --windowtest` | Not tested | Not tested | Passed (JDK 25.0.4) |
| `.bat` scripts | Not applicable | Not applicable | Passed (Command Prompt, JDK 25.0.4) |

**A teammate on Windows and one on an Intel Mac must run the scripts and
report back.** The brief takes up to 50% off if the code does not compile
or run on the instructor's laptop.

## Folder structure

| Folder or file | What it holds |
|---|---|
| `compile.sh`, `compile.bat` | Compile all sources into `classes/` and copy the `.fxml` and `.css` files. |
| `run.sh`, `run.bat` | Run the window, the demo, the screen checks or the console. |
| `clean.sh`, `clean.bat` | Empty `classes/` and remove `workspace/`. |
| `config.properties` | Every setting of the application. |
| `src/` | All Java source files, plus the FXML and CSS files of the `ui` package. |
| `classes/` | Compiled classes. Created by the compile script, not in git, and must be empty at submission. |
| `lib/` | The library jars. `lib/javafx/<platform>/` holds JavaFX for each platform. |
| `media/` | Images and audio used by the application. Empty for now. |
| `design/` | `gradezzy-design.html`, the clickable design (open it in a browser); `UI-INTEGRATION.md`, how each backend method reaches the screens; `UI-REVIEW.md`, the design and accessibility review; `concept/`, Damien's design concept and its handoff notes. |
| `README.md` | This file. |
| `docs/` | The project brief and coding style PDFs. Not in git. |
| `samples/` | The provided testers, score sheet, starter folder and six sample submissions. Not in git. |
| `test/` | `make-dummies.sh`, which builds the dummy submissions in `samples/dummy-submissions/` from the provided samples. |
| `workspace/` | Created while grading: working copies, exports, database. Not in git. |

Two things to know:

- The brief calls the media folder `media files`. The team chose `media`.
  Rename it before submission if the instructors insist on the brief's
  name.
- `docs/` and `samples/` are kept out of git because the documents are
  marked restricted and the samples hold student names. Each teammate
  copies them in from eLearn: `samples/` must contain `Tester-Files/`,
  `student-submission/`, `RenameToYourUsername/` and
  `IS442-ScoreSheet.csv`, as `config.properties` expects.

## Packages

The base package is `gradezzy`. Each package has one job.

| Package | Responsibility | Main classes |
|---|---|---|
| `app` | Entry points and wiring. | `ConsoleMain`, `ApplicationContext`, `ConsoleProgressListener` |
| `config` | Loads `config.properties`. | `AppConfig`, `ConfigKeys`, `ConfigException` |
| `model` | Plain data classes with no logic. | `Submission`, `Student`, `Section`, `Assessment`, `Question`, `QuestionResult`, `TestCaseResult`, `Anomaly`, `GradingRun`, `AuditEntry`, and the enums `AnomalyType`, `Severity`, `FixStatus`, `SubmissionStatus` |
| `ingest` | Accepts uploads, unzips them into a working copy, reads the roster. | `SubmissionLoader`, `ZipExtractor`, `WorkingCopyManager`, `RosterReader` |
| `validation` | One class per structural check. | `Validator` (interface), `AbstractValidator`, `StructureValidator`, `FileNameValidator`, `RequiredFilesValidator`, `HeaderNameValidator`, `ValidationRunner` |
| `fix` | One class per automatic correction, each with apply and undo. | `Fixer` (interface), `AbstractFixer`, `FolderNameFixer`, `FolderHierarchyFixer`, `HeaderNameFixer`, `FixManager`, `FixRecord` |
| `exec` | Runs `javac` and `java` in a separate process with a time limit. | `ProcessRunner`, `ProcessResult`, `SourceCompiler`, `TesterRunner` |
| `testcase` | Where tester files come from. | `TestCaseSource` (interface), `UploadedTesterSource`, `TypedTestCaseSource`, `TesterGenerator`, `TesterInstaller`, `TestCase`, `TesterFile` |
| `grading` | The pipeline, its stages and scoring. | `GradingStage` (interface), `GradingPipeline`, `GradingContext`, the nine `...Stage` classes, `ScoreParser` (interface), `TesterOutputScoreParser`, `ScoreCalculator`, `ProgressListener`, `CancellationToken` |
| `detection` | Flags answers for review. Never changes a mark. | `Detector` (interface), `UntouchedStarterDetector`, `EmptyAnswerDetector`, `HardCodedAnswerDetector` |
| `plagiarism` | Compares submissions for copied code. | `SimilarityDetector` (interface), `TokenSimilarityDetector`, `SimilarityPair` |
| `report` | One class per export format. | `Exporter` (interface), `CsvScoreSheetExporter`, `JsonExporter`, `StudentReportExporter`, `AnomalyReportExporter`, `AuditLogExporter`, `JsonWriter` |
| `stats` | Statistics for the dashboard. | `StatisticsCalculator`, `StatisticsSummary`, `StatisticsFilter`, `MarkBand` |
| `persistence` | Saves and loads grading state. | `ResultRepository` (interface), `SqliteResultRepository`, `BackupManager` |
| `service` | What the user interface calls. | `GradingService`, `PipelineFactory`, `AssessmentFactory`, `AnomalyService`, `ExportService`, `StatisticsService`, `SearchService`, `AuditService`, `SettingsService`, `HistoryService`, `PlagiarismService`, `ProgressEstimator`, `ServiceRegistry` |
| `ui` | JavaFX only: FXML files, controllers and the window. | `MainApp`, `Screen`, `ScreenNavigator`, `ShellBuilder`, `UiState`, `BaseController`, `ThemeManager`, `GradingTask`, `GradingMonitor`, sixteen `...Controller` classes, `FileTreeBuilder`, `DragDropHandler` |
| `ui.backend` | The one interface the screens call, and its live version that calls the services. | `DashboardBackend`, `LiveBackend`, `Capability`, small view classes such as `RepairItem` and `RunRecord` |
| `ui.demo` | Synthetic data for `--demo`. Never reads, grades or saves real files. | `DemoBackend`, `DemoData`, `DemoGrader` |
| `ui.component` | Reusable parts of screens. | `StatusBadge`, `EmptyState`, `NotConnectedPanel`, `MetricTile`, `Sheet` |
| `ui.view` | Parts of single screens, so each controller stays short. | `Sidebar`, `ContextBar`, `ResultsColumns`, the `Evidence...` tabs, the `Statistics...` charts |
| `ui.check` | Developer checks behind the `--check`, `--selftest`, `--snapshot` and `--windowtest` options. Not used when grading. | `ScreenCheck`, `SelfTest`, `SnapshotWriter`, `WindowTest` |
| `util` | Small shared helpers. | `FileUtils`, `TextUtils`, `InputValidator` |

### How the pieces fit

```
ui (JavaFX)        app.ConsoleMain
      \                 /
       service.GradingService
               |
       grading.GradingPipeline
               |
  ExtractStage -> ValidateStage -> FixStage -> DetectStage
     -> InstallTestersStage -> CompileStage -> ExecuteStage
     -> ScoreStage -> PersistStage
```

- **Every `javafx` import is in the `ui` package and its subpackages.**
  The console entry point and `compile.sh core` prove that the rest
  compiles and runs without JavaFX.
- **Screens never call a service directly.** A controller calls
  `ui.backend.DashboardBackend`. `LiveBackend` passes each call to one
  service method; `DemoBackend` answers from synthetic data.
- **One failing submission never stops the batch.** `GradingPipeline`
  catches the failure of any stage, records it as an `INTERNAL_ERROR`
  anomaly on that submission, marks the submission `FAILED` and moves on
  (brief section 4.2).
- **Originals are never modified.** All changes happen in the working copy
  under `workspace/working/`.
- **Storage is behind `ResultRepository`.** Only `SqliteResultRepository`
  contains SQL.

### The pipeline stages

| Order | Stage | What it does | Owner |
|---|---|---|---|
| 1 | `ExtractStage` | Creates the working copy and matches the student on the roster. | Damien |
| 2 | `ValidateStage` | Runs every `Validator`. | Seth |
| 3 | `FixStage` | Applies automatic corrections. | Jun Sheng |
| 4 | `DetectStage` | Runs every `Detector`. | Jun Sheng |
| 5 | `InstallTestersStage` | Copies testers and data files into the working copy. | Zach |
| 6 | `CompileStage` | Compiles each question folder. | Zach |
| 7 | `ExecuteStage` | Runs each tester with a time limit and parses its output. | Zach |
| 8 | `ScoreStage` | Sets per-question and per-student scores. | Jing Yuan |
| 9 | `PersistStage` | Saves the result so a run can be resumed. | Huxley |

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

Owners and TODO counts (3 left). The split is a starting point;
the team can swap areas. The next section explains each person's work in
detail.

| Owner | Packages | TODOs |
|---|---|---|
| Damien | `FileUtils`, `TextUtils`, `ingest`, one stage | 0 |
| Seth | `validation`, `InputValidator`, one stage; looks after `ui` | 0 |
| Jun Sheng | `fix`, `detection`, `plagiarism`, two stages | 0 |
| Zach | `exec`, `testcase`, three stages, the score parser | 3 |
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
3. Register it in `ApplicationContext.createFixManager()`:
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
- **Screen:** add a constant to `Screen`, an FXML file in
  `src/gradezzy/ui/fxml/` and a controller that extends `BaseController`.
  Anything it needs from a service goes through a new
  `DashboardBackend` method, written in both `LiveBackend` and
  `DemoBackend`. `run.sh --check` then confirms that it loads.
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
   `ingest.ignored.file.names`.
2. `SubmissionLoader.extractUsername`: remove the prefix matched by
   `ingest.zip.name.prefix.pattern` and the `.zip` ending, so
   `2023-2024-ping.lee.2023.zip` gives `ping.lee.2023`.
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
   missing input validation, and the Submissions screen reports every
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
   stage, and the brief rewards extra edge cases. Done:
   `sh test/make-dummies.sh` builds these six plus a username folder
   inside a username folder and the untouched starter folder, in
   `samples/dummy-submissions/`. Only the script is in git, because the
   dummies are built from the restricted samples. Add that folder on the
   Submissions screen to grade them; none of them stops the batch.
5. As each teammate finishes a service, open its screen in live mode
   (`sh run.sh`) and check that the real data looks right.
   `design/UI-INTEGRATION.md` says which screen shows which service;
   `design/UI-REVIEW.md` records the design and what was verified.
6. Make sure the scripts are run on Windows and on an Intel Mac, on
   your own laptop or a teammate's: `compile`, `run --check`,
   `run --selftest`, `run console` and `run --windowtest`. Record the
   results in the tested table above. The brief takes up to 50% off if
   the code does not run on the instructor's laptop.

The checks `InputValidator` must give:

| Method | Input | Expected |
|---|---|---|
| `isValidSectionCode` | `G1`, `G12`, `g1`, `G`, `1`, empty | true, true, decide with the team, false, false, false |
| `isPositiveInteger` | `10`, `0`, `-3`, `abc`, empty | true, false, false, false, false |

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

- Controllers hold no grading logic. A controller calls
  `DashboardBackend`, and `LiveBackend` passes the call to one service
  method.
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
  registered, so folder fixes come before header fixes. A fix that fails
  is skipped and its anomaly stays for the instructor.
- `FolderHierarchyFixer` moves everything visible at the top of the
  working copy (the Q folders and any other files) into a new username
  folder. `FolderNameFixer` renames the only top folder to the username,
  including a change of letter case only. Both are undone by moving back.
- `HeaderNameFixer` reads the file named at the start of the header
  anomaly's message, such as `Q1/Q1a.java`. It copies the file into the
  hidden `.gradezzy-backup` folder of the working copy, then writes the
  roster name and email after the blank `Name:` and `Email ID:` labels.
  Undo restores the copy. If the student is not on the roster, or the
  header cannot be completed (for example it has no `Name:` line), the
  file is left unchanged for the instructor.
- `FixManager` is shared by the grading threads, so its public methods
  are `synchronized`. `findRecord(anomaly)` returns the fix for an
  anomaly. `approve` turns an applied fix into `APPROVED`; `undo` reverses
  an applied or approved fix and sets `UNDONE`.

How the flags work (never a change of mark):

- `UntouchedStarterDetector` compares each answer file with the starter
  file, ignoring the header comment, blank lines and line endings. If
  every file is unchanged it raises one flag for the whole submission (the
  original resource folder was submitted); otherwise one flag per
  unchanged file.
- `EmptyAnswerDetector` flags an answer method whose body, without
  comments and spaces, is still the starter's body. A wholly unchanged
  file is left to the untouched check when that check is on.
- `HardCodedAnswerDetector` flags an answer method with 3 or more `if` or
  `case` branches that compare a value with a fixed literal, such as
  `s.equals("cat")`. Common values (0, 1, -1 and `""`) do not count. It
  can still flag honest code with many such checks, and misses answers
  hidden in arrays or maps.
- `DetectStage` runs each detector that is switched on
  (`detection.*.enabled`). If one fails, it adds a warning saying the
  check could not run; it never fails a submission.

How the similarity check works: `TokenSimilarityDetector` splits each
answer into tokens, ignoring spacing and comments, and collects every run
of three tokens. It removes the starter file's runs, so the shared
skeleton does not count, then compares two students with the Jaccard
measure (shared runs divided by all runs). Pairs at or above
`plagiarism.similarity.threshold` are reported. Renaming variables or
reordering code lowers the score.

Checked on the six samples: chee.teo and jing.lim get a username folder;
david's and fen.lai's folders are renamed; the blank headers (Q3 for
chee.teo and david, all five files for jing.lim) are filled in; every fix
shows `APPLIED`; undoing every fix restores each working copy byte for
byte; the totals and the original zips are unchanged.

### 4. Zach: compiling, running and reading scores

**Packages:** `exec`, `testcase`, plus `InstallTestersStage`,
`CompileStage`, `ExecuteStage` and `TesterOutputScoreParser` in
`grading`. 14 TODOs. This is where "safely terminate programs that exceed
the time limit" lives, which the brief lists as a core requirement.

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
  relative name. Those files are inside the sample zips but not in
  `Tester-Files`; ask the team where the instructor will supply them.
- `Q2` needs the provided `DataException.class`, and `Q3` needs
  `Shape.class`, `Circle.class` and `Rectangle.class`, in the folder.
- For each test case a tester prints `Passed` or a line starting with
  `Failed`. The last line of output is the total as a decimal, such as
  `3.0`. An exception inside a test prints a stack trace to standard
  error and the tester carries on.
- Maximum scores are 3, 3, 5, 5 and 4.

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
4. `ProgressEstimator`: fraction done and seconds remaining.
5. `ExportService`: find an exporter by name and write to the output
   folder.
6. `AnomalyService`, `SearchService`, `StatisticsService`,
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
- Only `SqliteResultRepository` may contain SQL or import `java.sql`.
  Always use `PreparedStatement` with `?` placeholders, never text joined
  into SQL.
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

## Configuration

Every setting is in `config.properties` and is read through
`AppConfig`, using the names in `ConfigKeys`. A missing setting stops the
application at start-up with a message naming the setting. To use another
file: `java -Dgradezzy.config=other.properties ...`.

| Setting | Default | What it does |
|---|---|---|
| `app.module.codes` | `CS102` | Modules the instructor can pick from, separated by commas. The first is the default. Every module shares the sections and the assessment. |
| `app.section.codes` | `G1` to `G10` | Sections the instructor can pick from. |
| `paths.submissions.dir` | `samples/student-submission` | Folder graded by the console entry point. |
| `paths.testers.dir` | `samples/Tester-Files` | Folder holding the tester files. |
| `paths.score.sheet.file` | `samples/IS442-ScoreSheet.csv` | The roster and the template for the CSV export. |
| `paths.starter.dir` | `samples/RenameToYourUsername` | Starter files, used to spot untouched answers. |
| `paths.working.dir` | `workspace/working` | Where working copies are created. |
| `paths.output.dir` | `workspace/output` | Where exports are written. |
| `paths.backup.dir` | `workspace/backup` | Where database backups are kept. |
| `ingest.zip.name.prefix.pattern` | `^[0-9]{4}-[0-9]{4}-` | Text in front of the username in a zip file name. |
| `ingest.ignored.file.names` | `.DS_Store,__MACOSX,Thumbs.db` | Names skipped inside a submission. |
| `ingest.max.zip.size.mb` | `50` | Largest unzipped size accepted. |
| `ingest.max.zip.entries` | `2000` | Largest number of entries accepted in a zip. |
| `assessment.name` | `Lab Test` | Name of the assessment. |
| `assessment.question.ids` | `Q1a,Q1b,Q2a,Q2b,Q3` | The questions, in order. |
| `assessment.question.<id>.folder` | for example `Q1` | Folder the question's file must be in. |
| `assessment.question.<id>.file` | for example `Q1a.java` | The required source file. |
| `assessment.question.<id>.max.score` | `3`, `3`, `5`, `5`, `4` | Maximum score of the question. |
| `assessment.placeholder.folder.names` | `RenameToYourUsername,RenameToYourStudentID` | Folder names that mean the student forgot to rename. |
| `assessment.provided.class.files` | `DataException.class`, ... | Compiled files that are kept in the working copy. |
| `assessment.tester.data.files` | `personstester.txt,studentstester.txt` | Data files copied next to the testers. |
| `validation.header.name.label` | `Name:` | Label in front of the name in a file header. |
| `validation.header.email.label` | `Email ID:` | Label in front of the email in a file header. |
| `validation.header.scan.lines` | `10` | Lines at the top of a file searched for the header. |
| `fix.auto.apply.enabled` | `true` | Apply fixes automatically, or only report. |
| `exec.javac.command` | `javac` | Command used to compile. |
| `exec.java.command` | `java` | Command used to run a tester. |
| `exec.compile.timeout.seconds` | `30` | Time limit for compiling one question folder. |
| `exec.run.timeout.seconds` | `10` | Time limit for one tester run. |
| `exec.thread.count` | `4` | Submissions graded at the same time. |
| `exec.max.output.characters` | `100000` | Longest program output kept. |
| `grading.tester.class.suffix` | `Tester` | `Q1a` becomes `Q1aTester`. |
| `grading.pass.marker` | `Passed` | Line a tester prints for a passed test. |
| `grading.fail.marker` | `Failed` | Start of the line for a failed test. |
| `grading.test.header.marker` | `Test` | Start of the line a tester prints before each test, such as `Test 1: ...`. |
| `detection.untouched.enabled` | `true` | Flag untouched starter files. |
| `detection.empty.enabled` | `true` | Flag empty answers. |
| `detection.hardcoded.enabled` | `true` | Flag hard-coded answers. |
| `plagiarism.enabled` | `false` | Run the similarity check after grading. |
| `plagiarism.similarity.threshold` | `0.80` | Similarity at which a pair is flagged. |
| `export.formats` | `csv,json` | Formats offered by default. |
| `export.csv.line.ending` | `CRLF` | Line ending of the score sheet. |
| `export.csv.id.prefix` | `#` | Character in front of the id and username. |
| `export.csv.end.of.line.marker` | `#` | Value of the last column. |
| `stats.pass.mark.percent` | `50` | Percentage that counts as a pass. |
| `stats.mark.band.width.percent` | `10` | Width of each mark band. |
| `database.enabled` | `true` | Save grading state to SQLite. |
| `database.file` | `workspace/gradezzy.db` | Location of the database file. |
| `database.backup.keep.count` | `5` | Backups kept before the oldest is deleted. |
| `audit.enabled` | `true` | Log instructor actions. |
| `ui.theme` | `light` | `light` or `dark`. |
| `ui.window.width`, `ui.window.height` | `1200`, `800` | Starting window size. |
| `ui.animations.enabled` | `true` | Show subtle animations. |

The time limits, size limits, pass mark, band width and similarity
threshold are starting values chosen for the skeleton. They do not come
from the brief; the team should agree on them.

## Libraries

All are free and open source, as the brief requires, and were downloaded
from Maven Central.

| Library | Version | Licence | Why it was chosen |
|---|---|---|---|
| JavaFX (`javafx-base`, `-graphics`, `-controls`, `-fxml`) | 25.0.4 | GPL v2 with Classpath Exception | The desktop window. It matches JDK 25, separates layout (FXML) from logic (controllers), and has built-in charts, tables, tree views, file pickers and drag and drop. |
| AtlantaFX (`atlantafx-base`) | 3.0.0 | MIT | Modern themes for JavaFX. Its Cupertino Light and Dark themes give light and dark mode with one line of code. |
| ControlsFX (`controlsfx`) | 11.2.5 | BSD 3-Clause | Extra controls JavaFX lacks. Used for the auto-completing search fields (`TextFields.bindAutoCompletion`) in `ui.ResultsController` and `ui.view.AnomalyFilters`. |
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

Kept to a minimum and confined to the classes named here:

| Construct | Where | Why |
|---|---|---|
| JavaFX, FXML | `ui` package only | The window. |
| Lambdas and method references (`->`, `::`) | 49 files in `ui` | Button handlers, table cells and callbacks. |
| `java.util.function` (`Function`, `Consumer`, `IntConsumer`) | `ui.HelpController`, several `ui.view` classes, `ui.check.WindowTest` | Passing a small piece of behaviour to a shared table or filter part. |
| `javafx.concurrent.Task` (a background thread) | `ui.GradingTask` | Keeps the window responsive while grading. |
| `new Thread`, `Platform.runLater` | `ui.backend.LiveBackend`, `ui.ResultsController`, `ui.PlagiarismController`, `ui.HistoryController`, `ui.GradingTask`, `ui.GradingMonitor` | Grading, exports, backups and the similarity check run off the window's thread; screen updates go back to it. |
| `ConcurrentHashMap` | `ui.backend.LiveBackend` | The last run of each section, which a restore clears from a background thread. |
| `javafx.animation` (`Timeline`, `FadeTransition` and others) | `ui.MainController`, `ui.GradingMonitor`, `ui.view.SheetLayer`, `ui.view.ToastLayer`, `ui.demo.DemoGrader` | Subtle animations and timers. Turned off by `ui.animations.enabled`. |
| `java.awt.Robot`, `javax.imageio.ImageIO`, `java.util.concurrent`, `java.util.zip.ZipOutputStream` | `ui.check` only | The `--snapshot` and `--windowtest` developer checks. Not used when grading. |
| `synchronized` | `grading.CancellationToken`, `fix.FixManager` | The Cancel button and the grading thread share one flag; the grading threads and the anomaly log share one list of fixes. |
| `java.nio.file` (`Files.move`, `Files.copy`, `Files.createTempDirectory`, `DirectoryStream`, `StandardCopyOption`) | `fix` package | Moves and renames folders in the working copy and keeps a backup of each file a fix changes. |
| `HashSet`, `String.join`, `List.subList` | `plagiarism.TokenSimilarityDetector` | Holds each answer's runs of three tokens for the similarity measure. |
| `java.util.Properties` | `config.AppConfig` | Reads `config.properties`. |
| `enum` | `model`, `ui.Screen` | Fixed lists of values. |
| JDBC (`Connection`, `PreparedStatement`, `ResultSet`), try-with-resources | `persistence.SqliteResultRepository` | Talks to SQLite and closes each statement even when a query fails. |
| `ProcessBuilder`, `Process`, `ProcessHandle` | `exec.ProcessRunner` | Runs `javac` and `java` as separate programs and kills a run and its child processes at the time limit. |
| `Stream.toList()` | `exec.ProcessRunner.terminate` | Turns the child processes of a run into a list so they can be killed. |
| `Thread.sleep` | `exec.ProcessRunner.run` | Pauses between checks of a running program, instead of using a reader thread. |
| `StackWalker` with a lambda, `System.setOut`, a `PrintStream` subclass | `exec.TesterHarness` | Marks only the lines the tester itself prints, so a student printing `Passed` is not believed. |
| Reflection (`Class.forName`, `Method.invoke`) | `exec.TesterHarness` | Starts the tester's `main` from inside the harness. |
| `Runtime.halt`, the `jdk.lang.Process.launchMechanism` property | `exec.TesterHarness` | Ends the run without waiting for student threads, and stops student code starting other programs (macOS and Linux). |
| `java` options `--limit-modules`, `--illegal-native-access=deny`; `UUID` | `exec.TesterRunner` | Student code gets only core Java and no native code; a new random token for every tester run. |
| `java.util.zip` (to be written) | `ingest.ZipExtractor` | Unzips submissions. |
| `java.util.regex.Pattern` | `service.SettingsService` | Checks that the zip name pattern setting is a valid regular expression. |
| `java.nio.file.Files` (`readString`, `writeString`, `move`, `isSymbolicLink`) | `service.SettingsService`, `service.SearchService` | Saves `config.properties` through a temporary copy; reads source files for the code search. |
| `java.nio.file` (`Path`, `Paths`, `Files`) | About 20 files in `ui` | The files and folders the instructor chooses or drops on the window. |

Outside the `ui` package, a stream appears only in `exec.ProcessRunner` and
`exec.TesterHarness`, and a lambda only in `exec.TesterHarness`.

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

## Feature checklist

`[ ]` means a place exists in the skeleton but the feature is not
implemented. `[x]` means it works now.

### MUST (brief section 4)

- [ ] Batch upload of zips or many files (`ingest.SubmissionLoader`,
  `ui.UploadController`)
- [x] Batch grading loop with no per-submission clicking
  (`grading.GradingPipeline`, `service.GradingService`)
- [ ] Unzip submissions (`ingest.ZipExtractor`,
  `ingest.WorkingCopyManager`)
- [ ] Validate structure, file names and required files
  (`validation.StructureValidator`, `FileNameValidator`,
  `RequiredFilesValidator`)
- [ ] Check student names in file headers
  (`validation.HeaderNameValidator`)
- [x] A failing submission is recorded as an anomaly and does not stop
  the batch (`grading.GradingPipeline`)
- [x] Auto-correct folder names, folder hierarchy and missing header
  names on a working copy (`fix.FolderNameFixer`,
  `FolderHierarchyFixer`, `HeaderNameFixer`, `FixManager`)
- [ ] Compile and run with testers (`exec.SourceCompiler`,
  `exec.TesterRunner`, `testcase.TesterInstaller`)
- [ ] Stop programs that exceed the time limit (`exec.ProcessRunner`)
- [ ] Per-question and per-student scores
  (`grading.TesterOutputScoreParser`, `ScoreCalculator`)
- [x] Export in the provided CSV format (`report.CsvScoreSheetExporter`)
- [x] Separate classes for validation, compilation, execution, grading
  and reporting (the package structure)
- [x] Configuration in an external file (`config.properties`,
  `config.AppConfig`)
- [ ] Screens that show progress, errors and results
  (`ui.ProgressController`, `AnomalyLogController`, `ResultsController`;
  the screens are built and work in demo mode, real results wait on the
  pipeline stages)
- [ ] Input validation everywhere (`util.InputValidator`)

### SHOULD

- [x] Anomaly log with action buttons; approve and undo, singly and in
  bulk (`service.AnomalyService`, `ui.AnomalyLogController`; undo needs
  the run graded in this session, because fix records are kept in
  memory)
- [x] Flag untouched starter files, empty answers and hard-coded answers
  (`detection` package)
- [ ] Test cases typed into the app (`testcase.TypedTestCaseSource`,
  `TesterGenerator`, `ui.TestCaseController`)
- [x] Progress bar and estimated time of completion
  (`service.ProgressEstimator`, `ui.GradingMonitor`)
- [x] Statistics dashboard filtered by module and section (`stats`
  package, `service.StatisticsService`, `ui.StatisticsController`)
- [x] Search, filter and sort (`service.SearchService`)
- [x] Select one, several or all (`ui.AnomalyLogController`,
  `ResultsController`)
- [ ] Collapse and expand panels (`ui`)
- [x] Next, Back and Cancel (`ui.MainController`)
- [x] Cancel, retry and resume grading (`service.GradingService`,
  `grading.CancellationToken`; a run from an earlier session is resumed
  with the score sheet and testers in the settings)
- [x] Light and dark theme switch (`ui.ThemeManager`, `ui.theme`)
- [x] Settings page backed by `config.properties`
  (`service.SettingsService`, `ui.SettingsController`)
- [x] SQLite persistence behind `ResultRepository`
  (`persistence.SqliteResultRepository`)
- [ ] Audit log, exportable (`service.AuditService`,
  `report.AuditLogExporter`; the log is saved and shown, the export is
  not registered in `ApplicationContext` yet)
- [ ] Individual student report, exportable
  (`report.StudentReportExporter`, `ui.StudentReportController`)
- [x] JSON export (`report.JsonExporter`, `JsonWriter`)
- [ ] Nielsen's 10 usability heuristics, reviewed screen by screen:
  https://www.nngroup.com/articles/ten-usability-heuristics/

### COULD

- [x] Backup and recovery of a previous grading state
  (`persistence.BackupManager`, `service.HistoryService`)
- [x] Subtle animations (`ui`, `ui.animations.enabled`)
- [x] Plagiarism detection (`plagiarism` package,
  `service.PlagiarismService`; runs when `plagiarism.enabled` is true)

## Before submission

1. Run `sh clean.sh` (or `clean.bat`) so `classes/` is empty.
2. Remove `docs/`, `samples/`, `workspace/` and `.git/` from the copy you
   zip, unless the instructors want them.
3. Point the `paths.*` settings in `config.properties` at folders that
   exist in the submitted copy.
4. Add the slides (`GX-TY.pptx` or `.pdf`).
5. Test `compile` and `run` on a clean copy, on Windows and on macOS.
