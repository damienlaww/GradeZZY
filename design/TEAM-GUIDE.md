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
sh run.sh --selftest     # run the eleven demo flows, print PASS or FAIL
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
| `media files/` | Images and audio, named as the brief asks. `media files/readme/` holds the screenshots in `README.md`, taken with `sh run.sh --snapshot` in demo mode. |
| `design/` | `redesign/`, the design of the current window and its decisions; `UI-INTEGRATION.md`, how each backend method reaches the screens; `NIELSEN-REVIEW.md`, the window checked against Nielsen's 10 usability heuristics; `gradezzy-design.html` and `UI-REVIEW.md`, the design and review of the earlier 16-screen window, kept for reference; `concept/`, Damien's design concept and its handoff notes. |
| `README.md` | Part 1: how to use GradeZZY, for a first-time user. Part 2: how the grading backend works. |
| `design/TEAM-GUIDE.md` | This file: build, owners, coding rules, checklist. |
| `docs/` | The project brief and coding style PDFs. Not in git. |
| `samples/` | The provided testers, score sheet, starter folder and six sample submissions. Only `Tester-Files/` and `e2e/` are in git; the score sheet, starter folder and submissions are not. |
| `samples/e2e/` | The end-to-end test set: 42 students for section G4, with duplicate, damaged, late and copied uploads. `make_e2e.py` rebuilds it; its `README.md` lists the expected marks. Graded with `samples/Tester-Files`. |
| `workspace/` | Created while grading: working copies, exports, database. Not in git. |

Two things to know:

- The media folder is `media files/`, the name the brief asks for. Quote
  the path in scripts and write it as `media%20files/` in Markdown links.
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

## Who owns what

| Teammate | Packages |
|---|---|
| Damien | `ingest`, `FileUtils` and `TextUtils` in `util`, `ExtractStage` in `grading` |
| Seth | `validation`, `InputValidator` in `util`, `ValidateStage` in `grading`; looks after `ui` |
| Jun Sheng | `fix`, `detection`, `plagiarism`, `FixStage` and `DetectStage` in `grading`, the detector list in `PipelineFactory`; built the `ui` package |
| Zach | `exec`, `testcase`, `InstallTestersStage`, `CompileStage`, `ExecuteStage` and `TesterOutputScoreParser` in `grading` |
| Jing Yuan | `app`, `config`, `model`, `service`, `ScoreStage` and `ScoreCalculator` in `grading`, and the wiring (`PipelineFactory`, `ApplicationContext`) |
| Huxley | `report`, `stats`, `persistence`, `PersistStage` in `grading` |

## Libraries

All are free and open source, as the brief requires, and were downloaded
from Maven Central.

| Library | Version | Licence | Why it was chosen |
|---|---|---|---|
| JavaFX (`javafx-base`, `-graphics`, `-controls`, `-fxml`) | 25.0.4 | GPL v2 with Classpath Exception | The desktop window. It matches JDK 25 and has tables, shapes for the charts, file pickers and drag and drop. The screens are plain JavaFX code; `javafx-fxml` stays on the module path but no screen uses FXML any more. |
| AtlantaFX (`atlantafx-base`) | 3.0.0 | MIT | Modern themes for JavaFX. Its Cupertino Light and Dark themes give light and dark mode with one line of code. |
| ControlsFX (`controlsfx`) | 11.2.5 | BSD 3-Clause | Extra controls JavaFX lacks. It was used by the earlier window's search fields; the current window does not use it, and it stays on the classpath with its run flags. |
| Ikonli (`ikonli-core`, `ikonli-javafx`, `ikonli-fontawesome6-pack`) | 12.4.0 | Apache 2.0 | Font Awesome 6 icons as JavaFX nodes. `Parts.icon` turns an icon code such as `fas-check` into a `FontIcon`, so every icon is sharp at any size and takes its colour from the style sheet. |
| SQLite JDBC (`sqlite-jdbc`) | 3.53.4.0 | Apache 2.0 | A database in a single file with no server to install, for saved state, history, resume and backup. |

