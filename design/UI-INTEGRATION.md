# GradeZZY UI: how your backend plugs in

For everyone writing a backend class. The interface is finished, and every
screen already calls the service method your work fills in. This page says
where your work appears and what each screen expects from it.

## Run it

| Command (macOS or Git Bash) | Windows | What it does |
|---|---|---|
| `sh compile.sh` | `compile.bat` | Compiles everything, including the new `ui` subpackages. |
| `sh run.sh` | `run.bat` | The real application, calling the real services. |
| `sh run.sh --demo` | `run.bat --demo` | The same screens with synthetic data. Never reads, grades or saves real files. |
| `sh run.sh --check` | `run.bat --check` | Loads every screen with the live and demo backends in both themes, then exits. |
| `sh run.sh --selftest` | `run.bat --selftest` | Runs the seven demo flows without a window and prints PASS or FAIL for each. |
| `sh run.sh --snapshot [folder] [SCREENS] [--fullpage]` | same | Saves PNGs of every screen (or a comma-separated list such as `RESULTS,PROGRESS`) in both themes at 1200x800, 960x640, 800x533 and 600x400. With `--fullpage`, each screen that scrolls is also saved as tall as its whole page (`-full` files), so content below the fold can be checked. Default folder: `workspace/ui-snapshots` (ignored by git). |
| `sh run.sh --windowtest [folder]` | `run.bat --windowtest [folder]` | Opens the real window and drives it with operating-system mouse and keyboard input: the sidebar, every shortcut, Tab order with the focus ring read from the screen, tooltips, the seven demo flows, drag and drop from another window, three window sizes, then every screen in live mode. Prints PASS or FAIL per check and saves screen pictures to `workspace/ui-window-test`. Needs a desktop; leave the mouse and keyboard alone for about three minutes. |

Run `sh run.sh --check` after any change. It catches a broken FXML file,
stylesheet or controller in both modes.

## How a screen reaches your code

