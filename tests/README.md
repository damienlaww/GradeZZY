Run the ingestion regression checks from the repository root with a JDK:

```sh
sh compile.sh
mkdir -p /tmp/gradezzy-tests
javac -cp classes -d /tmp/gradezzy-tests tests/*.java
java -cp classes:/tmp/gradezzy-tests IngestRegressionTest
```

On Windows, use `compile.bat` and `;` as the Java classpath separator.
The javac line compiles every file in `tests/`, including the two helper
classes the ingestion checks use: `ExtractOnlyPipelineFactory` and
`NoTestersSource`.
The checks create and remove temporary uploads, rosters and working copies.
They exercise ingestion through `GradingService` with a pipeline containing
only extraction, plus UI batch eligibility and ZIP traversal protection.
They do not compile or execute student code.

Run the answer group checks the same way:

```sh
sh compile.sh
mkdir -p /tmp/gradezzy-tests
javac -cp classes -d /tmp/gradezzy-tests tests/*.java
java -cp "classes:lib/*:/tmp/gradezzy-tests" AnswerGroupsTest
```

They cover the code shape tokens, how answers are grouped and titled,
saving names and checks (in memory and in a temporary SQLite file), and
the common mistakes CSV and the JSON `mistake` field.
