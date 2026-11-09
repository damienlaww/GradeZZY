# GradeZZY: review against Nielsen's 10 usability heuristics

Date: 9 November 2026.

What was reviewed: the whole window. That is the tabs Home, Grading (the
steps Set up, Grade, Review and Export), Statistics, History and
Settings, and every sheet that opens over them: Help, Assessments, the
assessment editor, Type test cases, Grading settings, the student
panel, the code and test output sheet, Adjust a mark, similar code and
every confirmation.

How: `sh run.sh --snapshot` opened every tab, step and sheet in live
mode and in demo mode, in the light and dark themes, at 1200 and 960
pixels wide (212 pictures). Each picture was checked against the
heuristics, and the screen code in `gradezzy.ui.flow` was read for
states the pictures do not show, such as errors, empty pages and
confirmations. The heuristics and their names are from
https://www.nngroup.com/articles/ten-usability-heuristics/.

Severity uses Nielsen's scale: 0 not a problem, 1 cosmetic, 2 minor,
3 major, 4 catastrophe.

## 1. Visibility of system status

Where GradeZZY meets it:

- The step bar under the tabs shows Set up, Grade, Review and Export,
  with a tick on each finished step and the current one in blue.
- Grade shows "0 of 5 students", a percentage, a progress bar, the time
  left, and live counts for Graded, Needs review, Did not compile, Timed
  out, Could not grade and Waiting. Marks appear as students finish.
- Every zip on Set up has a status (Ready, Ready, check note, Cannot be
  used) and a one-line reason. The bar at the bottom says how many
  students are ready and how many files will be skipped.
- Home gives each section a status (Not graded, Stopped, Graded), its
  average and its step ("Step 2 of 4, Grade"). A "Next up" line names
  the one thing to do next, such as Resume.
- Review says "3 students still need a look." Export says what the
  score sheet will get: marks filled in, and blanks with the reason.
- Background work (checking files, testers, the similar code check,
  exports) says so in the bottom bar, for example "Checking 3 files...".

Gaps:

- N1. On Set up, while the chosen class files are still being checked,
  the score sheet and tester rows show a red "Missing" and "Not chosen
  yet." The bottom bar already says "Checking 3 files...", so the rows
  contradict it for a moment.
- N9. Review's bottom bar says "3 students still need a look." next to
  a button "Mark all 4 as checked". The 4 counts flags, not students,
  so the two numbers seem to disagree.

## 2. Match between the system and the real world

Where GradeZZY meets it:

- The words are the instructor's own: section, score sheet, tester
  files, starter files, marks, pass mark. Internal words such as
  anomaly, pipeline and stage never appear on screen.
- Problems are written as what happened to the student: "The folder
  had the wrong name", "Q2b did not compile", "Timed out".
- A fix shows the change as a before and after line
  (`- RenameToYourUsername/`, `+ learner.bravo/`).
- Export calls its file "Your score sheet with each student's mark
  filled in, in the same format, ready to upload to eLearn".
- Statistics explains its chart in a sentence: "Most students (about 2
  in 3) scored between 5 and 20."

Gaps:

- N2. When the score sheet of a run was changed, or a late batch was
  added to a run, the activity log wrote the internal run id ("Run
  RUN-1794195230676 ...") instead of the module and section
  (`service.GradingService`, the `SCORE_SHEET_CHANGED` and
  `STUDENTS_ADDED` entries). Both now name the section.

## 3. User control and freedom

Where GradeZZY meets it:

- Every automatic fix is made on a copy and waits for Keep fix or
  Reject fix. Rejecting grades the student again without it, and the
  fix can be applied again.
- Keep all fixes and Mark all as checked offer Undo straight after.
- A mark changed by hand can be undone.
- Grading can be cancelled at any time and resumed later; marks already
  given are kept.
- Back is on every step. Every sheet has a close button and closes with
  Esc. Settings has Discard until Save is pressed.
- History can restore an earlier run or a backup, and the other run is
  kept so the instructor can switch back.

Gaps: none found.

## 4. Consistency and standards

Where GradeZZY meets it:

- One blue primary button per page, always at the bottom right, and
  Cmd or Ctrl + Enter presses it.
- The same coloured pills mean the same thing everywhere: green done,
  amber needs a look, red failed, grey not started.
- Destructive buttons (Delete, Reject fix, Delete section) are red and
  always ask first.
- Lists use the same row layout with hairlines, and the same search
  box with the shortcut shown inside it.

Gaps:

- N3. The run time limit has three names. Set up and the Grading
  settings sheet say "Time limit per test", Settings says "Time limit
  per test run". The limit is for one tester run, not one test case.
- N4. The compile limit is "Compile time limit" on Set up and in the
  Grading settings sheet but "Time limit to compile" in Settings.
- N5. The empty Statistics page links to "Open Grading"; the empty
  History page has a "Go to Grading" button that does the same thing.

## 5. Error prevention

Where GradeZZY meets it:

- Start grading stays off until at least one usable zip is chosen, and
  the bar says why ("Add at least one student ZIP file.").
- Set up checks that the score sheet, tester folder and starter folder
  belong with the uploads before grading, and offers a better file
  nearby with "Use this file instead".
- A late batch asks whether to add the students to the section's
  earlier run or start a new one.
- Export warns about graded students missing from the score sheet and
  about another run of the section with students this one lacks, and
  asks before writing over an earlier export.
- Cancel grading, Delete, Restore, Discard, Grade again, Keep all fixes
  and Mark all as checked each ask first and say what will happen.
- Adjust a mark refuses a mark outside 0 to the maximum and a blank
  reason.
- Original zips are never changed; the Help sheet says so.

Gaps:

- N6. "Clear all" on Set up removes every chosen zip at once, with no
  question and no Undo. With 40 zips chosen from several folders, one
  slip loses the whole selection.

## 6. Recognition rather than recall

Where GradeZZY meets it:

- Module, section and assessment stay chosen across tabs, and every
  page heading repeats them ("CS102, G1, Lab Test").
- Set up shows the files and settings that will be used, with a link
  to change each one, so nothing has to be remembered from Settings.
- Review shows every question's marks and maximum in the table header
  ("Q1a / 3"), and the student panel shows each test as a green or red
  square.
- Home's "Next up" line and each card's step say where each section was
  left.

Gaps: none found.

## 7. Flexibility and efficiency of use

Where GradeZZY meets it:

- Shortcuts: Cmd or Ctrl + Enter for the blue button, Cmd or Ctrl + 1
  to 4 for the steps, Cmd or Ctrl + F for search, Esc to close a sheet.
  They are shown next to the buttons and in Help.
- Zips can be dropped on a Home card or on Set up, as files or a whole
  folder.
- Review has filters (Needs a look, Not fully graded, No submission),
  search by name or username, sorting, tick boxes, and actions for many
  students at once.
- Saved assessments let an instructor switch between lab tests without
  editing settings.
- Students are graded several at a time ("Students graded at once" in
  Settings).

Gaps: none found.

## 8. Aesthetic and minimalist design

Where GradeZZY meets it:

- Forms sit in one centred column; tables and charts use the full
  width. Nothing on the page has a shadow, so sheets stand out.
- Colour is used only for status, plus blue for the one primary action
  and the current tab and step.
- Detail is one click away instead of on the page: the student panel,
  the code sheet, the "?" explanations.

Gaps:

- N7. At 960 pixels wide with a student panel open on Review, the
  search box is cut off at the right edge ("Name or usernam").

## 9. Help users recognize, diagnose, and recover from errors

Where GradeZZY meets it:

- Errors say what went wrong and what to do: "This file could not be
  opened as a ZIP archive. Replace it with a new copy.", with a Remove
  link next to it and "Remove 1 file that cannot be used" above the
  list.
- A student whose code did not compile or timed out shows the tester
  output and compiler message in the code sheet, with "Run tests again".
- A timeout says which cases hung and that the passed cases keep their
  marks.
- A missing or wrong score sheet on Export leads either to a better
  sheet nearby or "Back to Set up".

Gaps: none found.

## 10. Help and documentation

Where GradeZZY meets it:

- Help (top right, on every page) explains each tab and step, the
  keyboard shortcuts and where files are saved.
- Every field label with a "?" opens a short explanation, for example
  what starter files are.
- Home starts with a three-step "Getting started" list until the first
  section is graded.
- Empty pages say what will appear there and link to where to start.
- The README explains every setting.

Gaps:

- N8. Help describes Export as "The score sheet as provided, and a
  JSON file." It does not mention the other files Export can save.

## Gaps found

| Id | Heuristic | Screen | Problem | Severity | Status |
|---|---|---|---|---|---|
| N1 | 1. Visibility of system status | Set up, Class files | Rows show a red "Missing" while the files are still being checked | 2 | Fixed in this change |
| N2 | 2. Match with the real world | History, Activity | A changed score sheet or a late batch is logged with the internal run id | 1 | Fixed in this change |
| N3 | 4. Consistency and standards | Set up, Grading settings, Settings | The run time limit has three names; "per test" is wrong | 2 | Fixed in this change |
| N4 | 4. Consistency and standards | Settings | "Time limit to compile" against "Compile time limit" elsewhere | 1 | Fixed in this change |
| N5 | 4. Consistency and standards | Statistics | Empty page says "Open Grading"; History says "Go to Grading" | 1 | Fixed in this change |
| N6 | 5. Error prevention | Set up | "Clear all" removes every chosen zip with no question | 2 | Fixed in this change |
| N7 | 8. Aesthetic and minimalist design | Review at 960 px | Search box cut off when the student panel is open | 1 | Open |
| N8 | 10. Help and documentation | Help | Export row names only the score sheet and JSON file | 1 | Fixed in this change |
| N9 | 1. Visibility of system status | Review | "Mark all 4 as checked" sits next to "3 students still need a look"; the 4 counts flags, not students | 2 | Fixed in this change |

No problem of severity 3 or 4 was found. N7 is the one gap left open:
it needs a layout change on Review, not a change of words.
