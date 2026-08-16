# Architecture

This document explains how Good Code is put together, end to end, so you can
work on it without needing anything beyond this repo and the JDK/Maven docs.

## Modules

```
good-code/                      parent POM (packaging: pom), groupId dev.vepo.goodcode
├── good-code-maven-plugin/     the plugin itself (packaging: maven-plugin)
└── good-code-sample/           a throwaway project that applies the plugin
```

- **`good-code-maven-plugin`** contains all the logic: parsing sources,
  aggregating stats, rendering the report. This is the only module you build
  to get a usable artifact.
- **`good-code-sample`** has no logic of its own — it's a handful of `.java`
  files (`Fleet`, `Vehicle`, `Car`, `GpsPosition`, `Trackable`) chosen to
  exercise every kind of type the analyzer understands (class, abstract
  class, interface, enum, record, nested class). Run `mvn site` inside it to
  manually eyeball a real report. It is also useful as an integration smoke
  test: if this module's `mvn site` fails, something in the plugin broke.

## Request flow (`mvn site` → HTML report)

1. Maven's site-rendering lifecycle invokes `CodeReportMojo` (bound to the
   `SITE` phase, category `Project Reports`). This is a **report mojo**: it
   extends `AbstractMavenReport`, the same base class JaCoCo and Checkstyle's
   report mojos use, rather than a plain `AbstractMojo`. That base class is
   why `mvn good-code:report` alone won't produce HTML — only `mvn site`
   wires up the Doxia sink, skin and navigation that report mojos write into.
2. `CodeReportMojo.canGenerateReport()` gates execution on `skip` and on
   `sourceDirectory` existing.
3. `CodeReportMojo.executeReport(Locale)` is the entry point:
   - resolves the `Charset` from the `encoding` parameter,
   - calls `new JavaSourceAnalyzer(log, charset).analyze(sourceDirectory)`,
   - passes the resulting `ProjectStats` to
     `new GoodCodeReportGenerator(getSink(), ...).generate(stats)`.

Everything downstream of step 3 is plain Java with no Maven/Mojo
dependencies except the `Log` interface and the `Sink` interface — this is
what makes it unit-testable (see [`TESTING.md`](TESTING.md)).

## Package layout (`good-code-maven-plugin`)

```
dev.vepo.goodcode.report
├── CodeReportMojo              the Mojo — Maven-facing entry point only
├── analyzer/
│   ├── JavaSourceAnalyzer      walks the source tree, drives JavaParser, builds the model
│   └── LineCounter             classifies each physical line as blank/comment/code
├── model/                      plain data classes, no behaviour beyond aggregation
│   ├── ProjectStats            root: all packages + totals used in the summary
│   ├── PackageInfo             one Java package's files
│   ├── SourceFileInfo          one .java file's line counts + types found in it
│   ├── ClassInfo               one class/interface/enum/record/annotation
│   ├── FieldInfo / MethodInfo  leaf data
│   └── TypeKind                CLASS / INTERFACE / ENUM / RECORD / ANNOTATION
└── render/
    └── GoodCodeReportGenerator turns a ProjectStats into Doxia Sink calls (→ HTML)
```

The data flow is strictly one-directional and mirrors the package layout:

```
CodeReportMojo
  └─▶ analyzer.JavaSourceAnalyzer.analyze(Path)  ──▶  model.ProjectStats
                                                          └─▶ render.GoodCodeReportGenerator.generate(...)
```

### `JavaSourceAnalyzer`

- Walks `sourceRoot` with `Files.walk`, filters to `*.java`, sorts for
  deterministic output.
- For each file: reads it, runs `LineCounter` over the raw lines, then parses
  it with JavaParser's `StaticJavaParser` (configured for
  `LanguageLevel.BLEEDING_EDGE` so newer syntax like records doesn't fail
  parsing).
- Per file it collects **every** `TypeDeclaration` found — `findAll` returns
  top-level *and* nested types, which is how `ClassInfo.isNested()` gets its
  value (`!type.isTopLevelType()`).
- Record components are a JavaParser quirk worth knowing: they're modeled as
  constructor-like `Parameter` nodes, not `FieldDeclaration`s, so
  `toClassInfo` special-cases `RecordDeclaration` to synthesize `FieldInfo`
  entries for them (implicitly `final`, not `static`).
- A file that fails to parse is caught, logged as a `Log.warn`, and skipped —
  the whole report never fails the build over one bad file.
- Files are grouped into `PackageInfo` by package name (`""` becomes the
  literal label `(default package)`, see `PackageInfo`'s constructor), then
  packages are sorted alphabetically into the final `ProjectStats`.

### `LineCounter`

A single static method, `count(List<String> lines)`, with three states per
line: blank (after trim), comment, or (implicitly) code. It is intentionally
a `cloc`-style heuristic, **not** a lexer:

- `//` line comments are recognised if they start the trimmed line.
- Block comments (`/* ... */`) are tracked with one boolean,
  `inBlockComment`. A block comment that both opens and closes on the same
  line is handled; a block comment that closes and is followed by real code
  on the same closing line (`*/ int x = 1;`) is counted **entirely** as a
  comment line — this is a known, documented approximation, not a bug. See
  the class javadoc and `LineCounterTest` for the exact cases covered.

### Model classes (`model/`)

All plain, mutable-by-addition data holders (`addField`, `addMethod`,
`addFile`, `addPackage`, `addType`) with derived getters computed on demand
via streams (`getFieldCount()`, `getCodeLines()`, `getAverageLinesPerFile()`,
...). Nothing here parses anything or knows about Maven — that separation is
what lets `model/` and `analyzer/` be tested with plain JUnit, no Mojo
harness required.

### `GoodCodeReportGenerator`

Pure Doxia `Sink` calls (`sink.section1()` / `sink.table()` / `sink.text()`
/ ..., always paired with the matching `_()` close call) producing four
sections in order: **Summary**, **Types by kind**, **Packages**, **Types**.
It has no knowledge of files or parsing — it only reads the `ProjectStats`
tree via its getters. This is why it can be tested with a lightweight
recording `Sink` instead of running the whole Maven site lifecycle (see
`GoodCodeReportGeneratorTest`).

## Why a report mojo instead of a normal one

Maven has two mojo flavors relevant here:

- A normal `AbstractMojo` runs standalone (`mvn <plugin>:<goal>`) and does
  whatever it wants.
- A **report** mojo (`AbstractMavenReport`) is designed to run inside the
  site-rendering lifecycle. It gets a `Sink` handed to it, and the site
  plugin wraps whatever it writes with the project's skin, navigation and
  cross-links to other reports (Surefire, JaCoCo, etc.) automatically.

Good Code deliberately chose the report-mojo shape (like JaCoCo/Checkstyle)
so its output sits naturally next to those other reports under
`target/site`. The trade-off, called out in the README, is that you must run
`mvn site` — running the goal directly is not supported by this mojo shape.

## Known limitations (carried over into design decisions)

- `LineCounter` is line-based, not a real lexer (see above).
- No complexity metrics (cyclomatic/cognitive), no historical trend, no
  pass/fail quality gate — this is a first version focused on structural
  counts only.
- A file that fails to parse is skipped with a warning rather than failing
  the build (`JavaSourceAnalyzer.analyze`, try/catch per file).

If you're picking up work here, `CodeReportMojo` → `JavaSourceAnalyzer` →
`model/*` → `GoodCodeReportGenerator` is the order to read the source in;
it's also the order data flows through the plugin.
