# Zach: tasks for 3 November 2026

Your area: `exec`, `testcase`, `InstallTestersStage`, `CompileStage`,
`ExecuteStage`, `TesterOutputScoreParser`.

These came out of a full test of `main` (421d2f6) on 3 November: the 17
student e2e cohort, 21 odd uploads, 14 cheating attempts and a 200
student batch. Every cohort mark is right today. The items below are the
ways a mark can still come out wrong, or a student can still cheat. Do
the P1 items first.

## Before you start

- Branch from `main` once the pull request from `jingyuan/taught-java`
  is merged. Until then, branch from `jingyuan/taught-java`. Line numbers
  below are from that branch (commit 957ec97); if a line moved, search
  for the method name.
- That branch rewrote `exec` in taught Java: `TrustedPrintStream` and
  `TrustedFrameCheck` are their own files again, `ProcessRunner` uses
  `String` instead of `StringBuilder`, and `List.of` is gone. The
  behaviour is the same as `main`.
- Project rules (the local rules file is not in git, so here they are):
  - Only Java taught in CS102: no ternary, nested or anonymous classes,
    try-with-resources, multi-catch, varargs parameters, `StringBuilder`,
    `String.join`, `List.of`, streams, method references, or lambdas
    outside `ui`. The README section "Java taught in CS102" shows the
    taught way for each. A new library needs a row in "Java beyond the
    CS102 syllabus".
  - 80 columns, 4 spaces, braces on every block, no magic numbers, a
    header comment on every file, Javadoc with its MUST/SHOULD/COULD tier
    on every public type and method.
  - No em dashes and no emojis. No co-author or attribution lines in
    commits.
  - A new setting goes in `config.properties`, `ConfigKeys` and the
    README settings table together.
- Check before you push, from the GradeZZY folder: `sh compile.sh core`,
  then `sh compile.sh` (no error or warning), `sh run.sh --check`,
  `sh run.sh --selftest`, and `sh run.sh console G2`. The console must
  still print 12.0, 10.0, 10.0, 0.0, 20.0, 1.0.
- Test files: `samples/` is not in git. Ask Jing Yuan for
  `samples/e2e-cohort` (hand-worked marks in its README, column
  "Correct") and `samples/e2e-weird`. Grade the cohort with its own
  `testers/` folder. Every mark must stay as in that column.

## P1: wrong marks and cheating

### 1. A student's code can replace the next question's tester

- Where: `CompileStage.process` (about line 121) compiles every question
  before `ExecuteStage` runs any tester. The sandbox lets student code
  write anywhere in its question folder (`TesterRunner.sandboxRules`,
  line 254).
- What happens: Q1a's code, run by `Q1aTester`, copies a fake class over
  `Q1/Q1bTester.class`. Q1b, with a wrong answer, then scores 3/3, and
  its lines carry the real token. The same works from Q2a to Q2b. Two
  questions in one folder that both declare `class Helper` also break
  each other: Q1b's compile overwrites Q1a's `Helper.class`, so Q1a fails
  with `NoSuchMethodError`.
- Fix: give each question its own fresh copy of its folder for compiling
  and running (for example `<working copy>/.questions/Q1a/`), so nothing
  one question writes is seen by another. If that is too much for today,
  at least compile each question's tester again right before it runs.
- Done when: a Q1a that writes a file named `Q1bTester.class` into its
  folder does not change Q1b's mark, and the cohort marks are unchanged.

### 2. Tester data files are taken from the student's zip

- Where: `TesterInstaller.copyDataFiles` (line 98) copies only the data
  files the tester folder has. `flagMissingDataFiles` (line 120) then
  finds the student's own copy and is satisfied.
- What happens: `samples/Tester-Files` has no `personstester.txt` or
  `studentstester.txt`, but every sample zip ships its own. So Q2 is
  marked against whatever the student sent, and an edited data file
  changes the Q2 mark with no flag.
- Fix: before copying, delete the student's copies of every file named
  in `assessment.tester.data.files`. Use only the tester folder's copy,
  and keep the existing flag when it is missing.
- Done when: a zip whose `personstester.txt` is edited gets the same Q2
  mark as with the original, and a tester folder without the file gives
  the "missing file" flag.

### 3. The score parser believes any total the tester prints

- Where: `TesterOutputScoreParser.readScore` (line 132) uses the last
  line whenever the exit code is 0. It never compares it with the
  `Passed` lines it counted (`passCount`).
- What happens: student code can read the token and the tester's
  `score` field by reflection, because the harness and the student share
  one module. A wrong answer then scores full marks: verified, with all
  three cases printing `Failed` and a total of 3.0.
