# GradeZZY checks

Every `*Test.java` file here has a `main` that runs its checks, prints a
count line and exits with 1 when a check fails. Run them from the
repository root with a JDK:

```sh
sh compile.sh
mkdir -p /tmp/gradezzy-tests
javac -encoding UTF-8 -cp "classes:lib/*" -d /tmp/gradezzy-tests tests/*.java
java -cp "classes:lib/*:/tmp/gradezzy-tests" IngestRegressionTest
```

On Windows, use `compile.bat` and `;` as the Java classpath separator.
Replace `IngestRegressionTest` with the name of any other test below.
The other files are helper classes the checks share, with no `main`:
`TestConfig`, `ExtractOnlyPipelineFactory`, `NoTestersSource`,
`FixOnlyPipelineFactory`, `FixCaseOutcome`, `FixedRemarks` and
`FakeBackendHandler`.

## Fixed test settings

No test reads the live `config.properties`, so the checks pass whatever
assessment is chosen in the window. `TestConfig` loads
`tests/test-config.properties` (the sample Lab Test in `samples/`):
`TestConfig.load()` reads it as it is, and `TestConfig.copyInto(folder)`
copies it into a test's temporary folder and moves the working, output,
backup and assessments folders and the database there too. A line
starting with `NOTE (known limitation)` reports behaviour kept on
purpose; it never fails the run.

## Ingestion regression checks (IngestRegressionTest)

The javac line compiles every file in `tests/`, including the two helper
classes the ingestion checks use: `ExtractOnlyPipelineFactory` and
`NoTestersSource`.
The checks create and remove temporary uploads, rosters and working copies.
They exercise ingestion through `GradingService` with a pipeline containing
only extraction, plus UI batch eligibility and ZIP traversal protection.
They do not compile or execute student code.

## Answer group checks (AnswerGroupsTest)

They cover the code shape tokens, how answers are grouped and titled,
saving names and checks (in memory and in a temporary SQLite file), and
the common mistakes CSV and the JSON `mistake` field.

## Assessment delete checks (AssessmentDeleteTest)

They work on a copy of the fixed test settings, a temporary assessments folder
and temporary chosen-files files. They check that deleting an assessment
clears the files chosen for it on Set up in every section, that Undo puts
them and its score sheets back, that a new assessment with the same name
inherits nothing, and that a rename moves the chosen files to the new name.

## Similar code checks (SimilarityTest)

They work on a copy of the fixed test settings and temporary student folders.
They check that honest answers following the same plan in their own names
are not flagged, that a copy with every name changed and a method moved is
flagged by winnowing (and not by trigrams), and that changing
`plagiarism.algorithm` takes effect at the next check without a restart,
with the run recording the algorithm its last check used.

## Backup restore checks (BackupRestoreTest)

They work on a copy of the fixed test settings, a temporary database and a
temporary backup folder. They check that a text file, the settings file,
an empty file and another program's SQLite file are refused and leave the
database as it was, that restoring the oldest kept backup deletes no
backup, that Undo puts the runs back, and that the copies made before a
restore are kept apart from the backups made with Back up now.

## Class file checks (ClassFilesTest)

They check that the working copy keeps a student's class file that has
no source, that the tester check on Set up names the classes nobody
provides, and that a run warns when a question did not compile for
nearly everyone.

## Util and config checks (UtilConfigTest)

CSV split and join, line endings, source decoding (including a UTF-8
byte order mark), FileUtils, AppConfig, ModuleSections and
ExportFolders.

## Ingestion unit checks (IngestUnitTest)

ZipExtractor limits, damaged and odd zips, macOS and Windows extras,
RosterReader formats, SubmissionLoader and WorkingCopyManager (class
files, inner zips, folder uploads, bad usernames).

## Fixer checks (FixersTest)

Each case builds a working copy with one kind of mistake, runs the real
validators and FixStage, checks the layout after the fix, then undoes
every fix and checks the copy is byte for byte what the student sent.
Also FixManager's rejected keys and records.

## Score parser checks (ScoreParserTest)

How tester output becomes test cases and a score: headers, verdicts,
timeouts, crashes, printed totals (NaN, Infinity and a negative total
fall back to counting passes), case names and ScoreCalculator.

## Similar code checks, unit level (PlagiarismTest)

JavaTokenizer, Winnowing, CodeLineCounter, LineComparison, the
detectors, IdenticalCopyFinder (Name and Email ID headers) and
PlagiarismService on temporary answers.

## Persistence checks (PersistenceTest)

A save and load round trip through SqliteResultRepository in a
temporary database, migration from an older database, BackupManager
(backup, prune, restore, timeOf) and BackupReader (read only, damaged
files).

## Report checks (ReportTest)

CsvScoreSheetExporter on many score sheet templates (encodings, column
orders, odd rows, Remarks column), ScoreFormatter, RunAuditFilter, and
the JSON and anomaly exporters on a small run. The fixed test settings
have `export.csv.extra.columns=false`, as by default; one check exports
with it off and expects the provided format, and the others turn it on
to check the question and Remarks columns. Pass
`-Dreport.out=folder` to keep the JSON file.

## Service checks (ServiceTest)

SettingsService (validation, saving with comments and line endings
kept), GradingService with a pipeline that only unpacks, checks and
fixes, and AssessmentLibrary (save, list, validate, use and rename).

## Window logic checks (UiLogicTest)

CheatingRemarks (the Remarks column of the score sheet) driven by a fake
backend, and the SectionProgress rules that need no FlowState. No window
is opened.

## Similar code remarks checks (CheatingRemarksTest)

A real backend on a temporary database: Mark all never decides Similar
code flags, the activity log says "Kept flagged" for a flag kept, a pair
with one side kept and the other "Looks fine" gets no remark, and a
score sheet of only some students gives the same remarks, with the
other student's name, as a full one.

## Apply fix again checks (ReapplyFixTest)

Grades one upload with an answer in the wrong question folder, then
restarts on the same database: a rejected fix can be applied again and
rejected again, in that session and after another restart, and the
marks follow each time.

## Review checks (ReviewChecksTest)

Strict reading of a typed mark (`2f` and `1e0` are refused), and that a
missing helper file counts as "Did not compile", not "No answer".

## Small fix checks (SmallFixesTest)

Identical copies with `Email ID :` headers and different domains, a
score sheet template with a byte order mark, students left RUNNING
counted as waiting, and adding a section that is already listed.

## Tester strength summary checks (TesterStrengthSummaryTest)

The Set up line for testers whose model answer does not compile names
the questions and the missing classes.
