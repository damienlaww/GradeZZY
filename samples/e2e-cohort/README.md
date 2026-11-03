# End-to-end test cohort (section G3)

A realistic lab test upload: 17 student zips for 18 students on the
roster. Every answer was written separately (different loops, names and
styles), and most students make one realistic mistake. The expected
marks were worked out by hand, by compiling and running the provided
testers outside GradeZZY.

## What to upload

| Upload screen field | Choose |
|---|---|
| Section | `G3` |
| Submissions | **Choose Folder**: `samples/e2e-cohort/submissions` (not `e2e-cohort` itself) |
| Score sheet | `samples/e2e-cohort/roster-G3.csv` |
| Testers | **Choose Folder**: `samples/e2e-cohort/testers` (not "Use Configured") |
| Starter | Use Configured |

The testers folder holds the five provided testers **plus**
`personstester.txt` and `studentstester.txt`. The configured
`samples/Tester-Files` does not have those two files, and these students
did not include them in their zips, so with "Use Configured" every Q2
mark comes out wrong (7 marks lost per student) and nothing warns you.

## Expected results

"Correct" is what a careful human marker gives. "GradeZZY today" is what
`main` at `cfb6303` gives (checked on 2026-10-28).

| Student | What they did | Q1a | Q1b | Q2a | Q2b | Q3 | Correct | GradeZZY today |
|---|---|---|---|---|---|---|---|---|
| alicia.tan.2024 | Model answer; stale `.class` files from the IDE | 3 | 3 | 5 | 5 | 4 | 20 | 20 |
| bryan.ng.2024 | Counts `4.0` as even; comparator ignores perimeter on ties | 3 | 2 | 5 | 5 | 2 | 17 | 17 |
| clara.wong.2023 | Zipped Q1-Q3 directly on a Mac (no wrapper, `__MACOSX`) | 3 | 3 | 5 | 5 | 4 | 20 | 20, wrapper fix applied |
| darren.lim.2024 | Folder still `RenameToYourUsername`, blank headers; Q2a case-sensitive | 3 | 3 | 4 | 5 | 4 | 19 | 19, 6 fixes applied |
| elaine.koh.2024 | Q1b missing semicolon | 3 | 0 | 5 | 5 | 4 | 17 | 17, COMPILE_ERROR |
| farhan.ismail.2023 | Q2a infinite loop (forgot `i++`) | 3 | 3 | 1 | 5 | 4 | 16 | 16, TIMEOUT (1 case kept) |
| grace.chua.2024 | Q1a hard-coded to the examples in `main` | 0 | 3 | 5 | 5 | 4 | 17 | 17, but **no hard-coded flag** |
| huimin.lee.2024 | Q2b tie goes to the last student (`>=`) | 3 | 3 | 5 | 3 | 4 | 18 | 18 |
| ivan.goh.2024 | Copied Hui Min's whole submission; folder named `01400109` | 3 | 3 | 5 | 3 | 4 | 18 | 18, folder fix applied |
| jasmine.ong.2023 | Submitted the untouched starter | 0 | 0 | 0 | 0 | 0 | 0 | 0, UNTOUCHED_STARTER |
| kenneth.yeo.2024 | Saved `q1a.java` and folder `q3` (wrong case) | 3 | 3 | 5 | 5 | 4 | 20 | **16**: Q3 folder not found, not fixed |
| lucas.teo.2024 | IDE added `package Q1;` to Q1a and Q1b | 0 | 0 | 5 | 5 | 4 | 14 | 14, COMPILE_ERROR "duplicate class" |
| meiling.chen.2024 | Q1a checks neighbouring letters only and prints `Passed check:` lines; Q2b calls `System.exit` | 1 | 3 | 5 | 0 | 4 | 13 | 13, RUNTIME_ERROR |
| nathan.chan.2024 | No Q3 folder; Q2b returns `""` instead of throwing | 3 | 3 | 5 | 3 | 0 | 14 | 14, MISSING_QUESTION_FOLDER |
| olivia.sim.2024 | Zip downloaded twice: `... (1).zip` | 3 | 3 | 5 | 5 | 4 | 20 | **20 shown, blank in the CSV**: not matched to the roster |
| priya.nair.2024 | Upload cut off half way (corrupt zip) | - | - | - | - | - | FAILED | FAILED, reported as INTERNAL_ERROR |
| quentin.lau.2024 | Wrote everything on Windows (CRLF line endings) | 3 | 3 | 5 | 5 | 4 | 20 | 20 |
| rachel.tan.2024 | On the roster, submitted nothing | - | - | - | - | - | blank | blank |

Also check:

- **Plagiarism** (turn on `plagiarism.enabled`): only huimin.lee.2024 and
  ivan.goh.2024 are flagged, on all five questions (similarity 1.00).
- **Statistics for G3**: mean 16.19, median 17 over the 16 graded
  submissions; most failed test "Q2b case 3".
- **Anomaly log**: undo every fix on darren.lim.2024 and the working copy
  goes back to `RenameToYourUsername` with blank headers; Approve then
  Undo on clara.wong.2023 also works.
- **Cancel, then Resume** without closing the window: correct. Cancel,
  **close the app**, reopen, Resume from History: the students graded
  after the restart lose 7 marks each on Q2 (see the review notes).

## Other files

- `../e2e-weird/`: one folder per odd upload (bulk zip, zip slip, zip
  bomb, duplicate student, fake `Passed` output, broken tester, Excel
  roster and more). Each has `subs/`, `testers/` and `roster.csv`.
- `make_cohort.py`: rebuilds this folder from `samples/`
  (`python3 -I samples/e2e-cohort/make_cohort.py .` from the GradeZZY
  folder).
