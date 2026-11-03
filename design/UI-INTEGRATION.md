# GradeZZY UI: how your backend plugs in

For everyone writing a backend class. The interface is finished, and every
screen already calls the service method your work fills in. This page says
where your work appears and what each screen expects from it.

## Run it

| Command (macOS or Git Bash) | Windows | What it does |
|---|---|---|
| `sh compile.sh` | `compile.bat` | Compiles everything into `classes/`. |
| `sh run.sh` | `run.bat` | The real application, calling the real services. |
| `sh run.sh --demo` | `run.bat --demo` | The same window with synthetic data. Never reads, grades or saves real files. |
| `sh run.sh --check` | `run.bat --check` | Opens every tab, step and sheet with the live and demo backends, in both themes, at 1200x800 and 960x640, then exits. |
| `sh run.sh --selftest` | `run.bat --selftest` | Runs the seven demo flows without a window and prints PASS or FAIL for each. |
| `sh run.sh --snapshot [folder]` | same | The same walk as `--check`, saving a PNG of every state. Default folder: `workspace/ui-snapshots` (ignored by git). |
| `sh run.sh --windowtest [folder]` | `run.bat --windowtest [folder]` | Opens the real window and drives it with operating-system mouse and keyboard input: every tab, step, sheet and shortcut. Prints PASS or FAIL per check and saves pictures to `workspace/ui-window-test`. Needs a desktop; leave the mouse and keyboard alone while it runs. |

Run `sh run.sh --check` after any change. It catches a screen that fails
to open, or a broken stylesheet, in both modes.

## How a screen reaches your code

```
Screen (gradezzy.ui.flow.XScreen)
        |  calls only
        v
DashboardBackend (gradezzy.ui.backend)
        |                         |
        v                         v
LiveBackend                   DemoBackend (gradezzy.ui.demo)
  one service method per        synthetic data in memory,
  call, no grading logic        used with --demo
        |
        v
ServiceRegistry -> GradingService, AnomalyService, ExportService, ...
```

- Screens never call a service directly. `LiveBackend` passes each call
  to exactly one service method, or reads `AppConfig`.
- Data crosses the interface as `gradezzy.model` classes. Where the model
  has no field for something a screen shows, a small view class lives in
  `gradezzy.ui.backend` (`RepairItem`, `ScoreAdjustment`, `RunRecord`,
  `SimilarityDetail`, `ImportItem`). The model is unchanged.
- A placeholder that returns an empty list, `null` or `0` shows an empty
  state that names the service and owner. It never shows an error or a
  made-up number.
- A feature with no service method yet is **proposed**. In live mode its
  button or card is hidden (`DashboardBackend.supports`), so the window
  never shows something that does nothing. Demo mode shows it working.

## When you finish a service method

1. Run `sh compile.sh` and `sh run.sh` (live mode). Open the screen listed
   for your capability below and check that your data appears.
2. In `src/gradezzy/ui/backend/Capability.java`, change your capability's
   status from `WAITING` to `WORKS_NOW`. A hidden button or card then
   appears in live mode by itself.
3. If you add a method for a **proposed** capability, tell the UI owner.
   The `LiveBackend` method that returns `ActionResult.notConnected(...)`
   then needs one line changed to call your method, and the status
   becomes `WAITING` or `WORKS_NOW`.
4. Run `sh run.sh --check` and `sh run.sh --selftest`.

## Capabilities

Status on commit `aafc5aa`, with later owner and status changes applied. "Works now" means the method returns real data
today.

