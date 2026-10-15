# GradeZZY UI: design, accessibility and verification review

For the team and the CS102 markers. It records the final design tokens,
how the interface meets Nielsen's ten heuristics, the accessibility and
consistency review, where the build differs from Damien's concept
(`design/concept/HANDOFF.md`), and what was and was not verified.

## 1. Design tokens

Apple-style shapes on the team palette. Every pair below was measured
with the WCAG 2.1 formula: text needs 4.5:1, icons, focus rings and
control borders need 3:1. Values marked *added* fix pairs in the original
palette that failed.

| Token | Light | Dark | Use |
|---|---|---|---|
| Primary | #1D4ED8 | #60A5FA | One primary button per screen, links, selection (including the current pipeline stage), focus |
| Primary hover | #1E40AF | #93C5FD | Pointer over the primary button |
| Primary pressed | #1E3A8A | #3B82F6 *added* | Primary button while pressed |
| Primary soft | #DBEAFE | #172554 | Selected table rows, drop area while dragging |
| On primary | #FFFFFF (6.70:1) | #0B1220 *added* (5.09:1) | Text on the primary button |
| Focus ring | #60A5FA | #93C5FD (8.15:1) | 2 px outer ring, keyboard focus only |
| Focus edge | #1E3A8A *added* (8.49:1) | same as ring | 1 px inner edge; the light ring alone is 2.54:1 |
| Background | #F8FAFC | #0B1220 | Window background |
| Surface | #FFFFFF | #111827 | Panels, tables |
| Raised | #F1F5F9 | #172033 | Table headers, code, drop area |
| Sidebar | #111827 | #080D18 | Navigation |
| Sidebar text | #E2E8F0 *added* (11.91:1) | #E2E8F0 (14.39:1) | Navigation labels |
| Sidebar muted | #94A3B8 *added* (5.72:1) | #94A3B8 (7.58:1) | Group headings, icons |
| Sidebar selected | #1D4ED8 with #FFFFFF | #1E3A8A with #F8FAFC *added* | Current screen |
| Text | #111827 (14.54:1) | #F8FAFC (14.04:1) | Body text |
| Secondary text | #64748B (4.55:1 on background) | #94A3B8 (5.73:1) | Captions |
| Secondary on raised or selected | #59677C *added* (4.71:1) | #94A3B8 | #64748B fails (4.34:1) on #F1F5F9 |
| Divider | #DCE3ED | #334155 | Decorative hairlines only |
| Control boundary | #7D8BA0 *added* (3.16:1) | #6B7A90 *added* (3.73:1) | Text fields, checkboxes, combo boxes |
| Success | #18864B (3.78:1 as icon) | #4ADE80 *added* (8.43:1) | Passed, completed, approved |
| Warning | #B45309 (4.12:1) | #FBBF24 *added* (8.80:1) | Needs review |
| Error | #C62828 (4.61:1) | #F87171 *added* (5.31:1) | Failed, not compiled, timed out |
| Information | #0369A1 (4.86:1) | #38BDF8 *added* (6.86:1) | A submission being graded now |

Rules applied everywhere:

- Status colours mean grading status only. App messages, mode banners,
  the Demo data badge, capability states on Help and "Shown now" on
  History use the neutral raised colours; confirmations show a grey
  tick and problems a red cross.
- Every status is a
  `StatusBadge`: an icon with its own shape plus a text label in the
  normal text colour. Ungraded (dash), scored zero (empty set), not
  compiled (cross), timed out (stopwatch), needs review (triangle) and
  completed (tick) differ by shape and words, not only colour.
- The dark theme uses the lighter status values; the light-theme values
  fall to 2.48:1 to 3.18:1 on dark surfaces.
- Charts use the primary blue and neutrals, never status colours, and each
  has a text summary.

## 2. Apple style

- AtlantaFX Cupertino Light and Dark as the base, with the tokens mapped
  onto its looked-up colours (`-color-accent-emphasis`,
  `-color-bg-default`, `-color-fg-muted`, `-color-border-default`, ...)
  in `app.css`. `app-dark.css` only redefines token values, so a theme
  switch reloads no screen.
- The operating system's own title bar. No imitation window controls.
- Source-list sidebar with group headings, 16 px line icons drawn as SVG
  paths, and a 6 px rounded selection inset from the edges. The main list
  scrolls in short windows while Settings and Help stay pinned below it.
- A context bar (module, section, assessment, run, Demo data badge,
  Light/Dark segmented control) and, on each screen, a title row with the
  buttons on the right and a one-line status under the title.
