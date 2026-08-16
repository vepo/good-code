# Testing

## Running the tests

```bash
mvn test                                   # unit tests only, whole project
mvn -f good-code-maven-plugin/pom.xml test # unit tests, plugin module only
```

Tests use **JUnit 5** (`junit-jupiter`) and run through the standard
`maven-surefire-plugin` — no extra setup, no test containers, no network
access required.

## What's covered, and where

All unit tests live under
`good-code-maven-plugin/src/test/java/dev/vepo/goodcode/report/`, mirroring
the main package layout described in [`ARCHITECTURE.md`](ARCHITECTURE.md):

| Test class | Exercises |
|---|---|
| `analyzer/LineCounterTest` | The blank/comment/code line heuristic: plain code, blank/whitespace-only lines, `//` comments, single-line and multi-line `/* */` blocks, and the documented edge case where code following a `*/` on the same line is counted as comment. |
| `analyzer/JavaSourceAnalyzerTest` | End-to-end parsing of real source snippets written to a `@TempDir`: package grouping (including the default package), every `TypeKind` (class/interface/enum/record/annotation), record components becoming synthetic final fields, nested types, field/method modifiers (static/final/abstract), constructors, and that a file which fails to parse is skipped with a warning instead of failing the whole analysis. |
| `model/PackageInfoTest`, `model/ClassInfoTest`, `model/ProjectStatsTest` | Aggregation math on the plain data model (counts, sums, averages, the `(default package)` label) using hand-built objects — no parsing involved. |
| `render/GoodCodeReportGeneratorTest` | That `generate()` emits the expected title, section headings, and summary/table values into the Doxia `Sink`. |

## Why there's no mock Maven runtime

`CodeReportMojo` itself (the `@Mojo`-annotated class Maven instantiates) is
deliberately left untested by unit tests: it is a thin adapter that reads
`@Parameter` fields and delegates to `JavaSourceAnalyzer` and
`GoodCodeReportGenerator`, both of which *are* fully unit tested in
isolation. Testing the Mojo itself would mean standing up a Maven plugin
test harness (`maven-plugin-testing-harness`) for very little additional
coverage.

Instead, the **`good-code-sample`** module is the integration check for the
Mojo wiring: running `mvn -f good-code-sample/pom.xml site` exercises the
real `SITE`-lifecycle path (parameter injection, `Sink` creation by the site
renderer, HTML output) end to end. If you change anything in
`CodeReportMojo` — parameter names, defaults, `canGenerateReport()` logic —
re-run that command and open
`good-code-sample/target/site/good-code-report.html` to confirm the report
still renders.

## `GoodCodeReportGeneratorTest`'s recording `Sink`

`org.apache.maven.doxia.sink.Sink` is a large interface (dozens of void
structural methods like `section1()`/`table()`/`tableRow()`). Rather than
hand-implementing all of them, the test builds a `java.lang.reflect.Proxy`
that only records the string arguments passed to `text(String)` and no-ops
everything else. That's enough to assert on the report's actual output
(titles, headings, computed values) without coupling the test to Doxia's
full API surface. See `GoodCodeReportGeneratorTest.RecordingSink` if you
need to extend it (e.g. to assert on table structure, not just text).

## Adding new tests

- Pure model/analyzer logic → plain JUnit 5, no Maven APIs needed beyond
  `org.apache.maven.plugin.logging.SystemStreamLog` (a real, ready-to-use
  `Log` implementation) if a `Log` is required.
- New source-parsing behavior → add a case to `JavaSourceAnalyzerTest` using
  `@TempDir` and a Java text block; write the smallest snippet that isolates
  the behavior.
- New report content → add assertions to `GoodCodeReportGeneratorTest`
  against `RecordingSink.texts`.
