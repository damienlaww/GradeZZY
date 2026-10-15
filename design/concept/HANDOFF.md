# GradeZZY design concept and handoff

Design only · 15 October 2026 · synthetic identities and evidence throughout.

Open `index.html` for the interactive concept or `boards.html` for fixed logical
viewport review boards. No build or network connection is needed. Reload / Reset
Demo restores the fixture. All changes live only in page memory. No grading,
file parsing, database access, or real export is implemented.

## Repository review and scope

Reviewed the local checkout with origin https://github.com/damienlaww/GradeZZY,
commit `afee9a2`: README product, architecture, UI, configuration and feature
sections; all configuration values; `design/gradezzy-design.html`; `Screen.java`;
all twelve FXML/controller mappings; ProgressController and
StudentReportController handlers. GitHub retrieval was unavailable, so this
handoff describes the local revision, not a verified latest remote revision.
The repository describes itself as a skeleton. A screen or handler existing is
not evidence that its feature works.

Only new files in `design/concept/` were created. Existing design, user prompt,
Java, FXML, controllers, services, models, CSS, configuration, libraries and
compile/run scripts are unchanged. The prototype has no production imports or
network requests and does not read dropped files.

### Current concept audit

| Existing strength or issue | Design response |
|---|---|
| Stable sidebar, compact tables and contextual inspector | Retain this structure, increase action targets and make selected context explicit. |
| Exact 3/3/5/5/4 rubric; visible retained marks and review flags | Keep numerical columns, add distinct ungraded / zero / failure / review / completed labels. |
| Working-copy corrections and approve/undo concepts | Add original/proposed comparison, reject and scoped bulk approval. |
| Actual sample usernames and names embedded in the current HTML | Replace all identities and evidence with synthetic fixtures. Do not reuse private sample files. |
| macOS traffic-light decoration, floating shadow, large pill controls | Flat desktop shell without platform window controls; modest 5–6px corners. |
| Import emphasizes a single drop area; roster and starter readiness not explicit | Four independent input groups and explicit disabled Start Grading explanation. |
| Navigation omits Overview, Modules, Assessments and persistent Help | Add proposed destinations, clearly record missing production mappings below. |
| Several actions only show a toast saying export/backup succeeded | Label previews as simulated and state no file was saved. |
| Student detail remains an inspector; limited case recovery | Separate evidence view with six sections, narrow retry scope and manual-score reason. |
| 24–30px controls; keyboard selection and focus need review | 44px buttons, native inputs, explicit focus ring, native modal focus handling. |
| Existing dark theme and reduced-motion CSS are useful | Preserve both, add an explicit animation-disabled state. |
| History and audit are static examples | Record prototype decisions dynamically; restore shows consequences and recovery snapshot. |

## Navigation and main grading journey

Persistent navigation: Overview → Modules → Submissions → Assessments → Review
Anomalies → Results → Statistics → History → Audit Log → Settings → Help.
These are destinations, not a forced wizard. Module, section, assessment and run
stay in the context header. Validation and progress have explicit contextual
shortcuts. Similarity is reached from anomaly review or detection settings.

Main journey:

1. Overview / Modules: choose CS102 and section G1.
2. Assessments: inspect Lab Test, rubric and tester requirements.
3. Submissions: select six demo ZIPs; replace the invalid ZIP; add roster,
   testers and starter resources; remove any optional duplicate.
4. Validation: compare originals with working-copy proposals. Approve, reject,
   undo or approve selected. Missing answers are not generated.
5. Start Grading: stage, processed count, issue count and timing remain visible.
   A timeout and compile error are recorded without stopping later submissions.
6. Cancel with an explanation of retained results, then Resume. Completed
   submissions are not scheduled again by the simulated counter.
7. Review flags and compare evidence. Rejecting a flag changes no mark.
8. Results → selected student inspector → Student evidence → Cases.
9. learner.bravo starts at 19/20. Q1a Case 2 shows a 2,000 ms timeout.
   Targeted retry simulates a pass and updates Q1a from 2 to 3; other marks stay.
10. Adjust Q1a with a required reason; Saved is explicit and Undo restores it.
11. Export scoped visible or selected results as a CSV/JSON preview.
12. Statistics, History and Audit Log support interpretation and recovery.

Returning from student evidence restores results search, sort and status filter.
Theme switching preserves selection and decisions. State is not durable across
reloads. The fixture starts with five graded students and one ungraded student,
so all score states are visible without first running the import walkthrough.