- Rounded rectangles, 6 px radius, 28 px standard controls, 32 px primary
  buttons; 10 px panels with hairline borders; one soft shadow on sheets
  and messages only (the theme's small button shadows are switched off).
  No gradients, glass, blur or purple. Spacing is on a 4 px grid.
- As on macOS, clicking a button does not mark it as focused; only
  keyboard focus draws the ring.
- Sheets slide down from the top of the window over a dimmed backdrop,
  and the window behind takes no clicks or focus until they close; Cancel
  on the left, the action on the right. Messages appear at the
  bottom centre for 3 seconds, in the past tense of the button, and
  clicks pass through them to the controls below; problems stay until
  closed. Motion eases out.
- Settings are grouped inset lists with toggle switches, in the style of
  macOS System Settings.
- System font (SF Pro on macOS, Segoe UI on Windows), 13 px body, 12 px
  captions, 15 px panel and sheet titles, 22 px page titles, 26 px key
  numbers. Semibold is drawn as bold (see Known limitations).
  Code and paths use the best installed monospaced font (Consolas, Menlo,
  SF Mono), picked at run time.

## 3. Keyboard

Shortcut is Cmd on macOS and Ctrl on Windows (`KeyCombination.SHORTCUT_DOWN`).

| Keys | Action |
|---|---|
| Shortcut+F | Search on the screen shown |
| Shortcut+1 to 9 | Overview, Modules, Assessments, Submissions, Review Anomalies, Results, Statistics, Similarity Review, History |
| Shortcut+Comma | Settings |
| F1 | Help |
| Shortcut+[ and Shortcut+] | Back, and the next step of the grading journey |
| Shortcut+A | Select every row of a table |
| Enter or a double click | Open the selected student's evidence |
| Escape | Close a sheet |
| Tab and Shift+Tab | Move focus; a 2 px blue ring shows where it is |

Shortcuts appear in tooltips (sidebar items, search fields with
Shortcut+F, Select All with Shortcut+A, and the button that closes a
sheet with Esc) and on the Help screen.

## 4. Nielsen's ten heuristics

| Heuristic | Where it shows |
|---|---|
| 1. Visibility of system status | Context bar run state ("Grading 3 of 6"); Grading Progress with the nine pipeline stages, processed count, execution issues, elapsed time, time left and one row per student; the sidebar Grading row while a run goes or a cancelled run can be resumed; the Review Anomalies count badge; Saved status after a mark change; past-tense messages after every action; the Demo data badge. |
| 2. Match with the real world | Instructor words: module, section, Lab Test, roster, tester, working copy, "Timed out", "Not compiled", "Scored zero", "Needs review"; never enum names, exit codes or stack traces. |
| 3. User control and freedom | Back to Results and Shortcut+[; Cancel Grading (keeps completed results) and Resume; Undo Decision for flags and repairs; Undo for a manual mark; Restore with a recovery snapshot; Remove and Replace File before grading. |
| 4. Consistency and standards | One shell; the same title row, status line, filter bar, table selection, inspector and empty states on every list screen; one accent button per screen; Cancel left and action right in every sheet; the same badge for the same state everywhere. |
| 5. Error prevention | Start Grading stays unavailable with the reasons listed; duplicate and unreadable ZIP files are marked before grading; confirmations name what is kept (Cancel Grading, Retry Entire Submission, Remove All, Restore); the reason field is required for a mark change and marks are bounded by the question maximum; Settings validates as you type and blocks Save. |
| 6. Recognition rather than recall | Module, section, assessment and run always in the context bar; the rubric in every column header ("Q1a /3"); the selected student in the inspector; filters, sort, selection and tab remembered per screen; the main grading journey on Overview. |
| 7. Flexibility and efficiency | Search with suggestions, filters and sort; multi-selection with Shortcut+A and bulk actions (Approve Selected, Mark Selected Reviewed); drag and drop of ZIP files and folders; keyboard shortcuts for every main screen. |
| 8. Aesthetic and minimalist design | Summary first, detail on demand: totals and badges in the table, cases, source and logs behind the Student Evidence tabs; the inspector hides behind a toggle in a compact window. |
| 9. Help users recognise, diagnose and recover from errors | Each problem says what happened, where, what was kept and what to do next (for example: "The Q1a tester was stopped at the 10 s limit during case 3. Cases 1 and 2 passed and their marks are kept." with Retry Q1a). Not connected yet panels name the missing service and its owner. |
| 10. Help and documentation | Persistent Help with the seven walkthroughs, keyboard shortcuts, the meaning of every result state, recovery advice and the Backend connections table; help text under every setting. |

## 5. Accessibility and consistency review

Done in the code:

- Every control has a visible label, or `accessibleText` when it shows
  only an icon. Combo boxes are linked to their labels with `setLabelFor`.
- Keyboard focus: `:focus-visible` draws a 2 px ring with a 1 px darker
  inner edge on every control: buttons, fields, choices, check boxes,
  tables, lists, tabs, switches and links (links and switches as a
  border outside them). Focus is visible at 3:1 or more on every
  surface, and the sidebar has its own ring. Focus order follows the
  layout: page title row, filters, table, inspector, then the sidebar
  and the context bar. The window test reads the ring from the real
  screen (section 8).
- No meaning rests on colour, hover or motion alone. Tooltips repeat
  information already on screen.
- Hit areas are at least 24 by 24 px (WCAG 2.2 AA 2.5.8). Controls are
  28 px high, primary buttons 32 px, switches at least 24 px, table rows
  40 to 48 px.
- Animations: 150 to 200 ms ease-out fades and sheet slides only. With
  `ui.animations.enabled=false` or the Animations switch off there is no
  motion at all: the window gets a `no-motion` class that also stops the
  switch thumb from sliding. Table rows are never animated on progress
  ticks.
- Long content: long names truncate in tables with the full name in the
  inspector or a tooltip; paths wrap (learner.charlie's working copy has
  a deliberately long path); the Submissions list shows each file's full
  path as a tooltip; the context bar's run and assessment have tooltips
  and are never cut; the 100,000-character sample log scrolls inside a
  read-only text area; tables are virtualised (240-student cohort).
- Responsive: below 1100 px the sidebar narrows to 188 px, the context bar
  drops its captions but keeps every value, and inspectors hide behind a
  toolbar toggle. When one row of the context bar would not fit, the
  assessment and run move to a second row. Below 760 px the sidebar
  becomes a menu button. Two-column screens stack in live and demo mode.
  Buttons never shrink below their text. List screens scroll as a page
  at 600 by 400 instead of squeezing the table.

Not verified (needs a person):

- [ ] Keyboard-only walkthrough of the seven flows on Windows and macOS.
  The window test covers Tab, Shift+Tab, every shortcut and Escape on
  Windows, but it drives the flows with the mouse.
- [ ] Screen reader (Narrator on Windows, VoiceOver on macOS) reading
  badges, metric tiles and table cells. JavaFX accessibility differs by
  platform.
- [ ] True operating system display scaling at 125, 150 and 200 percent.
  The snapshots use the matching logical sizes (960x640, 800x533,
  600x400), which is how JavaFX lays out a scaled window, but not a real
  scaled display.
- [ ] A run on macOS (Apple Silicon and Intel) and with the high-contrast
  mode of Windows.
- [ ] Live mode with real submissions once the backend stages work.

## 6. Where the build differs from HANDOFF.md and the brief

| Concept | Build | Why |
|---|---|---|
| 44 px buttons, 5 px corners | 28 px controls, 32 px primary buttons, 6 px corners, 24 px minimum hit area | Apple macOS control sizes; WCAG 2.2 AA asks for 24 px. 44 px is the iOS and AAA size. |
| White chips behind status icons in dark mode | Lighter dark-theme status colours | Simpler, and each passes 3:1 as an icon and 4.5:1 as text. |
| 2,000 ms per-case limit, Retry Failed Case | The configured `exec.run.timeout.seconds` (10 s per tester run); Retry Q1a; learner.bravo's tester stops during case 3 | One tester run covers every case of a question, so a question is the narrowest retry the architecture can run. |
| HTML concept with simulated actions | JavaFX screens calling `DashboardBackend`, live by default, demo with `--demo` | Teammates can plug real services in without changing controllers. |
| Sidebar 176 px below 1100 px (also in the brief) | 188 px | At 176 px "Review Anomalies" with its count badge is cut; 188 px is the narrowest width that keeps every label whole. |
| Checkbox column for selection | The table's own multi-selection with Select All and Clear Selection | Native desktop pattern (Finder, Mail) with Shortcut+A and Shift-click. |
| Settings save "preview settings" | Live mode saves to `config.properties` through `SettingsService` (it keeps comments); demo mode keeps changes in memory | `SettingsService` already works. |
| Search in the title row (the brief's unified toolbar) | Search, its Names or Code scope and the filters sit in one row directly under the title | The title row already holds two export buttons; at 600 px one row would squeeze the search field too narrow to use. |
| A "Grading" sidebar row while a run is active | The row stays while a cancelled run can still be resumed, and goes when the run finishes | Without it a cancelled run could only be resumed by finding Grading Progress again. |
| Light and Dark in the context bar save the choice | The context bar switch lasts for the session; Settings saves the default theme | Saving on every click would write `config.properties` in live mode. |
| Filter and sort through `SearchService` | Search uses `SearchService.search`; filtering and sorting stay in the screens | The screens filter by the seven result states (status, anomalies and review decisions together) and keep ungraded rows last, which `filterByStatus` and `sortByScore` do not. |
| Form checks through `InputValidator` | Adjust Score and typed test cases check their own fields | `InputValidator` has no check for a decimal mark in a range or a Java method name; both are listed as contract requests in UI-INTEGRATION.md. |

## 7. Known limitations

- **Library mismatch in `lib/`.** AtlantaFX 3.0.0 was built against a
  newer JavaFX than the bundled 25.0.4: its `ModalPane` calls
  `Interpolator.ofSpline`, which JavaFX 25.0.4 does not have, so opening a
  `ModalPane` throws `NoSuchMethodError`. Sheets are therefore drawn by
  `gradezzy.ui.view.SheetLayer` with plain JavaFX. No other AtlantaFX class
  in use touches that method (checked by scanning the jar). Do not use
  `ModalPane` unless JavaFX is upgraded.
- JavaFX CSS cannot turn on tabular figures, so numbers line up by right
  alignment, not by fixed-width digits.
- JavaFX's logical System font has only regular and bold faces, so weight
  600 renders as regular. Semibold text in the type scale is drawn bold.
- JavaFX 25 does not break lines at a zero-width space, so long paths are
  broken inside a folder name only when a line is full. They never clip
  or scroll sideways.
- Long sentences in FXML files are written as `<text>` elements over
  several lines, because FXML joins the lines with single spaces there,
  while a line break inside an attribute would add extra spaces. Every
  Java, FXML and CSS line keeps to 80 columns.
- `--windowtest` needs a desktop session. It keeps its window above other
  windows while it runs and moves the real pointer, so leave the mouse
  and keyboard alone for about three minutes.
- The Results status line describes exports of "selected or shown"
  students; in live mode `ExportService` still exports the whole run until
  the export scope contract exists, and the message says so.
- Live mode shows empty states for most screens until the backend stages
  are written; Help lists which service each one waits for.
- `--selftest` and `--snapshot` use demo data only. They do not exercise
  live services.

## 8. Verification results

Run on Windows 11 with Temurin JDK 25.0.4, on branch `UI/UX-implementation`
at `aafc5aa` plus these changes.

| Check | Result |
|---|---|
| `compile.bat` (Command Prompt) | Compiled, no errors |
| `sh compile.sh` (Git Bash) | Compiled, no errors |
| `sh compile.sh core` | Compiled the grading core without JavaFX; no `ui` classes |
| `run.bat --check` and `sh run.sh --check` | "All 16 screens loaded in live and demo mode, with both themes." Exit 0 |
| `run.bat --selftest` and `sh run.sh --selftest` | All 7 flows PASS. Exit 0 |
| `sh run.sh --windowtest` | 93 checks PASS, 0 FAIL, with real mouse and keyboard input on the real window (below) |
| `sh run.sh console` | Runs as before; 0 submissions because `samples/` is not on this machine. Exit 0 |
| `--snapshot --fullpage` | 260 pictures: 15 screens, 3 kinds of sheet and a running grading run, in both themes at 1200x800, 960x640, 800x533 and 600x400, plus 108 whole-page pictures of the screens that scroll. The changed screens were inspected at every size touched by a fix (Results, Overview, Submissions, Validation, Grading Progress, Help and the sheets) |
| Live grading and settings saves | Not run, on purpose: both would touch real files and `config.properties` |
| TODO counts | Seth 31, Jun Sheng 23, Zach 14, Jing Yuan 15, Huxley 28, all unchanged, and no other owner's TODO line edited. Damien 49 to 0: every `TODO(Damien)` in `ui` was implemented, then its line removed; the audit and student-report exports show Not connected yet with a contract request, and saving the theme from the context bar is a recorded deviation |
| Files changed outside `src/gradezzy/ui/` | `compile.sh` and `compile.bat` (`UI_SOURCES` lines only), and the new `design/UI-INTEGRATION.md` and `design/UI-REVIEW.md` |
| Style scan of every `ui` Java, FXML and CSS file | No line over 80 characters, no tabs, no non-ASCII characters (so no em dashes or emojis), no `setStyle`, streams or ternary operators, Javadoc on every public declaration, the AI use note in every header |

### The real-window test

`--windowtest` opens GradeZZY on the desktop and drives it the way a
person would: operating-system mouse moves, clicks, double clicks,
Ctrl-clicks, typing, Tab, shortcuts and a drag from another window. Each
click checks that the press reached the control it aimed at. It checks:

- The window opens at 1200 by 800; each of the 12 sidebar items opens its
  screen.
- Shortcut+1 to 9, Shortcut+Comma, F1, Shortcut+[ and Shortcut+F.
- Tab moves through eight controls on Results and Shift+Tab returns; at
  each step the focus ring is found in a picture of the real screen
  (buttons, combo boxes, the table and the sidebar).
- Resting the pointer on a sidebar item shows its tooltip with the
  shortcut.
- The seven flows below, done through the window: choosing files, the
  list row and Replace File; Start Grading, Cancel Grading with its
  confirmation sheet and Resume; Approve Selected, Reject Selected and
  Undo Decision; a double click on learner.bravo and Retry Q1a; Adjust
  Score with the question picked from its list, a refused blank reason,
  a typed reason, Save and Undo; the section picked in the context bar,
  a click and a Ctrl-click, both export previews, Escape and Done; the
  Dark and Light buttons.
- ZIP files dragged from a second window highlight the drop area and are
  accepted (demo mode adds the sample files and reads nothing).
- At 960 by 640 the window is compact; at 600 by 400 the sidebar becomes
  a menu that still reaches Results; back at 1200 by 800 the sidebar
  returns.
- Every sidebar screen opens in live mode without an error message.

What the window test found and what was fixed:

- Combo boxes, choice boxes and spinners drew no focus ring: their rule
  named a colour the theme defines only on text fields.
- After Approve Selected, the focused "Review Anomalies" link was drawn
  as a solid blue block with invisible words, because the theme marks a
  clicked button as focused. Clicked buttons no longer look focused.
- The Submissions file rows were wider than the list, which cut the
  status words and hid part of each row behind a sideways scroll bar.
- Confirmation messages caught clicks meant for the controls under them;
  clicks now pass through.

## 9. Requirement audit

Every requirement line of `design/UI-UX-Mockup-Prompt.md` was checked
against the build (211 rows): 158 met at first, 42 partly met, 1
missing, 5 not applicable (conversation-only steps, and `ModalPane`,
which cannot run on JavaFX 25.0.4) and 5 that need a person (section 5).
All 43 open rows were then fixed or, where fixing would make the
interface worse, recorded as deviations in section 6 with the reason.
The main fixes:

- The context bar always shows module, section, assessment and run; it
  moves the assessment and run to a second row instead of hiding them.
- Demo data now has a long path, all 16 anomaly types and all 7 result
  states.
- The large shadow under every table and list (a CSS class name shared
  with JavaFX) and the theme's button shadows are gone.
- Problem messages show a red icon; confirmations, banners, capability
  states and the Demo data badge no longer use status colours.
- Restore calls `HistoryService.restore` with a chosen backup file in
  live mode; Capability names only methods that are called, and each
  capability has a plain-language purpose shown on Help.
- Exports, code search, Back Up Now, Restore and the similarity check
  run on a background thread.
- Focus rings have the inner edge on every control; switches are 24 px
  high and stop sliding when animations are off; motion eases out.
- Grading Progress statuses and execution issues show an icon with their
  words; the sidebar row is called Grading.
- Two-column screens stack in live mode at 600 by 400; Overview's links
  and times are never cut below the fold; `--snapshot --fullpage` now
  pictures whole pages.
- Spacing is on a 4 px grid in CSS, FXML and Java; every FXML line keeps
  to 80 columns; Overview buttons are in Title Case; magic numbers in
  layout code are named constants.
- `--selftest` also checks the Start Grading button, the current stage,
  elapsed time and time left, the Cancel explanation, and that decisions
  and progress survive a theme switch.

The seven flows checked by `--selftest` (and, through the window, by
`--windowtest`):

1. Six ZIP files with one unreadable; Start Grading blocked with the reasons
   listed; the file replaced; roster and testers added; Ready to Grade.
2. Grading records a compile error and a timeout and carries on; Cancel
   keeps the three finished results; Resume grades only the other three.
3. A repair approved in Validation; a flag rejected in Review Anomalies,
   then Undo returns it to Open.
4. learner.bravo at 19/20 with Q1a stopped at the 10 s limit during case
   3; Retry Q1a passes, Q1a becomes 3 of 3, the total 20/20, and no other
   mark changes.
5. A blank reason and a mark above the maximum are refused; a valid
   change is saved and listed; Undo restores the earlier mark.
6. A section with no run and the empty module show no results; CS102 G1
   shows six; the CSV and JSON previews hold only the two selected
   students; the export sheets open.
7. Dark and light themes switch without reloading the screen or losing
   the students shown and the selection.