| Capability | What it does for the instructor | Screens | DashboardBackend method | Service method | Owner | Status | Contract needed |
|---|---|---|---|---|---|---|---|
| Module, sections, rubric and limits | Shows the module, sections, marks and time limits set in config.properties. | Set up (Class), Settings | `getModuleCodes`, `getSectionCodes`, `getAssessment`, `getRunTimeoutSeconds`, `getCompileTimeoutSeconds` | `AppConfig, AssessmentFactory.create` | Jing Yuan | Works now | None |
| Check chosen files and folders | Tells you at once when a chosen file or folder cannot be read, how many students it holds, what it skips and which students appear twice. | Set up | `inspect` | `InputValidator.isReadableFile, isReadableDirectory`; `SubmissionLoader.listCandidates, listSkipped, isBatchArchive, listBatchEntries, listBatchSkipped, extractUsername` (through `SubmissionPreview`) | Seth, Damien | Works now | None |
| Import and grade a batch | Grades every submission of a section in one batch. | Set up, Grade | `startGrading` | `GradingService.startGrading` | Jing Yuan, with Seth, Damien, Jun Sheng, Zach and Huxley | Works now | None |
| Cancel grading | Stops a batch early and keeps the results already finished. | Grade | `cancelGrading` | `GradingService.cancel` | Jing Yuan | Works now | None |
| Retry failed submissions | Grades the submissions that failed or were cancelled again. | Review | `retryFailedSubmissions`, `canRetryFailed` | `GradingService.retryFailed` | Jing Yuan | Works now | None; only the last run started or resumed in this session can be retried, and Review shows a note for any other run. |
| Resume a cancelled run | Finishes a cancelled run without grading finished students again. | Grade, Runs | `resumeGrading` | `GradingService.resume` | Jing Yuan, Huxley | Works now | None; a run from an earlier session uses the score sheet and testers in the settings. |
| Progress and time left | Shows how far grading has got and about how long is left. | Grade | `GradingMonitor` (a `ProgressListener`) | `ProgressListener, ProgressEstimator` | Jing Yuan | Works now | None |
| List anomalies | Lists every problem the checks found, so you can review it. | Review (student panel), Statistics | `listAnomalies` | `AnomalyService.listAnomalies` | Jing Yuan | Works now | None |
| Approve and undo repairs | Lets you accept or undo each automatic repair of a working copy. | Review (student panel) | `approveRepair`, `rejectRepair` | `AnomalyService.approveFix, undoFix` | Jing Yuan, Jun Sheng | Works now | None; undo needs the run graded in this session. |
| Original and proposed repair values | Shows what each repair changed, before and after, side by side. | Review (student panel) | `listRepairs` | `AnomalyService.findFixRecord`, `FixRecord.getValueBefore, getValueAfter` | Jun Sheng | Works now | None; a fix made before GradeZZY was last opened shows no Before and After. |
| Undo a repair decision | Takes back an approve or undo decision on a repair. | Review (student panel) | `undoRepairDecision` | None yet | Jing Yuan, Jun Sheng | Proposed | A method that returns an approved or undone fix to Applied. |
| Mark flags reviewed or rejected | Records that you looked at a flag, without changing any mark. | Review (student panel) | `getDecision`, `setDecision` | `LiveBackend` keeps the decisions in memory | Jing Yuan | Works now (kept until GradeZZY closes) | To keep decisions after a restart: store them per anomaly, with an audit entry. |
| Search, filter and sort | Finds students by name and orders the results table. | Review, Export | `search` | `SearchService.search` | Jing Yuan | Works now | None |
| Search inside source code | Finds the students whose code contains some text. | Review (Code search) | `searchCode` | `SearchService.searchCode` | Jing Yuan | Works now (needs working copies) | None |
| Show working-copy files | Shows a student's files, so you can read the code behind a mark. | Review (code and test output) | `getFileTree`, `readSourceFile` | `FileTreeBuilder, FileUtils.readText` | Damien | Works now | None |
| Export CSV and JSON | Saves the score sheet as a CSV or JSON file. | Review, Export | `export` | `ExportService.export` | Jing Yuan, Huxley | Works now | None |
| Export only chosen students | Exports only the students you selected or filtered. | Review, Export | `export` (scope argument) | None yet | Jing Yuan, Huxley | Proposed | An export method that takes the visible or selected students instead of the whole run. |
| Statistics | Summarises the marks and common mistakes of a section. | Statistics, Export | `summarise` | `StatisticsService.summarise` | Jing Yuan, Huxley | Works now | None |
| Earlier runs | Lists earlier grading runs, so you can go back to one. | Runs, Statistics | `listRuns`, `getRun` | `HistoryService.listRuns` | Jing Yuan, Huxley | Works now | None |
| Back up now | Saves a copy of the saved results that you can restore later. | Runs (Backups) | `backupNow` | `HistoryService.backupNow` | Jing Yuan, Huxley | Works now | None |
| Restore a backup | Puts the saved results back to a backup file you choose. | Runs (Backups) | `restoreBackup` (a backup file chosen in live mode) | `HistoryService.restore` | Jing Yuan, Huxley | Works now | None; restore when no run is being graded. |
| Restore preview | Shows which run each backup holds and saves a recovery snapshot first, so a restore can be undone. | Runs (Backups) | `restore` (a run from the list) | None yet | Jing Yuan, Huxley | Proposed | A list of backup files with the run each holds, and a restore of one run that saves a recovery snapshot first. |
| Audit log | Keeps a dated record of every action, so changes can be checked later. | Runs (Activity) | `listAuditEntries`, `recordAudit` | `AuditService.record, listEntries` | Jing Yuan, Huxley | Works now | None |
| Export the audit log | Saves the audit log as a file to keep or share. | Runs (Activity) | none | None yet | Jing Yuan, Huxley | Proposed | Register AuditLogExporter in ApplicationContext and add an export method for the audit log. |
| Settings | Lets you check and change the settings from the app instead of a text file. | Settings | `getSetting`, `validateSetting`, `saveSettings` | `SettingsService` | Jing Yuan | Works now | None |
| Similarity review | Lists pairs of submissions with very similar code for you to review. | Export (Similar code) | `listSimilarPairs`, `isSimilarityEnabled` | `PlagiarismService.check` | Jing Yuan, Jun Sheng | Works now | None; runs when plagiarism.enabled is true. |
| Compare a similar pair | Shows the matching code of a pair side by side. | Export (Similar code) | `getSimilarityDetail` | None yet | Jun Sheng | Proposed | The compared files, matching excerpts and the excluded starter and tester files of one pair. |
| Use a tester folder | Grades with the tester files you already have in a folder. | Set up (Class files) | `startGrading` (tester folder in the batch) | `UploadedTesterSource` | Zach | Works now | None |
| Typed test cases | Builds testers from test cases you type in the app. | Set up (type test cases) | `startGrading` (typed cases in the batch) | `TypedTestCaseSource, TesterGenerator` | Zach | Waiting | TypedTestCaseSource.prepareTesters and TesterGenerator. |
| Retry one question | Runs one question's tester again without touching other marks. | Review (student panel) | `retryQuestion` | None yet | Jing Yuan, Zach | Proposed | Run the tester of one question again and replace only that question's result. |
| Retry one submission | Grades one student's submission again from the start. | Review (student panel) | `retrySubmission` | None yet | Jing Yuan | Proposed | Grade one chosen submission again, even when it did not fail. |
| Adjust a mark by hand | Lets you change a mark by hand with a reason, and undo it. | Review (student panel) | `adjustScore`, `undoAdjustment`, `listAdjustments` | None yet | Jing Yuan, Huxley | Proposed | Store a manual score with its reason and the earlier score, record it in the audit log, and allow undo. |
| More than one module | Lets you grade more than one module in the same app. | Set up, Statistics | `getModuleCodes` | `AppConfig` (`app.module.codes`), `AssessmentFactory.create` | Jing Yuan | Works now | None. Saved runs keep the module through `Section` once Huxley's repository saves runs. |
| Time taken per question | Shows how long each question's tester took to run. | Review (student panel) | none | None yet | Zach | Proposed | Record how long each tester ran in QuestionResult. |
| Export one student's report | Saves one student's marks and evidence as a report. | Review (student panel) | `export` (one student) | None yet | Jing Yuan, Huxley | Proposed | Register StudentReportExporter and add an export method for one submission. |