## Screen inventory

| View | Purpose / primary action | Important states | Data shown |
|---|---|---|---|
| Overview | Orient instructor / Import Submissions | Recent activity, pending run, empty sections | Module, section, assessment, counts |
| Modules | Group sections / Open Assessment | G1 populated; G2–G10 empty; DEMO module empty | Configured CS102, ten sections |
| Assessments | Verify rubric / Prepare Submissions | Uploaded tester context, proposed case execution | Exact question folders, files, testers and maximums |
| Submissions | Preflight / Start Grading | Empty, six selected, invalid ZIP, replaced ZIP, duplicate, warning, missing supporting inputs, ready | Four input groups |
| Validation | Inspect working-copy change / Approve Selected | Pending, approved, rejected, undone, missing-file blocked | Folder, filename, required file, header name; before/after |
| Progress | Explain batch state / Cancel, Resume, then Review | Running, isolated failures, cancelled, completed | Nine pipeline stages, retained count, elapsed, estimate |
| Review anomalies | Triage / Mark Selected Reviewed | Open, reviewed, rejected, undo, empty filter | Seven requested anomaly types; type/severity/search/sort |
| Results | Compare marks / Export Results | Ungraded, numeric zero, compile failure, warning, completed; empty filters | Six learners, Q1a /3, Q1b /3, Q2a /5, Q2b /5, Q3 /4, Total /20 |
| Student evidence | Explain one result / Retry Failed Case | Timeout, retry passed, manual Saved, Undo, invalid reason | Cases, Source, Logs, Repairs, History, Anomalies |
| Statistics | Interpret cohort / Preview Summary Export | Populated, no data; incomplete totals excluded | Completion, mean, median, pass rate, distribution, difficulty, flags, mistakes |
| History | Recover / Preview Restore | Current and earlier snapshot; restore confirmation | Timestamps, backup state, consequences, recovery snapshot |
| Audit Log | Trace actions / Preview Audit Export | Seeded automatic repair; dynamic approve/reject/retry/score/export intentions | Time, actor, action, reason/detail |
| Settings | Configure / Save Preview Settings | Valid and invalid timeout, light/dark, motion off | Grading, execution, detection, export, storage, appearance |
| Help | Learn and recover / Start Import Walkthrough | Walkthroughs, keyboard reference, failure advice | Local instructions and handoff link |
| Similarity review | Compare fairly / Mark Pair Reviewed | Disabled-by-default feature, illustrative flagged pair | Token %, files, excluded starter/tester resources, neutral explanation |

Every export and restore action is a design preview. A full-submission retry
records an intent only. Supporting input Browse buttons load named synthetic
fixtures, not a real file picker. Source and logs are illustrative excerpts.
Similarity does not calculate tokens. No production behavior is implied.

## Design tokens

| Token | Light | Dark | Usage |
|---|---|---|---|
| Primary | #1D4ED8 | #60A5FA | One dominant action |
| Primary hover | #1E40AF | #93C5FD | Pointer feedback |
| Primary pressed | #1E3A8A | #60A5FA | Pressed state; dark retains supplied accent |
| Primary soft | #DBEAFE | #172554 | Selected rows and feedback |
| Focus | #60A5FA | #93C5FD | 3px visible focus outline |
| Background | #F8FAFC | #0B1220 | Application canvas |
| Surface | #FFFFFF | #111827 | Tables and content |
| Raised | #F1F5F9 | #172033 | Headers, excerpts, secondary areas |
| Sidebar | #111827 | #080D18 | Stable navigation |
| Text | #111827 | #F8FAFC | Normal content |
| Secondary | #64748B | #94A3B8 | Supporting text |
| Divider | #DCE3ED | #334155 | Nonessential grouping |
| Control boundary | #64748B | #94A3B8 | Meaningful control edges, stronger than dividers |
| On primary | #FFFFFF | #0B1220 | Text on filled primary button |
| Success | #18864B | #18864B | Grading success icon |
| Warning | #B45309 | #B45309 | Review icon |
| Error | #C62828 | #C62828 | Failure icon |
| Information | #0369A1 | #0369A1 | Informational icon |

Semantic status text uses the normal text color. Colored symbols communicate
status redundantly with text; on dark surfaces they sit on white chips to keep
the exact supplied semantic values distinguishable. Do not use the supplied
dark semantic hues as small normal text directly on a dark background.