- Fix: if the total is more than the trusted `Passed` count allows,
  where each case is worth max score divided by the number of trusted
  `Test` headers, use the count and add a flag. Then decide on the
  harness-as-a-named-module change you documented in October. It makes
  the token unreachable by reflection.
- Done when: a submission that sets the tester's score field gets the
  mark its `Passed` lines earn, with a flag.

### 4. A nested class can stand in for the tester's own types

- Where: nothing checks for it today. Each tester `extends` the student
  class, so a member type of the student class wins over
  `import java.util.*` inside the tester.
- What happens: `public static class ArrayList<E> extends
  java.util.ArrayList<E> { public boolean equals(Object o) { return
  true; } }` inside `Q1a` makes the tester's `expected.equals(result)`
  always true: 20/20 with an empty answer and no flag.
- Fix: after compiling, list the student class's member classes (the
  `Q1a$*.class` files). If one has the simple name of a `java.lang` or
  `java.util` type, or of a name used in the tester source, add a
  flag. Do not change the mark: flags are never auto-penalised.
- Done when: the example above gets a flag.

### 5. The macOS sandbox lets student code read your files and use the network

- Where: `TesterRunner.sandboxRules` (line 254) starts with
  `(allow default)` and denies only the working, backup and submissions
  folders.
- What happens: verified. Student code could read the marker's
  `~/.zshrc`, the tester folder and `samples/groundtruth.txt`, and open
  a connection to the internet, which is enough to send files away.
- Fix: add `(deny network*)`, and deny `file-read-data` under `/Users`.
  Then allow the JDK (`System.getProperty("java.home")`), the harness
  classes folder and the student's own folder again. Off macOS, nothing
  is sandboxed and the only notice is one line on `System.err`
  (line 122). Add a warning the window can show (ask Seth or Jing Yuan
  where it should go).
- Done when: a tester run cannot open `https://example.com` or read a
  file in your home folder, and the cohort marks are unchanged.

### 6. A missing tester: give Set up a way to warn before grading

- Where: `UploadedTesterSource.prepareTesters` (line 91) skips a question
  with no tester. Since 957ec97, `ScoreStage` flags it on every student,
  but only after grading.
- Fix: add a public method that lists, for an assessment and a tester
  folder, the questions with no tester, the data files that are
  missing, and the testers that do not compile against the starter. You
  can reuse `CompileStage.checkTester`. Seth will call it from Set up
  through `LiveBackend`, so agree the method's name and return type with
  him early today.
- Done when: the method reports the three problems for
  `samples/e2e-weird/w15-missing-tester`, `w16-no-tester-data` and
  `w21-broken-tester`.

## P2: robustness

### 7. Tester JVMs have no memory or stack limit

- Where: `TesterRunner.runTester` (around line 179) builds the java
  command with no `-Xmx`. Each run can take 6 GB, and with 4 workers a
  memory bomb can take the machine down.
- Fix: add `-Xmx256m`, `-Xss2m` and `-XX:ActiveProcessorCount=1`. Put the
  memory size in a new setting, such as `exec.max.memory.mb`, rather
  than a magic number.

### 8. Testers can outlive GradeZZY

- Where: `ProcessRunner` has no shutdown hook, and the harness has no
  time limit of its own. If the app is closed or crashes during a run,
  an endless tester keeps spinning.
- Fix: a shutdown hook in `ProcessRunner` that destroys every running
  child. A second thread in `TesterHarness` that halts after the time
  limit plus a short grace period.

### 9. The tester check uses the starter from the settings, not the batch's

- Where: `CompileStage.compileWithStarter` (line 176) reads
  `paths.starter.dir`. `PipelineFactory.createPipeline` already knows the
  batch's starter folder (line 102) but does not pass it to
  `CompileStage` (line 119).
- Fix: give `CompileStage` the starter folder in its constructor and pass
  it at line 119. You may change that one line in `PipelineFactory`.

## P3: small

- After a timeout, output still in the pipe is dropped
  (`ProcessRunner`, line 160): a `Passed` printed in the last 50 ms is
  lost. Read what is left after the kill.
- `System.getenv()` in student code throws `NoClassDefFoundError`,
  because `TesterHarness.blockNewPrograms` (line 107) breaks
  `ProcessImpl`, which `System.getenv` needs. Rare in CS102 code, but it
  turns a correct answer into 0. Look for a way to block new programs
  that leaves `getenv` working.

## Note

`UploadedTesterSource` gained one getter, `getTestersDirectory()`, in
957ec97, so the run can remember its tester folder for Resume. Nothing
else in your files changed in behaviour.
