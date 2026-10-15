# GradeZZY UI and UX mockup prompt

Use the following prompt with a UI design, image generation, or prototyping
assistant.

```text
You are a senior product designer specialising in professional desktop tools.
Create a high-fidelity UI and UX mockup for GradeZZY. This is a design and
prototyping task only. Do not implement the production JavaFX interface yet.

Review the repository before designing:

https://github.com/damienlaww/GradeZZY

Read README.md, config.properties, design/gradezzy-design.html,
src/gradezzy/ui/Screen.java, the FXML screen names, and the UI controller
names so that the mockup reflects the real product. Treat the repository as
the source of truth for features, terminology, workflow, and technical
constraints.

Strict design-only boundary

- Do not edit Java source files, FXML files, controllers, services, models,
  app.css, config.properties, compile scripts, run scripts, or libraries.
- Do not implement grading, database, file processing, export, or other
  production functionality.
- Do not claim that a workflow is operational because it appears in the
  mockup.
- Use synthetic sample data and label the prototype as a design concept.
- If an interactive prototype is produced, keep it completely separate under
  the design folder. It may simulate navigation and interface states, but it
  must not call production services or modify real submissions.
- The mockup should be feasible to implement later with JavaFX 25, FXML,
  AtlantaFX 3.0.0, ControlsFX 11.2.5, and SQLite JDBC without adding another
  UI framework.

Product context

GradeZZY is a desktop autograding application for Java lab tests. An
instructor selects a module and section, imports student ZIP submissions and
tester files, validates and repairs working copies, grades submissions as a
batch, reviews anomalies, inspects case-level evidence, adjusts marks with an
audit reason, views statistics, and exports reports. Original submissions are
never modified. One broken submission must not stop the rest of the batch.

The mockup must communicate these principles:

- grading is reliable, explainable, and recoverable;
- the instructor always knows what the application is doing;
- warnings and anomaly flags request human review rather than declaring
  misconduct;
- completed results and original files are preserved;
- dense information remains easy to scan and compare;
- the experience feels native and professional on Windows and macOS.

Visual direction

Create a polished, calm, professional interface for an instructor grading an
entire class. Use precise alignment, strong information hierarchy, compact
but comfortable spacing, subtle borders, limited elevation, and clear
typography. Avoid gradients, glass effects, decorative charts, novelty
interactions, excessive rounded cards, platform-specific window controls,
purple, and visual clutter.

Use semantic colour tokens consistently.

Light theme:

- Primary action: #1D4ED8
- Primary hover: #1E40AF
- Primary pressed: #1E3A8A
- Primary soft or selected background: #DBEAFE
- Focus ring: #60A5FA
- Application background: #F8FAFC
- Main surface: #FFFFFF
- Raised or secondary surface: #F1F5F9
- Sidebar: #111827
- Main text: #111827
- Secondary text: #64748B
- Border and divider: #DCE3ED

Dark theme:

- Primary action or accent: #60A5FA
- Primary hover: #93C5FD
- Primary soft or selected background: #172554
- Focus ring: #93C5FD
- Application background: #0B1220
- Main surface: #111827
- Raised surface: #172033
- Sidebar: #080D18
- Main text: #F8FAFC
- Secondary text: #94A3B8
- Border and divider: #334155

Semantic grading colours:

- Success or passed: #18864B
- Warning or needs review: #B45309
- Error or failed: #C62828
- Informational status: #0369A1

All normal text should meet WCAG AA 4.5:1 contrast. Meaningful controls and
graphical indicators should meet at least 3:1. Do not rely on colour alone.
Pair every status with an icon and a text label. Green, amber, and red are
reserved for grading status and must not be used as general decoration.

Application shell

Design a stable desktop shell containing:

1. Persistent navigation for Overview, Modules, Submissions, Assessments,
   Review Anomalies, Results, Statistics, History, Audit Log, Settings, and
   Help.
2. A top context area showing the current module, section, assessment, and
   grading run.
3. One clear page title with a short status summary.
4. One visually dominant primary action per page.
5. Breadcrumbs or an obvious Back action on detail screens.
6. A contextual inspector when a selected student, anomaly, or case needs to
   be compared without leaving the list.
7. Search, filter, sort, selection, and bulk-action patterns that remain
   consistent across screens.

Create high-fidelity mockups for these views:

1. Overview and modules
   - Show modules, sections, assessments, grading status, recent activity,
     and useful empty states.
   - Make module and section grouping obvious.

2. Upload submissions
   - Include a labelled drag-and-drop area and Browse buttons.
   - Separate student ZIP files, score sheet or roster, tester files, and
     starter resources.
   - Show accepted, warning, duplicate, invalid, and missing-input states.
   - Explain why Start Grading is unavailable when required inputs are
     missing.

3. Validation and repair preview
   - Show folder, file-name, required-file, and header-name findings.
   - Compare original and proposed working-copy values.
   - Provide Approve, Reject, Undo, Approve Selected, and Undo All concepts.
   - Clearly state that the original ZIP remains unchanged.

4. Grading progress
   - Show overall progress, current pipeline stage, completed submissions,
     failures, elapsed time, estimated time remaining, and Cancel.
   - Explain that completed results are retained after cancellation.
   - Demonstrate that one failed submission does not stop the batch.

5. Review anomalies
   - Include search, type and severity filters, sorting, selection, and bulk
     review actions.
   - Cover untouched starter answers, empty answers, hard-coded answers,
     abnormal resource folders, compile errors, runtime errors, and timeouts.
   - Use neutral language and distinguish a flag from a confirmed problem.

6. Results table
   - Show students as rows and Q1a, Q1b, Q2a, Q2b, Q3, and Total as columns.
   - Use the exact rubric: Q1a /3, Q1b /3, Q2a /5, Q2b /5, Q3 /4, total /20.
   - Clearly distinguish ungraded, zero, failure, warning, and completed.
   - Include search, filters, sorting, multi-selection, and export actions.

7. Student evidence
   - Show score summary and tabs for Cases, Source, Logs, Repairs, History,
     and Anomalies.
   - For a timeout, show expected result, actual result, 2,000 ms limit,
     relevant log output, retained marks, and plain-language recovery advice.
   - Make Retry Failed Case the primary action. Make Retry Entire Submission
     secondary so that broad reruns are not triggered accidentally.
   - Show Adjust Score with a required reason, Saved status, and Undo.

8. Statistics
   - Include module and section filters.
   - Show completion, average, median, pass rate, score distribution,
     question difficulty, anomaly frequency, and common mistakes.
   - Every chart must have a text summary and accessible labels.

9. History and audit log
   - Show earlier grading runs, backup state, restore preview, user actions,
     automatic repairs, score changes, exports, and timestamps.
   - Make restore consequences and recovery options explicit.

10. Settings and help
    - Group settings into grading, execution, detection, export, storage, and
      appearance.
    - Include light and dark previews, inline validation, contextual help,
      and safe defaults.

11. Similarity review
    - Present token similarity as an instructor review aid.
    - Show compared files and excluded starter or tester code.
    - Never label a student as guilty automatically.

Nielsen's ten usability heuristics

Demonstrate all ten heuristics in the mockup and provide a short mapping:

1. Visibility of system status through progress, saved state, completion,
   cancellation, background work, and export feedback.
2. Match with the real world through instructor terminology.
3. User control and freedom through Back, Cancel, Undo, Retry, and Restore.
4. Consistency through stable components, wording, icons, button order, and
   table behaviour.
5. Error prevention through preflight validation, scope explanations,
   confirmations, disabled-state reasons, and preservation of originals.
6. Recognition rather than recall through persistent module, section,
   assessment, run, rubric, selection, and filter context.
7. Flexibility and efficiency through search, filter, sort, bulk actions,
   drag and drop, and visible keyboard shortcuts.
8. Aesthetic and minimalist design through progressive disclosure and clear
   prioritisation.
9. Error diagnosis and recovery by explaining what happened, where it
   happened, what was preserved, and what to do next.
10. Help and documentation through persistent Help and contextual guidance.

Accessibility and desktop conventions

- Show a logical keyboard focus order and a visible focus indicator.
- Use labelled controls and standard desktop interaction patterns.
- Keep important targets at least 44 by 44 logical pixels where practical.
- Do not depend on colour, icons, hover, or animation alone.
- Include an animation-disabled state.
- Demonstrate layouts at 1200 by 800 and one narrower desktop size.
- Account for 100, 125, 150, and 200 percent display scaling.
- Handle long student names, long paths, large logs, empty data, six sample
  submissions, and larger cohorts without breaking the layout.
- Preserve visible context when moving between a list and its detail view.

Prototype behaviour

If creating a clickable prototype, simulate these flows with synthetic data:

1. Import six submissions, encounter one invalid ZIP, fix the selection, and
   reach a ready-to-grade state.
2. Start grading, display live progress, record one compile error and one
   timeout, cancel safely, and resume while retaining completed results.
3. Review and approve one repair, reject one anomaly, then undo the decision.
4. Inspect learner.bravo at 19/20, open Q1a Case 2, view its timeout evidence,
   and retry only that failed case.
5. Adjust one mark with a reason, show Saved, then Undo.
6. Filter results by module and section and export CSV and JSON concepts.
7. Switch between light and dark themes without losing the current state.

Deliverables

Provide:

1. A concise audit of the current gradezzy-design.html concept.
2. A navigation map and main grading journey.
3. A screen inventory listing purpose, primary action, states, and data.
4. A design-token sheet for both themes.
5. High-fidelity mockups of the key screens and their important states.
6. A clickable prototype if the selected design tool supports it.
7. A Nielsen heuristic mapping tied to visible mockup elements.
8. An accessibility and consistency checklist for later JavaFX work.
9. A handoff note mapping mockup components to likely JavaFX controls such as
   BorderPane, SplitPane, TableView, TreeView, TabPane, ProgressBar,
   ScrollPane, Dialog, and ControlsFX notifications.

Do not write or modify the production UI. Stop after the mockup, prototype,
design rationale, heuristic review, and implementation handoff notes are
complete. Clearly label simulated interactions and synthetic data.
```