Typography: system UI (Segoe UI / Apple system), 14px body, 12px supporting
text, 16px panel titles, 26px page title; monospace 12px for paths and logs.
Numerical tables use tabular figures. Spacing: 4/8/12/16/20/24/28px. Buttons:
44px minimum height, 5px radius. Panels: 6px radius, 1px divider, no shadow.
Sidebar: 202px at wide sizes, 176px below 1100px. Inspector: 275px when beside
the table; below 1100px it moves beneath the list. Only the table scrolls
horizontally when all rubric columns cannot fit.

### Contrast caveat and treatment

#64748B is about 4.76:1 on white but about 4.34:1 on #F1F5F9. Consequently,
secondary text on raised light surfaces uses a stronger #59677C override.
#60A5FA focus on white is below 3:1. A dark inner focus edge supplements the
requested blue outer ring on light surfaces. Required borders use the control
boundary token, not the pale divider. These adjustments preserve the supplied
palette while meeting the intended contrast on actual surfaces.

## Nielsen heuristic mapping

| Heuristic | Visible evidence |
|---|---|
| 1. Visibility of status | Context run state, nine-stage progress, retained count, timing, cancellation summary, Saved, persistent export-preview feedback |
| 2. Real-world match | Module, section, Lab Test, roster, working copy, question marks, instructor review |
| 3. Control and freedom | Back to Results, Cancel / Resume, repair Undo, flag Undo, score Undo, restore recovery snapshot |
| 4. Consistency | Same shell, fields, status symbols, table spacing and button hierarchy; Cancel before primary action in dialogs |
| 5. Error prevention | Required input preflight, disabled-state explanation, constrained score and required reason, scoped retry and restore confirmations |
| 6. Recognition | Persistent module/section/assessment/run; explicit rubric, selected student, retained list filters and named evidence case |
| 7. Efficiency | Search, sort, type/severity filters, selection, bulk approval/review, drag/drop concept and Ctrl/Cmd+K |
| 8. Minimalism | One title and dominant page action; table and inspector; detailed logs/source behind evidence sections |
| 9. Recovery | Timeout specifies case, expected/actual, limit, preserved score and next step; invalid ZIP has Replace; missing file is never invented |
| 10. Help | Persistent Help, timeout advice, settings defaults and inline validation, seven walkthroughs |

## Accessibility, desktop and consistency acceptance checklist

Implemented in concept source:

- [x] Labelled native buttons, inputs and selects; native tab order.
- [x] Visible focus indication; native modal focus containment and Escape.
- [x] Text alongside status symbols; no essential hover-only content.
- [x] 44px buttons and main controls; labelled checkbox areas where practical.
- [x] One meaningful page heading; contextual evidence headings.
- [x] Polite message region; validation errors use alert regions.
- [x] Reduced-motion CSS plus explicit animations-disabled state.
- [x] Long synthetic learner name wraps; paths break safely; logs bounded and selectable.
- [x] Empty section/module and no-search-match states.
- [x] Table overflow contained; inspector stacks at narrower desktop sizes.
- [x] Charts have direct labels, numeric values and prose summaries; no hover dependency.
- [x] Persistent context and list/detail return behavior.

Required later validation, not claimed as passed:

- [ ] Keyboard and screen-reader walkthrough on Windows and macOS JavaFX.
- [ ] Measure every rendered foreground/background pair, including focus edges,
  selected navigation, disabled controls and high-contrast operating-system mode.
- [ ] Verify focus after navigation, dialogs, async progress and virtualized rows.
- [ ] Audit live announcements: announce stage transitions and completion, not
  every timer tick. Retain keyboard focus when updating background status.
- [ ] Test 1200×800 and 960×800 at 100%, 125%, 150%, 200% OS scaling.
  A 1200×800 physical window implies about 1200×800, 960×640, 800×533,
  600×400 logical space respectively. Avoid double scaling JavaFX controls.
- [ ] At reduced logical widths, collapse navigation to a labelled menu, move
  inspector below list, keep full rubric via table scrolling, preserve search.
- [ ] Use TableView virtualization and paged/incremental data for 500+ learners;
  never render every log line or reconstruct the table on each progress tick.
- [ ] Test real long Unicode names, paths, 100,000-character output and empty data.
- [ ] Distinguish completed zero marks from execution failure in export metadata.
- [ ] Confirm keyboard shortcuts do not conflict with native platform conventions.