## Contract requests

These features are built and work in demo mode, but no service offers
what they need. Each one is hidden in live mode until the service exists.

| Request | Owner | What the UI needs |
|---|---|---|
| Undo a repair decision | Jing Yuan, Jun Sheng | A method that returns an approved or undone fix to Applied. |
| Flag review decisions after a restart | Jing Yuan, Huxley | Save the Open, Reviewed or Rejected decision per anomaly, with an audit entry, so it survives a restart. Today `LiveBackend` keeps them only until GradeZZY closes. A decision never changes a mark. |
| Retry one question | Jing Yuan, Zach | Run the tester of one question again and replace only that question's result. One tester run covers every case of a question, so the narrowest retry is a question, not a case. |
| Retry one submission | Jing Yuan | Grade one chosen submission again, even when it did not fail. |
| Manual mark changes | Jing Yuan, Huxley | Store a manual score with its reason and the earlier score, record it in the audit log, allow undo. |
| Export scope | Jing Yuan, Huxley | Export only the visible or selected students. Today the whole run is exported and the message says so. |
| Restore preview | Jing Yuan, Huxley | A list of backup files with the run each holds, and a restore of one run that saves a recovery snapshot first. Restoring a whole backup file is already wired to `HistoryService.restore`. |
| Form checks | Seth | Two checks for `InputValidator`: a mark that may be a decimal between 0 and a maximum (Adjust Score), and a Java method name (typed test cases). Until they exist the screens check these themselves. |
| Similar pair detail | Jun Sheng | The compared files, matching excerpts and the excluded starter and tester files of one pair. |
| Audit log export | Jing Yuan, Huxley | Register `AuditLogExporter` and add an export method for the audit log. |
| Student report export | Jing Yuan, Huxley | Register `StudentReportExporter` and add an export method for one submission. |
| Time per question | Zach | Record how long each tester ran in `QuestionResult`. |

