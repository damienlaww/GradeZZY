Run the ingestion regression checks from the repository root with a JDK:

```sh
sh compile.sh
mkdir -p /tmp/gradezzy-tests
javac -cp classes -d /tmp/gradezzy-tests tests/IngestRegressionTest.java
java -cp classes:/tmp/gradezzy-tests IngestRegressionTest
```

On Windows, use `compile.bat` and `;` as the Java classpath separator.
The checks create and remove temporary uploads, rosters and working copies.
They exercise ingestion through `GradingService` with a pipeline containing
only extraction, plus UI batch eligibility and ZIP traversal protection.
They do not compile or execute student code.