`boards.html` includes 1200×800 light results, 1200×800 dark evidence, 960×800
results, 1200×800 import, 960×800 dark anomaly review and a 600×400 effective
viewport. These are live layout boards, not verified screenshots. Browser
visual inspection was blocked by the embedded browser's local-file URL policy.
Native display scaling and accessibility behavior remain unverified.

## JavaFX implementation handoff (future work only)

| Component | Likely controls | Existing screen / controller |
|---|---|---|
| Shell and context | BorderPane, VBox navigation, HBox/ToolBar, ComboBox | main.fxml / MainController |
| Module/section tree | TreeView, ListView, empty-state VBox | Proposed; no dedicated Screen entry |
| Assessment rubric / testers | TableView, TabPane, FileChooser | test-cases.fxml / TestCaseController |
| Import inputs | VBox/GridPane, drag events, FileChooser, DirectoryChooser | upload.fxml / UploadController |
| Validation and anomaly list | TableView multi-selection, SplitPane, comparison GridPane | anomaly-log.fxml / AnomalyLogController; separate repair view proposed |
| Progress | ProgressBar, Label, ListView, Button | progress.fxml / ProgressController |
| Results / inspector | TableView, SplitPane, selection model | results.fxml / ResultsController |
| Evidence sections | TabPane, TableView, read-only TextArea, TreeView, ScrollPane | student-report.fxml / StudentReportController |
| Score/retry dialogs | Dialog, Spinner/TextField, required TextArea | Proposed handlers; no score-adjustment handler currently |
| Statistics | BarChart plus accessible text/table alternatives | statistics.fxml / StatisticsController |
| History / restore | TableView, Dialog with consequences | history.fxml / HistoryController |
| Audit | TableView, filters, export dialog | audit-log.fxml / AuditLogController |
| Settings | ScrollPane, GridPane, CheckBox, Spinner, ToggleGroup | settings.fxml / SettingsController |
| Similarity | SplitPane, paired read-only TextArea, labels | plagiarism.fxml / PlagiarismController; user-facing label “Similarity review” |
| Help | ScrollPane and labelled Hyperlink controls | Proposed; no dedicated Screen entry |
| Persistent feedback | Inline Label + ControlsFX Notifications | Use inline state for critical outcomes; notification is supplementary |

Use JavaFX 25/FXML, AtlantaFX 3.0.0 and ControlsFX 11.2.5. Theme token overrides
can refine AtlantaFX; HTML is a disposable design medium, not a WebView UI
proposal or additional production framework. SQLite JDBC remains behind the
existing repository abstraction. Controllers should call services and contain
no grading logic. GradingTask/background work must marshal updates through
Platform.runLater; do not block the JavaFX thread.

### Explicit product and technical gaps to resolve before implementation

1. Config has a single module and assessment. Modules/Overview are proposed
   navigation, not proof of multi-module persistence support.
2. Config uses `exec.run.timeout.seconds=10` for a tester. The requested
   2,000 ms case example and Retry Failed Case require independent case
   execution, scoring merge rules, prior-attempt evidence and new settings.
3. Current ProgressController retries failed submissions. StudentReportController
   only declares export; targeted retry and score adjustment need service and
   audit contracts before handlers can be implemented.
4. Auto-fix defaults to enabled. Preview-first is a proposed interaction mode;
   distinguish proposed, applied-awaiting-review, approved and undone states.
5. File-name repair has a validator but no corresponding fixer among the listed
   built-in fixers. Treat it as manual review. Missing required answers must
   block affected work or request a corrected submission, never invent code.
6. Similarity is optional and disabled. Excluding starter/tester code is a
   review design requirement that must be verified in the future detector.
7. Restore recovery snapshots and append-only score-audit history are contracts
   to validate, not implemented database behavior.
8. Statistics exclude ungraded totals; pass threshold is 50%; distribution
   uses two-mark (10%) bands. Partial results must have a defined denominator.
9. Proposed settings validation of 1–300 seconds is a design guardrail,
   not a new configuration constraint implemented in production.

## Verification

- JavaScript syntax checked with `node --check`.
- In-memory state smoke checks cover screen rendering, import gating, repairs,
  reject/undo, case retry, score validation/undo, cancellation/resume, export
  scope, theme persistence and empty result filtering (see `verify.cjs`).
- This is not a browser rendering or native accessibility test.
- No production compilation or grading run was needed or performed for these
  separate design artifacts.
