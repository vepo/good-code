# Good Code

A Maven report plugin that analyses a project's Java source code and renders the
result as an HTML page under `target/site`, the same way JaCoCo, Checkstyle or
Surefire reports do.

This is a **first version**: it focuses on structural, static counts rather than
quality rules (no thresholds, no pass/fail gate, no complexity metrics yet).

## What it reports

For every `.java` file under the configured source directory:

- **Lines**: total lines, lines of code, comment lines, blank lines
- **Types**: classes, interfaces, enums, records, annotations (top-level and nested)
- **Fields** per type (record components count as fields too)
- **Methods** per type (constructors included)
- **Packages**: same metrics aggregated per package

The report has four sections: an overall **Summary**, **Types by kind**, a
**Packages** breakdown table, and a **Types** table listing every class/interface/
enum/record found, with its own line/field/method counts.

## Modules

- `good-code-maven-plugin` — the report plugin itself (a `maven-plugin` module).
- `good-code-sample` — a small sample project that applies the plugin, used to
  exercise and manually verify the report.

## Building

```bash
mvn install
```

This builds and installs both modules into your local repository
(`~/.m2/repository`), running the unit test suite in the process.

## Testing

```bash
mvn test
```

Runs the plugin's JUnit 5 unit tests (line counting, source parsing, model
aggregation, report rendering). See [`TESTING.md`](TESTING.md) for what's
covered and why. For an end-to-end check, run the sample (see "Trying the
sample" below) and open the generated report.

## Using it in a project

Add it to the `<reporting>` section of your `pom.xml`:

```xml
<reporting>
  <plugins>
    <plugin>
      <groupId>dev.vepo</groupId>
      <artifactId>good-code-maven-plugin</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </plugin>
  </plugins>
</reporting>
```

Then generate the site:

```bash
mvn site
```

Open `target/site/good-code-report.html` in a browser.

> Report mojos built on Maven's reporting API (the same base class JaCoCo and
> Checkstyle use) are designed to run through the site-rendering lifecycle
> (`mvn site`), which is what wires up the skin, navigation and HTML rendering.
> Running the goal directly on the CLI (`mvn good-code:report`) is not
> supported by this first version.

### Configuration

All parameters are optional:

| Parameter | Property | Default | Description |
|---|---|---|---|
| `sourceDirectory` | - | `${project.build.sourceDirectory}` | Directory to analyse |
| `encoding` | `goodcode.encoding` | `${project.build.sourceEncoding}` | Charset used to read source files |
| `reportName` | `goodcode.reportName` | `Good Code Report` | Report title |
| `reportDescription` | `goodcode.reportDescription` | *(see source)* | Subtitle under the title |
| `skip` | `goodcode.skip` | `false` | Skips the report entirely |

## Trying the sample

```bash
mvn install                          # builds and installs the plugin
mvn -f good-code-sample/pom.xml site # runs it against the sample sources
open good-code-sample/target/site/good-code-report.html
```

## Known limitations (v1)

- Comment/blank line detection is a line-based heuristic (similar to `cloc`),
  not a full lexer — a `/* ... */` block that opens and closes with code on
  the same line is approximated.
- No historical trend, no complexity (cyclomatic, cognitive) metrics, no
  pass/fail quality gate yet.
- Files that fail to parse are logged as a warning and skipped, rather than
  failing the build.

## Developing

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — how the plugin is put together
  internally: module layout, package map, and how a `.java` file turns into
  a row in the HTML report.
- [`TESTING.md`](TESTING.md) — what's tested, where, and how to add a test.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — day-to-day dev loop (build, test,
  run the sample) and where to make common changes.