Notes:

- The JavaFX jars differ per platform, so there are three copies, in
  `lib/javafx/mac`, `lib/javafx/mac-aarch64` and `lib/javafx/win`. The
  scripts choose one. On a Mac they ask the JDK which processor it is
  built for (`os.arch`), not the shell, so an Intel JDK on an Apple
  Silicon Mac, or a terminal running under Rosetta, still gets the
  matching jars.
- `compile.sh` and `compile.bat` pass `--release 25` to `javac`, so an
  older JDK stops with "release version 25 not supported" instead of a
  confusing class file version error.
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
| `java.nio.file` (`Path`, `Paths`, `Files`, `DirectoryStream`, `StandardCopyOption`, `LinkOption`, `FileTime`) | About 60 files | Every file and folder the system reads, copies, moves or writes. | `Path` is a file or folder location (like `File`); `Files` has static methods to read, copy, move and list them, and can refuse to follow links. |
| `javafx.concurrent.Task`, `new Thread`, `Platform.runLater` | `ui.GradingTask`, `ui.backend.LiveBackend`, `ui.flow.GradeScreen`, `ui.flow.SimilarityCheck`, `ui.flow.FileInspection`, `ui.flow.ExportJob`, `ui.flow.TesterCheck`, `ui.flow.TesterStrengthCheck`, `ui.flow.MissingStudentsLookup`, `ui.flow.UndoFixJob`, `ui.flow.RegradeJob`, `ui.flow.SetUpScreen`, `ui.flow.ExportScreen`, `ui.flow.FlowWindow`, `ui.flow.StudentPanel`, `ui.flow.RunsScreen`, `ui.flow.CodeSheet`, `ui.flow.HomeScreen`, `ui.flow.ScoreSheetSearch`, `ui.flow.ScoreSheetChange` | Grading (with the similar code check at the end of `GradingTask`), checking chosen files, the class files and the tester folder, checking how strong each tester is, reading the score sheet for students with no submission, grading a question again, grading one student, one question or the ticked students again, exports, backups, and undoing a fix or applying a rejected one again (each grades the student again) run off the window's thread so the window does not freeze. | The slow work runs on a second thread; `Platform.runLater` hands each screen update back to the window's thread. |
| `ConcurrentHashMap` | `ui.backend.LiveBackend` | The last run of each section, which a restore clears from a background thread, and the review decisions on flags. | A `HashMap` that two threads can use at the same time. |
| `javafx.animation` (`PauseTransition`, `Timeline`) | `ui.flow.FlowWindow`, `ui.flow.ReviewScreen`, `ui.demo.DemoGrader` | How long a notice stays, the pause before a code search, the demo grader's pace. | Timers that call a method after a delay. |
| Parallel grading: `Thread`, `Runnable`, `synchronized`, `Thread.join` | `grading.GradingPipeline`, `GradingWorker`, `SubmissionHandout`, `SynchronizedProgressListener`, `CancellationToken`, `fix.FixManager`, `persistence.SqliteResultRepository`, `service.HistoryService`, `service.GradingService`, `app.ConsoleProgressListener`, `exec.TrustedPrintStream`, `modelanswer.TesterStrengthChecker`, `MutantWorker`, `MutantBatch`, `RandomInputChecker` | Grades `exec.thread.count` submissions at the same time, and runs that many broken copies of the model answer at once in the tester strength check. | Each `GradingWorker` takes the next submission from the `SubmissionHandout` (each `MutantWorker` the next copy from the `MutantBatch`); `synchronized` lets only one thread at a time touch shared data; `join` waits for a thread to finish. |
| `CharsetDecoder`, `CodingErrorAction`, `ByteBuffer` | `fix.EncodingFixer`, `report.CsvScoreSheetExporter` | Tells whether a Java file is valid UTF-8, which javac needs, and whether a score sheet is UTF-8 or Windows-1252, so the export is written the same way. | A decoder turns bytes into characters; set to `REPORT`, it throws instead of guessing when the bytes are not UTF-8. |
| `java.util.Properties` | `config.AppConfig` | Reads `config.properties`. | Reads a file of `key=value` lines into a lookup table. |
| JDBC (`Connection`, `PreparedStatement`, `ResultSet`) | `persistence` only: `SqliteResultRepository` and its table classes (`SqliteSchema`, `RunTables`, `SubmissionTables`, `ReviewTables`, `AdjustmentTable`, `AuditTable`, `JdbcSupport`) | Saves runs, review decisions, checked 0s and changed marks to SQLite so History can list them after a restart. | `Connection` is the open database, `PreparedStatement` is one SQL command with `?` blanks we fill in, `ResultSet` is the rows that come back; each is closed in a `finally` block. |
| `Thread.sleep`, `InterruptedException` | `util.FileUtils` | Waits briefly before retrying a move or delete that Windows blocked, such as when antivirus holds a new file open. | Pauses for half a second, then tries again, up to five times. |
| `Files.getFileStore`, `Files.setAttribute(path, "dos:readonly", false)` | `util.FileUtils` | A read-only file cannot be deleted on Windows, so the flag is cleared before a delete is tried again. Elsewhere a failed delete is reported at once. | Asks which kind of disk a file is on, and on a Windows disk switches off the file's read-only tick box. |
| `ProcessBuilder`, `Process`, `ProcessHandle`, `Thread.sleep` | `exec.ProcessRunner` | Runs `javac` and `java` as separate programs and stops a run and its child programs at the time limit. | Starts another program like typing a command in the terminal; while it runs we read what it prints, and wait 50 milliseconds only when it has printed nothing new. |
| `ProcessHandle.onExit().get` with `TimeUnit`, `TimeoutException`, `ExecutionException` | `exec.ProcessRunner` | On Windows a stopped `java.exe` holds its folder for a moment, so the runner waits up to 1 second (shared by all the children it stopped) for it to end. | `onExit()` is a result that arrives when the program ends; `get` waits for it, but gives up after the time given. |
| `default` methods in an interface | `ui.backend.DashboardBackend` | A few methods, such as `getScoreSheet` and `setZeroChecked`, work the same in the live and demo backends, so they are written once in the interface. | An interface method with a body: each class that implements the interface gets it unless it writes its own. |
| `InputStream.available()` | `exec.ProcessRunner` | Tells whether a running program has output waiting, so the runner reads it at once instead of pausing first. | Asks a stream how many bytes can be read right now without waiting; 0 means nothing has arrived yet. |
| Shutdown hook (`Runtime.addShutdownHook`), a daemon `Thread` | `exec.ProcessRunner`, `exec.RunningProcesses`, `exec.TesterHarness`, `exec.HarnessWatchdog` | No tester keeps running after GradeZZY closes or crashes. | A shutdown hook is a thread Java starts as the program ends; ours stops every tester still running. The watchdog is a second thread inside each tester run that sleeps until the time limit plus 2 seconds, then ends the run, in case GradeZZY is gone. |
| `PrintStream` subclass, `System.setOut`, `StackWalker` | `exec.TesterHarness`, `exec.TrustedPrintStream`, `exec.TrustedFrameCheck` | Marks only the lines the tester itself prints, so a student printing `Passed` is not believed. | All printing goes through our `PrintStream`, which checks who called it and marks a line only when no student method is on the call stack. |
| `Throwable.setStackTrace`, an overridden `toString` | `exec.SkippedCaseException` | The provided testers print the stack trace of any exception in a case, so a case GradeZZY skips prints one plain line instead of a GradeZZY stack trace. | Our exception keeps an empty list of call stack lines and prints as its message alone. |
| Varargs `format(String, Object...)` | `exec.TrustedPrintStream` only | It overrides `PrintStream.format`, whose signature Java fixes. | `printf` calls this method, so ours must have exactly the same parameters to catch `printf` output too. |
| Reflection (`Class.forName`, `Method.invoke`, `Class<?>`) | `exec.TesterHarness`, `exec.TrustedPrintStream`, `exec.TrustedFrameCheck` | Starts the tester's `main` from inside the harness. | Loads a class by its name at run time and calls its `main`, the way `java Q1aTester` would. |
| `Runtime.version().feature()` | `app.ConsoleMain`, `ui.MainApp` | Stops at once with a clear message on a JDK older than 25. | Asks Java which version it is, such as 25. |
| `Runtime.halt`, the `jdk.lang.Process.launchMechanism` property | `exec.TesterHarness` | Ends the run without waiting for student threads, and stops student code starting other programs (macOS and Linux). | A hard stop for the tester's program, and a switch that blocks it from launching others. |
| `java` options `--limit-modules`, `--illegal-native-access=deny`; `UUID` | `exec.TesterRunner` (`UUID` also for anomaly ids) | Student code gets only core Java and no native code; a new random token for every tester run. | Command-line options that shrink what student code can reach; a `UUID` is a random id nobody can guess. |
| `java.util.zip` (`ZipFile`, `ZipEntry`, `Enumeration`, `CRC32`) | `ingest.ZipExtractor` | Unzips submissions safely. | Opens a zip and hands us its entries one at a time, like an `Iterator`. |
| `java.util.regex.Pattern`, `matches`, `replaceFirst`, `replaceAll` | `service.SettingsService`, `ingest.SubmissionLoader`, `ingest.ZipExtractor.isSafeEntry`, `modelanswer.MutantMaker` | The zip name setting is itself a pattern; unsafe Windows file names are a pattern too; a number literal is "digits only", and spaces are "any run of whitespace". | A pattern describes a family of strings, such as "2023-2024-" followed by a username. |
| `split("\\R")` | `grading.CompileStage`, `grading.TesterOutputScoreParser`, `exec.TesterRunner`, `exec.TrustedPrintStream`, `modelanswer.TesterStrengthChecker`, `modelanswer.RandomInputChecker` | Splits program output into lines whatever the line ending. | `split` as taught; `\R` means "any kind of line break". |
| `StandardCharsets.ISO_8859_1` | `testcase.TesterMarks` | Opens a tester file whatever encoding it was saved in, to read its `score +=` lines. | A way of turning bytes into characters that accepts every byte, so a tester saved in Windows-1252 still opens. |
| `StandardCharsets.UTF_16LE`, `UTF_16BE` | `util.TextUtils` (used by `fix.EncodingFixer` and `validation.HeaderNameValidator`) | Reads a source file Notepad saved as "Unicode" (UTF-16), so it can be saved again as UTF-8 and compiled. | Names for the two byte orders of UTF-16; the first two bytes of the file (its byte order mark) say which one it is. |
| `Collections.reverseOrder()` | `testcase.TesterMarks` | Sorts a tester's case marks from largest to smallest, to find the most a number of passed cases can be worth when its cases cannot be matched to its marks one for one. | `Collections.sort` as taught, given a comparator that puts the larger number first. |
| `java.time` (`LocalDateTime`, `DateTimeFormatter`) | `model`, `persistence`, `report.AuditLogExporter`, `service`, `ui.demo` | Run and audit times. | The current date and time, and how to print it. |
| `java.util.Random` | `ui.demo.DemoData`, `modelanswer.InputMaker` | Synthetic marks for `--demo`, always from the same seed; random inputs for students' methods, from a new seed each grading run. | Gives a sequence of "random" numbers decided by its seed: the same seed gives the same demo every time, a new seed gives inputs no student could know. |

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