## Where a backend method does more than one call

Most `LiveBackend` methods are one service call. Three are thin glue:

- `saveSettings` checks every value with `SettingsService.validate`,
  then calls `updateSetting` for each one and `save` once, because
  `SettingsService` has no single "check and save all" method. Nothing
  is written unless every value is valid.
- `listRepairs` keeps the anomalies from `AnomalyService.listAnomalies`
  that carry a fix and pairs each with the values to show; the values
  wait for the Repair values contract above.
- `inspect` turns `InputValidator`'s yes or no answers, and the file
  extension, into the import states the Set up step shows (accepted,
  cannot be used, duplicate).

## What each owner will see

- **Seth.** Set up marks unreadable files before grading. The Review
  student panel lists what the validators report, in plain words.
- **Damien.** Review's "Open code and test output" shows a student's
  working-copy files, without hidden files and folders such as
  `.gradezzy-backup`, and the tester output.
- **Jun Sheng.** The Review student panel (fixes to approve or undo,
  flags from the detectors) and Export's Similar code card (pairs from
  `PlagiarismService.check`).
- **Zach.** Grade shows what each student is going through. Review shows
  per-question marks, compile failures, timeouts and one square per test
  case from `QuestionResult` and `TestCaseResult`.
- **Jing Yuan.** Every service call goes through your `ServiceRegistry`.
  Grade offers Cancel and Resume; Runs offers Resume and Restore;
  Settings saves `config.properties` end to end.
- **Huxley.** Export (score sheet CSV and JSON), Statistics (bell curve,
  per question, status, compare sections), Runs (runs with their
  averages, Back up now, Restore) and Runs' Activity view (the audit
  log).

## The window

One window, `gradezzy.ui.flow.FlowWindow`, with four tabs along the top.
Every screen is plain JavaFX code (no FXML) styled by `css/flow.css` on
the AtlantaFX Cupertino theme.

