# End-to-end test set (CS102, section G4, 42 students)

One realistic lab test for a whole section, used to test GradeZZY from
Set up to Export. It replaces the old `e2e-cohort`, `e2e-weird` and
`dummy-submissions` sets.

- 42 students on the score sheet, 40 of whom upload in the main batch.
- Duplicate uploads of every kind: `(1)` and `(2)` copies, the same name
  in the folder and in the bulk zip, names that differ only in case,
  the same folder chosen twice, and a student who uploads again late.
- Student code is written separately (different loops, names and
  styles). Most students make one realistic mistake.
- Two uploads come from people who are not on the score sheet.

There is **one tester folder**: the configured `samples/Tester-Files`
(the five testers plus `personstester.txt` and `studentstester.txt`).
Nothing in this folder has its own testers.

## Files

| Path | What it is |
|---|---|
| `upload-1-main/` | The main batch: 43 student zips, an unzipped folder, a bulk zip from eLearn (`eLearn-G4-Lab-Test-download.zip`, 3 zips and an `index.html`), `marking notes.txt` and `.DS_Store` |
| `upload-2-late/` | 4 zips that came in after grading |
| `roster-G4.csv` | The score sheet as eLearn exports it (CRLF, `#` in front of ids) |
| `roster-G4-excel.csv` | The same sheet saved again in Excel: byte order mark, LF, no last `#` column. Must give the same marks |
| `bad-rosters/duplicate-row.csv` | aaron.lee.2024 pasted in twice. Must be refused |
| `bad-rosters/not-a-score-sheet.csv` | A marks list with no eLearn columns. Must be refused |
| `make_e2e.py` | Rebuilds everything above |

Rebuild from the GradeZZY folder:

```sh
python3 -I samples/e2e/make_e2e.py .
python3 -I samples/e2e/make_e2e.py . --reference /tmp/e2e-reference
```

`--reference` also writes every zip that can be opened as a careful
marker would see it (right folder and file names, plain UTF-8), one
folder per zip. Running the provided testers on those folders by hand
gives the "Correct" column below.

## 1. Main batch

First, in **Settings**, turn **Similar code check** on.

| Set up field | Choose |
|---|---|
| Module | CS102 |
| Section | G4 |
| Submissions | the folder `samples/e2e/upload-1-main` |
| Score sheet | `samples/e2e/roster-G4.csv` |
| Starter files | From Settings |
| Tester files | From Settings (`samples/Tester-Files`) |

This gives 42 submissions: 40 students on the score sheet and 2
uploads that are not on it. `marking notes.txt` is skipped. Then
**Start grading**.

Marks are Q1a/3, Q1b/3, Q2a/5, Q2b/5, Q3/4, total /20. "Correct" is what
a careful human marker gives. "GradeZZY should show" is what GradeZZY
showed on 2026-11-04 (branch `jingyuan/nov4-tasks`, 8774c72), checked
twice with the same result, and again on `main` at 52eaa5b.

