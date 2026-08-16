# Contributing / Developer Guide

This is the "how do I actually work on this repo" doc. Read
[`README.md`](README.md) first for what the project does and how to *use*
it; read [`ARCHITECTURE.md`](ARCHITECTURE.md) for how it's built internally;
this doc is about the day-to-day loop of changing code and checking your
work.

## Prerequisites

- JDK 17+ (the build sets `maven.compiler.release` to 17).
- Maven 3.9+ (any recent Maven works; the plugin itself targets Maven 3.9.9
  APIs via `maven.version` in `good-code-maven-plugin/pom.xml`).
- No other services, containers, or network access are needed to build or
  test.

## Repository layout

```
good-code/
├── pom.xml                     parent POM — shared groupId/version/Java level
├── good-code-maven-plugin/     the plugin (all the logic lives here)
│   ├── src/main/java/...       see ARCHITECTURE.md for the package map
│   └── src/test/java/...       unit tests, see TESTING.md
└── good-code-sample/           sample project used to manually run the report
```

`good-code-maven-plugin` and `good-code-sample` share one Maven "reactor"
(the parent `pom.xml`'s `<modules>`), and they share one
version/groupId (`dev.vepo.goodcode`, currently `1.0.0-SNAPSHOT`) via the
parent POM.

## The day-to-day loop

```bash
# 1. Build + install everything (plugin + sample), running unit tests
mvn install

# 2. Point-run just the plugin's unit tests while iterating
mvn -f good-code-maven-plugin/pom.xml test

# 3. See your change's effect on a real report
mvn -f good-code-sample/pom.xml site
open good-code-sample/target/site/good-code-report.html   # or xdg-open on Linux
```

Step 3 matters even for logic-only changes: `good-code-sample` is the only
thing that exercises the real Maven `SITE` lifecycle (parameter injection,
`Sink` wiring, HTML rendering) — see [`TESTING.md`](TESTING.md) for why that
isn't covered by unit tests instead.

If you only changed the plugin and want to re-run the sample without a full
reactor build:

```bash
mvn -f good-code-maven-plugin/pom.xml install   # re-publish the plugin jar locally
mvn -f good-code-sample/pom.xml site
```

## Making a change: typical flows

**Changing what counts as a comment/blank/code line**
→ `good-code-maven-plugin/src/main/java/dev/vepo/goodcode/report/analyzer/LineCounter.java`.
Add a case to `LineCounterTest` first (it's a pure function, cheapest thing
in the repo to test) before touching the implementation.

**Changing what gets extracted from a type (new field/method attribute,
new `TypeKind`, etc.)**
→ `JavaSourceAnalyzer.toClassInfo(...)` and the relevant `model/` class.
Add/extend a case in `JavaSourceAnalyzerTest` with a small source snippet
(`@TempDir` + text block) that isolates the new behavior.

**Changing the report's content or layout**
→ `render/GoodCodeReportGenerator.java`. It only calls the Doxia `Sink` API
— pair every "open" call (`section1()`, `table()`, `tableRow()`, ...) with
its matching "close" call (`section1_()`, `table_()`, `tableRow_()`, ...) or
the generated HTML will be malformed. Verify with
`GoodCodeReportGeneratorTest` for text/values and with
`mvn -f good-code-sample/pom.xml site` for the actual rendered HTML.

**Changing a `@Parameter` on the Mojo (name, default, property key)**
→ `CodeReportMojo.java`. Update the parameter table in `README.md` to match,
and re-run the sample to confirm the new default/behavior takes effect.

## Before you consider a change done

1. `mvn install` passes (compiles both modules, runs all unit tests).
2. `mvn -f good-code-sample/pom.xml site` produces a report and
   `good-code-report.html` looks correct for your change.
3. New behavior has a test per the table in [`TESTING.md`](TESTING.md).
4. If you touched a `@Parameter`, the table in `README.md` is updated.
5. If you touched the module layout, the data-flow diagram or package map in
   `ARCHITECTURE.md` is updated.

## Coding conventions observed in this repo

- Model classes (`model/`) are plain data holders: constructor + `addX`
  mutators + derived getters computed with streams. No parsing or Maven
  knowledge belongs there — keep that separation so they stay trivially
  testable.
- `analyzer/` and `render/` depend only on `org.apache.maven.plugin.logging.Log`
  and `org.apache.maven.doxia.sink.Sink` respectively from the Maven world —
  not on `MavenProject`, `MavenSession`, or anything Mojo-specific. That's
  what keeps them unit-testable without a plugin test harness.
- Javadoc is used sparingly, on classes/methods where the *why* isn't
  obvious from the name (e.g. the record-component quirk in
  `JavaSourceAnalyzer`, or the block-comment heuristic in `LineCounter`) —
  not restating what a getter does.
