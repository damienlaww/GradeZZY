# Jing Yuan: tasks for 3 November 2026

Your area: `app`, `config`, `model`, `service` and scoring.

Most of your list is done on `jingyuan/taught-java`:
- `16549a7` finishes the merge of `main`.
- `47d5615` writes the merged code in taught Java again.
- `93eb046` moves the grading workers back to their own files, and Cancel
  now stops at the next step.
- `957ec97` covers the rest:
  - a missing tester gets a flag on every student;
  - the run keeps its tester folder for Resume, with a warning until
    Huxley saves it;
  - totals are rounded to 2 decimal places;
  - Statistics count this session's runs;
  - export writes a temporary file and moves it into place;
  - Restore backs up first;
  - the audit log is fuller;
  - true or false settings are strict;
  - the console prints one block per student.

What is left, in order:

## Decided on 3 November

1. **Undo grades the student again without that fix** (option A).
   kenneth.yeo.2024 goes from 20 to 16 when his folder fixes are undone.
2. **A late batch joins the section's existing run** (option A). One run
   per section, so Review, Export and Statistics show everyone.
3. **Seth's three drafts are approved**: the tester warning on Set up,
   the banner for problems every student shares, and "No submission"
   with the blank rows named on Export.

## Your code for those decisions (do these first)

1. **Undo** (with Jun Sheng, his file item 5):
   - add `List<String> getRejectedFixes()` to `Submission`;
   - keep that list in `GradingService.resetForRetry`;
   - in `AnomalyService.undoFix`, add the fix's key to the list, then
     grade that one student again. A new `GradingService` method grades
     one submission with `lastContext`, like `retryFailed`, but for a
     GRADED student;
   - return the old and new total, so the card can say "Marks 20 to 16".
2. **Late batch** (with Seth, his file item 3, and Huxley, his file
   item 6):
   - a `GradingService` method that finds the newest run of a module and
     section (this session's runs, then the saved ones);
   - a method that grades new zips into that run. A new username is
     added. A username already in the run replaces the older
     submission, and an audit entry keeps the old total ("Replaced
     alicia.tan.2024: 20 to 18");
   - refuse with a clear message if the tester folder or score sheet
     differs from the run's.
3. **Export blank rows** (for Seth's draft C): a method on
   `ExportService` that reads the run's score sheet and returns the
   counts and usernames of filled rows, rows with no submission, and rows
   that could not be graded.

## Code still in your packages

- `PlagiarismService.check` never saves the run, so the "Similar code"
  flags are lost when the app is closed. Save the run after the check,
  the way `GradingService.saveRun` does.
- `GradingService.cancel` reads `lastRun` from the window's thread while
  the grading thread writes it. It is only used for the audit text, but
  it could name the previous run for a moment.
- `config.properties` is not checked when the app starts. A bad value is
  found only when that setting is first read: for example
  `database.enabled=yes`, which `AppConfig.getBoolean` now refuses.
  Consider checking every setting once at start-up with
  `SettingsService.validate`.

## Team and repo

- Merge the pull request from `jingyuan/taught-java` early, so everyone
  branches from the same `main`.
- Tell Zach that `UploadedTesterSource` gained `getTestersDirectory()`.
- About 38 untracked files from the old window are in your local folder:
  `src/gradezzy/ui/*Controller.java`, `ui/view/`, `ui/fxml/`,
  `ui/component/`, `ui/css/app*.css`, the old `ui/check` classes and
  `service/ProgressEstimator.java`. `compile.sh` compiles every file in
  `ui/`, so an in-place build may pick them up. They are not in git;
  delete them when you are sure you do not need them.
- Share `samples/e2e-cohort` and `samples/e2e-weird` with the team.
  Everyone's "done when" checks use them.