```
Screen controller (gradezzy.ui.XController)
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

- Controllers never call a service directly. `LiveBackend` passes each call
  to exactly one service method, or reads `AppConfig`.
- Data crosses the interface as `gradezzy.model` classes. Where the model
  has no field for something a screen shows, a small view class lives in
  `gradezzy.ui.backend` (`RepairItem`, `ScoreAdjustment`, `RunRecord`,
  `SimilarityDetail`, `ImportItem`). The model is unchanged.
- A placeholder that returns an empty list, `null` or `0` shows an empty
  state that names the service and owner. It never shows an error or a
  made-up number.
- A feature with no service method yet is **proposed**. Its screen shows a
  "Not connected yet" panel naming the owner and the contract needed, and
  changes nothing.

## When you finish a service method

1. Run `sh compile.sh` and `sh run.sh` (live mode). Open the screen listed
   for your capability below and check that your data appears.
2. In `src/gradezzy/ui/backend/Capability.java`, change your capability's
   status from `WAITING` to `WORKS_NOW`. The Help screen's Backend
   connections table and the Overview banner update by themselves.
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
| Module, sections, rubric and limits | Shows the module, sections, marks and time limits set in config.properties. | Context bar, Modules, Assessments | `getModuleCodes`, `getSectionCodes`, `getAssessment`, `getRunTimeoutSeconds`, `getCompileTimeoutSeconds` | `AppConfig, AssessmentFactory.create` | Jing Yuan | Works now | None |
| Check chosen files and folders | Tells you at once when a chosen file or folder cannot be read. | Submissions | `inspect` | `InputValidator.isReadableFile, isReadableDirectory` | Seth | Waiting | Until these return true for readable paths, every chosen file is reported as unreadable. |
| Import and grade a batch | Grades every submission of a section in one batch. | Submissions, Grading Progress | `startGrading` | `GradingService.startGrading` | Jing Yuan, with Seth, Damien, Jun Sheng, Zach and Huxley | Waiting | The pipeline stages that load, check, fix, compile, run and score submissions. |
| Cancel grading | Stops a batch early and keeps the results already finished. | Grading Progress | `cancelGrading` | `GradingService.cancel` | Jing Yuan | Works now | None |
| Retry failed submissions | Grades the submissions that failed or were cancelled again. | Grading Progress | `retryFailedSubmissions` | `GradingService.retryFailed` | Jing Yuan | Works now | None |
| Resume a cancelled run | Finishes a cancelled run without grading finished students again. | Grading Progress | `resumeGrading` | `GradingService.resume` | Jing Yuan, Huxley | Waiting | Loading the run from the repository and grading the submissions that are still pending. |
| Progress and time left | Shows how far grading has got and about how long is left. | Grading Progress, context bar | `GradingMonitor` (a `ProgressListener`) | `ProgressListener, ProgressEstimator` | Jing Yuan | Works now | None |
| List anomalies | Lists every problem the checks found, so you can review it. | Review Anomalies, Overview, sidebar badge, Statistics | `listAnomalies` | `AnomalyService.listAnomalies` | Jing Yuan | Works now | None |
| Approve and undo repairs | Lets you accept or undo each automatic repair of a working copy. | Validation | `approveRepair`, `rejectRepair` | `AnomalyService.approveFix, undoFix` | Jing Yuan, Jun Sheng | Waiting | FixManager approve and undo, and the AnomalyService methods that call them. |
| Original and proposed repair values | Shows what each repair changed, before and after, side by side. | Validation | `listRepairs` | None yet | Jun Sheng | Proposed | A way to read each FixRecord's original and proposed value through AnomalyService. |
| Undo a repair decision | Takes back an approve or undo decision on a repair. | Validation | `undoRepairDecision` | None yet | Jing Yuan, Jun Sheng | Proposed | A method that returns an approved or undone fix to Applied. |
| Mark flags reviewed or rejected | Records that you looked at a flag, without changing any mark. | Review Anomalies, Similarity Review | `getDecision`, `setDecision` | None yet | Jing Yuan | Proposed | Store an Open, Reviewed or Rejected decision for each anomaly, with undo and an audit entry. |
| Search, filter and sort | Finds students by name and orders the results table. | Results | `search` | `SearchService.search` | Jing Yuan | Works now | None |
| Search inside source code | Finds the students whose code contains some text. | Results (Code) | `searchCode` | `SearchService.searchCode` | Jing Yuan | Works now (needs working copies) | None |
| Show working-copy files | Shows a student's files, so you can read the code behind a mark. | Student Evidence (Source) | `getFileTree`, `readSourceFile` | `FileTreeBuilder, FileUtils.readText` | Damien | Waiting | FileUtils.readText, so a chosen source file can be shown. |
| Export CSV and JSON | Saves the score sheet as a CSV or JSON file. | Results | `export` | `ExportService.export` | Jing Yuan, Huxley | Waiting | ExportService.export and the CSV and JSON exporters. |
| Export only chosen students | Exports only the students you selected or filtered. | Results | `export` (scope argument) | None yet | Jing Yuan, Huxley | Proposed | An export method that takes the visible or selected students instead of the whole run. |
| Statistics | Summarises the marks and common mistakes of a section. | Statistics | `summarise` | `StatisticsService.summarise` | Jing Yuan, Huxley | Waiting | StatisticsService.summarise and StatisticsCalculator. |
| Earlier runs | Lists earlier grading runs, so you can go back to one. | History, Overview | `listRuns`, `getRun` | `HistoryService.listRuns` | Jing Yuan, Huxley | Waiting | Saved runs from the repository. |
| Back up now | Saves a copy of the saved results that you can restore later. | History | `backupNow` | `HistoryService.backupNow` | Jing Yuan, Huxley | Waiting | BackupManager and HistoryService.backupNow. |
| Restore a backup | Puts the saved results back to a backup file you choose. | History | `restoreBackup` (a backup file chosen in live mode) | `HistoryService.restore` | Jing Yuan, Huxley | Waiting | HistoryService.restore: close the repository, copy the backup over the database with BackupManager.restore, then open the repository again. |
| Restore preview | Shows which run each backup holds and saves a recovery snapshot first, so a restore can be undone. | History | `restore` (a run from the list) | None yet | Jing Yuan, Huxley | Proposed | A list of backup files with the run each holds, and a restore of one run that saves a recovery snapshot first. |
| Audit log | Keeps a dated record of every action, so changes can be checked later. | Audit Log, Overview, Student Evidence (History) | `listAuditEntries`, `recordAudit` | `AuditService.record, listEntries` | Jing Yuan, Huxley | Waiting | Saving and reading audit entries in the repository. |
| Export the audit log | Saves the audit log as a file to keep or share. | Audit Log | none | None yet | Jing Yuan, Huxley | Proposed | Register AuditLogExporter in ApplicationContext and add an export method for the audit log. |
| Settings | Lets you check and change the settings from the app instead of a text file. | Settings | `getSetting`, `validateSetting`, `saveSettings` | `SettingsService` | Jing Yuan | Works now | None |
| Similarity review | Lists pairs of submissions with very similar code for you to review. | Similarity Review | `listSimilarPairs`, `isSimilarityEnabled` | `PlagiarismService.check` | Jing Yuan, Jun Sheng | Waiting | TokenSimilarityDetector and PlagiarismService.check. |
| Compare a similar pair | Shows the matching code of a pair side by side. | Similarity Review | `getSimilarityDetail` | None yet | Jun Sheng | Proposed | The compared files, matching excerpts and the excluded starter and tester files of one pair. |
| Use a tester folder | Grades with the tester files you already have in a folder. | Submissions, Assessments | `startGrading` (tester folder in the batch) | `UploadedTesterSource` | Zach | Waiting | UploadedTesterSource.prepareTesters. |
| Typed test cases | Builds testers from test cases you type in the app. | Assessments | `startGrading` (typed cases in the batch) | `TypedTestCaseSource, TesterGenerator` | Zach | Waiting | TypedTestCaseSource.prepareTesters and TesterGenerator. |
| Retry one question | Runs one question's tester again without touching other marks. | Student Evidence | `retryQuestion` | None yet | Jing Yuan, Zach | Proposed | Run the tester of one question again and replace only that question's result. |
| Retry one submission | Grades one student's submission again from the start. | Student Evidence | `retrySubmission` | None yet | Jing Yuan | Proposed | Grade one chosen submission again, even when it did not fail. |
| Adjust a mark by hand | Lets you change a mark by hand with a reason, and undo it. | Student Evidence | `adjustScore`, `undoAdjustment`, `listAdjustments` | None yet | Jing Yuan, Huxley | Proposed | Store a manual score with its reason and the earlier score, record it in the audit log, and allow undo. |
| More than one module | Lets you grade more than one module in the same app. | Modules, context bar | `getModuleCodes` | `AppConfig` (`app.module.codes`), `AssessmentFactory.create` | Jing Yuan | Works now | None. Saved runs keep the module through `Section` once Huxley's repository saves runs. |
| Time taken per question | Shows how long each question's tester took to run. | Student Evidence | none | None yet | Zach | Proposed | Record how long each tester ran in QuestionResult. |
| Export one student's report | Saves one student's marks and evidence as a report. | Student Evidence | `export` (one student) | None yet | Jing Yuan, Huxley | Proposed | Register StudentReportExporter and add an export method for one submission. |

## Contract requests

These screens are built and work in demo mode, but no service offers
what they need. Each one shows "Not connected yet" in live mode.

| Request | Owner | What the UI needs |
|---|---|---|
| Repair values | Jun Sheng | Read each `FixRecord`'s original and proposed value through `AnomalyService`, so Validation can show them side by side. |
| Undo a repair decision | Jing Yuan, Jun Sheng | A method that returns an approved or undone fix to Applied. |
| Flag review decisions | Jing Yuan | Store an Open, Reviewed or Rejected decision per anomaly, with undo and an audit entry. A decision never changes a mark. |
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
  extension, into the import states the Submissions screen shows
  (accepted, cannot be used, duplicate).

## What each owner will see

- **Seth.** Submissions screen. Until `InputValidator` returns true
  for readable paths, every chosen file is reported as unreadable and
  Start Grading stays unavailable. That is expected. Validation and
  Review Anomalies list the anomalies the validators report.
- **Damien.** Student Evidence (Source) shows the working-copy tree, and
  a chosen file's text once `FileUtils.readText` works.
- **Jun Sheng.** Validation (approve, reject), Review Anomalies (flags
  from the detectors), Similarity Review (pairs from
  `PlagiarismService.check`).
- **Zach.** Grading Progress shows each stage as the pipeline reports it.
  Results and Student Evidence show per-question marks, compile failures
  and timeouts from `QuestionResult` and `TestCaseResult`. The Cases tab
  reads expected and actual values from details shaped like
  `Failed: expected X but got Y`.
- **Jing Yuan.** Every service call goes through your `ServiceRegistry`.
  Grading Progress shows Cancel, Retry and Resume; Settings already works
  end to end. Note that Save on the Settings screen writes
  `config.properties`. Restore on History calls `HistoryService.restore`
  with the backup file chosen in live mode; until that method is written
  the screen says Not connected yet.
- **Huxley.** Results (Export Score Sheet, Export JSON), Statistics,
  History (runs, Back Up Now) and Audit Log. Exports, Back Up Now,
  Restore and the similarity check run on a background thread, so a
  slow service never freezes the window.

## Screens

| Screen | Screen constant | FXML | Controller | Reached from |
|---|---|---|---|---|
| Overview | `OVERVIEW` | `overview.fxml` | `OverviewController` | Sidebar, Shortcut+1 |
| Modules | `MODULES` | `modules.fxml` | `ModulesController` | Sidebar, Shortcut+2 |
| Assessments | `TEST_CASES` | `test-cases.fxml` | `TestCaseController` | Sidebar, Shortcut+3 |
| Submissions | `UPLOAD` | `upload.fxml` | `UploadController` | Sidebar, Shortcut+4 |
| Validation | `VALIDATION` | `validation.fxml` | `ValidationController` | Submissions, Review Anomalies |
| Grading Progress | `PROGRESS` | `progress.fxml` | `ProgressController` | Start Grading; the sidebar Grading row while a run goes or a cancelled run can be resumed |
| Review Anomalies | `ANOMALY_LOG` | `anomaly-log.fxml` | `AnomalyLogController` | Sidebar, Shortcut+5 |
| Results | `RESULTS` | `results.fxml` | `ResultsController` | Sidebar, Shortcut+6 |
| Student Evidence | `STUDENT_REPORT` | `student-report.fxml` | `StudentReportController` | Results (double-click, Enter, inspector) |
| Statistics | `STATISTICS` | `statistics.fxml` | `StatisticsController` | Sidebar, Shortcut+7 |
| Similarity Review | `PLAGIARISM` | `plagiarism.fxml` | `PlagiarismController` | Sidebar, Shortcut+8, Review Anomalies |
| History | `HISTORY` | `history.fxml` | `HistoryController` | Sidebar, Shortcut+9 |
| Audit Log | `AUDIT_LOG` | `audit-log.fxml` | `AuditLogController` | Sidebar, Overview |
| Settings | `SETTINGS` | `settings.fxml` | `SettingsController` | Sidebar footer, Shortcut+Comma |
| Help | `HELP` | `help.fxml` | `HelpController` | Sidebar footer, F1 |

## Packages added under `gradezzy.ui`

| Package | What is in it |
|---|---|
| `gradezzy.ui` | Controllers, `MainApp`, `Screen`, `ScreenNavigator`, `ShellBuilder`, `UiState`, `GradingMonitor`, `RunFollower`, `ThemeManager`, `AppShell`, `SortOrder`, `GradingTask`, `FileTreeBuilder`, `DragDropHandler` |
| `gradezzy.ui.backend` | `DashboardBackend`, `LiveBackend`, `Capability`, view classes, `ResultState`, `ReviewRules`, `PipelineStages` |
| `gradezzy.ui.demo` | `DemoBackend` and the synthetic data it shows |
| `gradezzy.ui.component` | Reusable parts: `StatusBadge`, `EmptyState`, `NotConnectedPanel`, `MetricTile`, `Sheet`, `Icon`, `Labels`, `QuestionScoreCell`, `Responsive`, `Tone` |
| `gradezzy.ui.view` | Parts of single screens that keep each controller short: the window's `Sidebar`, `ContextBar`, `SheetLayer`, `ToastLayer` and `ScreenShortcuts`; the Results columns and inspector; the Student Evidence tabs; the Review Anomalies, Validation and Similarity tables and inspectors; the Statistics charts |
| `gradezzy.ui.check` | The developer flags: `ScreenCheck` (`--check`), `SelfTest` (`--selftest`), `SnapshotWriter` and `PngWriter` (`--snapshot`), and the real-window test `WindowTest`, `WindowScenarios`, `WindowFlows`, `RobotDriver` and `SystemMouse` (`--windowtest`) |

`compile.sh` and `compile.bat` list these folders in `UI_SOURCES`. Every
`javafx` import stays in `gradezzy.ui` and its subpackages, so
`sh compile.sh core` still compiles the grading core without JavaFX.
