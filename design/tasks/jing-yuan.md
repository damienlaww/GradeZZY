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

## Decisions the team is waiting for (do these first)

1. **Undo and marks** (with Jun Sheng). Today Undo puts the working copy
   back, but the marks earned with the fix stay in the CSV. A regrade
   does not help, because `FixStage` applies the same fix again. Choose
   a rule, for example:
   - Undo marks the student "needs grading again";
   - Retry skips the fixes the instructor undid;
   - Export warns while any student needs grading again.

   Jun Sheng builds the `FixStage` side; you build the service and
   export side.
2. **One section graded in two batches** (with Huxley and Seth). If G3
   is graded, and then a late zip is graded as G3:
   - each run's CSV leaves the other students blank;
   - Statistics counts only the newest run.

   Choose between adding a late batch to the section's existing run and
   filling the CSV from the newest result per student across runs.
3. **Drafts for Seth.** He needs approved drafts for the Set up tester
   warning, the Review banner for shared problems, and the "No
   submission" filter with the blank-row line on Export.

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