| # | Student | What they did | Q1a | Q1b | Q2a | Q2b | Q3 | Correct | GradeZZY should show |
|---|---|---|---|---|---|---|---|---|---|
| 1 | aaron.lee.2024 | Model answer; stale `.class` files from the IDE | 3 | 3 | 5 | 5 | 4 | 20 | 20 |
| 2 | amelia.tan.2024 | Model answer. An older copy with the same file name (Q2a bug, 19) is inside the bulk zip | 3 | 3 | 5 | 5 | 4 | 20 | 20, "Two files are named ..." warning; the folder copy is graded |
| 3 | benjamin.koh.2024 | Counts `4.0` as an even integer | 3 | 2 | 5 | 5 | 4 | 19 | 19 |
| 4 | chloe.ng.2024 | Comparator ignores perimeter on ties | 3 | 3 | 5 | 5 | 2 | 18 | 18 |
| 5 | daniel.wong.2024 | Q1a misses a repeat of the first letter; Q2b calls `System.exit` | 1 | 3 | 5 | 0 | 4 | 13 | 13, error on Q2b |
| 6 | dinesh.kumar.2023 | Q2a divides two ints (the testers' averages are whole numbers, so no mark lost); Q3 keeps area < 1000 and sorts smallest first | 3 | 3 | 5 | 5 | 0 | 16 | 16 |
| 7 | emily.chua.2024 | Q2b prints the GPA as `4.00` | 3 | 3 | 5 | 2 | 4 | 17 | 17 |
| 8 | fatimah.ali.2024 | Zipped the Q folders directly on a Mac (no wrapper, `__MACOSX`) | 3 | 3 | 5 | 5 | 4 | 20 | 20, wrapper fix applied |
| 9 | gabriel.ong.2024 | Folder still `RenameToYourUsername`, blank headers | 3 | 3 | 5 | 5 | 4 | 20 | 20, folder fix and header notes |
| 10 | hannah.goh.2024 | Folder named with the OrgDefinedId `01500110`; Q2a is case-sensitive | 3 | 3 | 4 | 5 | 4 | 19 | 19, folder fix applied |
| 11 | isaac.teo.2024 | Username folder inside a username folder | 3 | 3 | 5 | 5 | 4 | 20 | 20, folder fix applied |
| 12 | jiahui.lim.2024 | Saved `q1a.java` and a `q3` folder | 3 | 3 | 5 | 5 | 4 | 20 | 20, two name fixes applied |
| 13 | kaixuan.chen.2024 | IDE added `package Q1;` to both Q1 files | 0 | 0 | 5 | 5 | 4 | 14 | 14, "duplicate class" compile errors |
| 14 | lakshmi.raj.2024 | Q1b misses a semicolon (fixed zip comes late) | 3 | 0 | 5 | 5 | 4 | 17 | 17, compile error |
| 15 | marcus.yeo.2023 | Q2a loops forever (forgot `i++`) | 3 | 3 | 1 | 5 | 4 | 16 | 16, time limit, 1 case kept |
| 16 | natalie.sim.2024 | Q1a hard-coded to the examples in `main` | 0 | 3 | 5 | 5 | 4 | 17 | 17, hard-coded flag |
| 17 | owen.tan.2024 | **Cheat:** Q1a prints `Passed` three times | 0 | 3 | 5 | 5 | 4 | 17 | 17 (the fake lines are ignored) |
| 18 | priya.menon.2024 | **Cheat:** Q1b prints `3.0` and calls `System.exit(0)` | 3 | 0 | 5 | 5 | 4 | 17 | 17, error on Q1b (a plain last-line reader gives 20) |
| 19 | qihao.zhang.2024 | Q2a waits for Enter on `System.in` | 3 | 3 | 0 | 5 | 4 | 15 | 15, no note: every case fails with an exception |
| 20 | rachel.lim.2024 | Q3 right but leaves a thread running forever | 3 | 3 | 5 | 5 | 4 | 20 | 20 |
| 21 | samuel.chan.2024 | Q1b never moves on and prints a line each time | 3 | 0 | 5 | 5 | 4 | 17 | 17, time limit, output cut |
| 22 | shuting.wee.2024 | Saved in Notepad: every file starts with a UTF-8 BOM | 3 | 3 | 5 | 5 | 4 | 20 | 20, six encoding fixes applied |
| 23 | zoe.lefevre.2024 | Windows: CRLF and Windows-1252 (`Zoé Lefèvre` in the headers) | 3 | 3 | 5 | 5 | 4 | 20 | 20, six encoding fixes applied |
| 24 | timothy.ho.2024 | Forgot `ShapeComparator.java` | 3 | 3 | 5 | 5 | 0 | 16 | 16, Q3 compile error |
| 25 | umar.farooq.2024 | Left a scratch `Test.java` in Q3 that does not compile | 3 | 3 | 5 | 5 | 4 | 20 | 20 |
| 26 | valerie.chong.2024 | Correct, but one zip entry points outside the folder (zip slip) | - | - | - | - | - | refused | FAILED, "tries to write a file outside its own folder"; nothing written outside |
| 27 | wenjie.liu.2024 | Submitted the untouched starter | 0 | 0 | 0 | 0 | 0 | 0 | 0, untouched starter flag |
| 28 | xavier.pang.2024 | Empty zip | - | - | - | - | - | blank | FAILED, "holds no files to grade" |
| 29 | yasmin.noor.2024 | A text file named `.zip` | - | - | - | - | - | blank | FAILED, "damaged" |
| 30 | yuki.tanaka.2024 | Correct zip, upload cut off half way | - | - | - | - | - | blank | FAILED, "damaged" |
| 31 | zhiwei.koh.2024 | No Q2 folder and no `Q1b.java` | 3 | 0 | 0 | 0 | 4 | 7 | 7, missing folder and missing file |
| 32 | bella.quek.2024 | An unzipped folder, not a zip | 3 | 3 | 5 | 5 | 4 | 20 | 20 |
| 33 | cheryl.foo.2024 | Uploaded twice: the first never throws in Q2b (18), `(1)` is fixed | 3 | 3 | 5 | 5 | 4 | 20 | 20, `(1)` graded, duplicate warning |
| 34 | darius.tan.2024 | Uploaded three times: compile error (17), fixed (20), `(2)` breaks the comparator | 3 | 3 | 5 | 5 | 2 | 18 | 18, `(2)` graded (newest), two duplicate warnings |
| 35 | weiming.lau.2024 | Model answer. An older `Weiming.Lau.2024.zip` (Q1b bug, 19) is in the bulk zip | 3 | 3 | 5 | 5 | 4 | 20 | 20, lower-case one graded, duplicate warning |
| 36 | junwei.tan.2024 | Q2b tie goes to the last student (`>=`) | 3 | 3 | 5 | 3 | 4 | 18 | 18 |
| 37 | kelvin.ong.2024 | **Copied** all of Jun Wei's work, renamed two variables, left Jun Wei's name in every header | 3 | 3 | 5 | 3 | 4 | 18 | 18 |
| 38 | nicole.yap.2024 | **Copied** Jun Wei's Q2b and Q3 | 3 | 3 | 5 | 3 | 4 | 18 | 18 |
| 39 | grace.lee.2024 | Correct, plus a 60 MB file of zeros (zip bomb) | - | - | - | - | - | refused | FAILED, "unpacks to more than 50 MB" |
| 40 | harith.rahman.2024 | (in the bulk zip) Q2a returns 0, not -1, for a missing file | 3 | 3 | 4 | 5 | 4 | 19 | 19 |
| 41 | meiling.ho.2024 | Nothing in the main batch (comes late) | - | - | - | - | - | blank | not listed |
| 42 | peter.chia.2024 | Uploaded as `peter.chia.2042` (typo in the year) | - | - | - | - | - | blank | not listed |

Not on the score sheet (graded, but not in the CSV):

| Upload | What it is | Total | GradeZZY should show |
|---|---|---|---|
| `2024-2025-peter.chia.2042.zip` | Peter's work under a mistyped username | 20 | 20, "not on the roster" (no hint that peter.chia.2024 is close) |
| `2024-2025-visitor.g2.2024.zip` | A student from another section | 19 | 19, "not on the roster" |

## 2. Late batch

Set up again for **CS102 G4**, with the same score sheet and tester
files, and choose the folder `samples/e2e/upload-2-late`.

The late batch should join the G4 run. In the service this works
(`GradingService.addToRun`). In the window, Set up should ask whether to
add these students to that run. That question is still to be wired in
the UI (Seth); until it is, the window starts a second run.

| Zip | What it is | Result |
|---|---|---|
| meiling.ho.2024 | First upload, model answer | added: 20 |
| lakshmi.raj.2024 | Fixed Q1b, same file name as before | replaces 17 with 20; the audit log keeps the old total |
| chloe.ng.2024 | A new zip, damaged on the way | Chloe keeps 18. Only the audit log says why; her row shows nothing |
| aaron.lee.2024 | The very same zip again | still 20 |

## 3. Export (CSV)

After both batches the score sheet has 42 rows in the order of
`roster-G4.csv`. The numerator is blank for valerie.chong, xavier.pang,
yasmin.noor, yuki.tanaka, grace.lee and peter.chia (6 rows); every other
row has the total above (wenjie.liu is `0`, not blank). Before the late
batch meiling.ho is blank too and lakshmi.raj is 17.

With `roster-G4-excel.csv` the marks are the same and the export keeps
the Excel layout (byte order mark, no last `#` column).

## 4. Other checks

- **Similar code** (Similar code check on), after both batches:
  - junwei.tan and kelvin.ong on Q1a and Q2a (1.00);
  - junwei.tan and nicole.yap on Q2b (1.00);
  - marcus.yeo and priya.menon on Q2b (0.80). This pair is honest (the
    same common approach) and should be dismissed.
  - Not flagged: kelvin's Q2b (renaming two variables takes it under
    0.80), and Q1b and Q3 for anyone. Answers shorter than 50 trigrams
    of their own are not compared, and only `Q3.java` is read, not
    `ShapeComparator.java`.
  - Nothing flags that kelvin.ong's headers carry Jun Wei's name.
- **Statistics for CS102 G4** (both batches): mean 17.58, median 19,
  pass rate 95% over the 38 graded submissions. That count includes the
  two uploads not on the score sheet. Most failed test: "Q2b case 3"
  (5 students).
- **Score sheets that must be refused**: `bad-rosters/duplicate-row.csv`
  ("duplicate username in record 12") and
  `bad-rosters/not-a-score-sheet.csv` ("Missing or repeated column:
  OrgDefinedId"). Grading must not start.
- **Same folder twice**: add `upload-1-main` twice. Every student is
  graded once and each zip says "was chosen more than once".
- **Wrong folder**: choose `samples/e2e` itself as Submissions. The
  message should name `upload-1-main` as the folder to choose.
- **Undo a fix**: Undo grades the student again without the fix.
  gabriel.ong's folder fix: stays 20 (the folder name does not change
  marks). isaac.teo's folder fix: 20 to 0 (no question folder found).
  jiahui.lim's `q3` fix: 20 to 16.
- **Cancel, then Resume**, and closing the app between them, should give
  the same marks as the table.