| Tab | Screen classes | What it does |
|---|---|---|
| Marking: 1 Set up | `SetUpScreen`, `TestCasesSheet` | Module and section, the zip files, the score sheet, testers and starter files, the grading settings in use. |
| Marking: 2 Grade | `GradeScreen` | Progress, time left, a live bell curve, Cancel and Resume. |
| Marking: 3 Review | `ReviewScreen`, `StudentPanel`, `AdjustMarkSheet`, `CodeSheet` | The results table, search (names or code), fixes and flags, mark changes, grading again. |
| Marking: 4 Export | `ExportScreen`, `SimilaritySheet` | Results at a glance, the score sheet and JSON, similar code. |
| Statistics | `StatisticsScreen`, `SectionView`, `CompareView` | Bell curve, each question, where students are, compare up to three sections. |
| Runs | `RunsScreen` | Earlier runs, backups and the activity log. |
| Settings | `SettingsScreen`, `SettingField` | Every setting in `config.properties`, grouped and searchable. |
| Help (top right) | `HelpSheet` | How the window works, shortcuts, where files are saved. |

Keyboard: Cmd or Ctrl + Enter presses the blue button, Cmd or Ctrl + 1
to 4 opens a step, Esc closes a sheet.

## Packages under `gradezzy.ui`

| Package | What is in it |
|---|---|
| `gradezzy.ui` | `MainApp`, `ThemeManager`, `GradingTask`, `FileTreeBuilder`, `DragDropHandler` |
| `gradezzy.ui.flow` | The window and every screen, sheet and chart (`BellCurveChart`, `CompareChart`, `StatusRing`), plus `Parts` (shared pieces) and `MarkStats` (average, median, spread) |
| `gradezzy.ui.backend` | `DashboardBackend`, `LiveBackend`, `Capability`, view classes, `ResultState`, `ReviewRules`, `PipelineStages` |
| `gradezzy.ui.demo` | `DemoBackend` and the synthetic data it shows |
| `gradezzy.ui.check` | The developer flags: `FlowCheck` (`--check`, `--snapshot`), `FlowSelfTest` (`--selftest`), `FlowWindowTest` with `RobotDriver` and `SystemMouse` (`--windowtest`), `PngWriter` |

`compile.sh` and `compile.bat` list these folders in `UI_SOURCES`. Every
`javafx` import stays in `gradezzy.ui` and its subpackages, so
`sh compile.sh core` still compiles the grading core without JavaFX.

## Submission ingestion behavior

Each ZIP passed to `SubmissionLoader.loadSubmissions` belongs to the student
named by its outer filename, even when it contains other ZIPs, unless it is a
bulk zip: it holds at least one zip that is not named like a question folder
and not inside one, holds no question folder at its top or one folder down, and
its own name does not start with the student prefix. Each such zip inside a
bulk zip is one student. A chosen folder
gives one student per zip and per unzipped submission folder inside it; other
files and the starter folder are skipped. The Set up step previews the same
rules through `SubmissionLoader.listCandidates`, `listSkipped`,
`isBatchArchive`, `listBatchEntries` and `listBatchSkipped` (the files a bulk
zip skips).

One upload per username (case-insensitive) is graded: the one without a copy
number such as ` (1)`, else the lowest copy number; on a tie, the one whose
username is already in lower case, as on the roster, else the first chosen.
The other uploads are not added to the run; the graded student gets a
`DUPLICATE_SUBMISSION` warning naming each of them, which Retry keeps. The CSV
export matches usernames in any case. The first upload without a username
remains in the run as a failed submission with an ingestion anomaly; each
other upload without one is named in a warning on it, so no two submissions
share the empty username. It never creates a working copy and Retry leaves its
rejection intact.

`LiveBackend` passes the selected roster through `GradingTask` to
`GradingService`; console runs use the configured score sheet. Roster lookup is
case-insensitive, and unmatched usernames raise `UNKNOWN_STUDENT` while
remaining eligible for grading. ZIP extraction accepts Windows separators and
`./` entries while rejecting traversal, absolute paths and unsafe filenames.

The regression checks are documented in [tests/README.md](../tests/README.md).
