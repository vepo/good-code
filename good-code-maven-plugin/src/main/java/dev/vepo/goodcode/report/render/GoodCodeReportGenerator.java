package dev.vepo.goodcode.report.render;

import dev.vepo.goodcode.report.model.*;
import org.apache.maven.doxia.sink.Sink;

import java.util.Locale;
import java.util.Map;

/**
 * Renders a {@link ProjectStats} into the Doxia {@link Sink}, which the Maven
 * Site plugin then turns into an HTML page under {@code target/site}, the same
 * way it does for JaCoCo, Checkstyle or Surefire reports.
 */
public class GoodCodeReportGenerator {

    private final Sink sink;
    private final Locale locale;
    private final String reportName;
    private final String reportDescription;

    public GoodCodeReportGenerator(Sink sink, Locale locale, String reportName, String reportDescription) {
        this.sink = sink;
        this.locale = locale;
        this.reportName = reportName;
        this.reportDescription = reportDescription;
    }

    public void generate(ProjectStats stats) {
        head();
        body(stats);
    }

    private void head() {
        sink.head();
        sink.title();
        sink.text(reportName);
        sink.title_();
        sink.head_();
    }

    private void body(ProjectStats stats) {
        sink.body();

        sink.section1();
        sink.sectionTitle1();
        sink.text(reportName);
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text(reportDescription);
        sink.paragraph_();
        sink.section1_();

        summarySection(stats);
        typeBreakdownSection(stats);
        packagesSection(stats);
        classesSection(stats);
        usagesSection(stats);

        sink.body_();
    }

    private void summarySection(ProjectStats stats) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Summary");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Metric", "Value");
        row("Packages", String.valueOf(stats.getPackageCount()));
        row("Source files", String.valueOf(stats.getFileCount()));
        row("Types (classes/interfaces/enums/records/annotations)", String.valueOf(stats.getTypeCount()));
        row("Fields", String.valueOf(stats.getFieldCount()));
        row("Methods (incl. constructors)", String.valueOf(stats.getMethodCount()));
        row("Lines of code", String.valueOf(stats.getCodeLines()));
        row("Comment lines", String.valueOf(stats.getCommentLines()));
        row("Blank lines", String.valueOf(stats.getBlankLines()));
        row("Total lines", String.valueOf(stats.getTotalLines()));
        row("Average lines of code per file", format(stats.getAverageLinesPerFile()));
        row("Average methods per type", format(stats.getAverageMethodsPerType()));
        row("Average fields per type", format(stats.getAverageFieldsPerType()));
        sink.table_();

        sink.section1_();
    }

    private void typeBreakdownSection(ProjectStats stats) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Types by kind");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Kind", "Count");
        Map<TypeKind, Long> byKind = stats.getTypeCountByKind();
        for (TypeKind kind : TypeKind.values()) {
            row(capitalize(kind.name()), String.valueOf(byKind.getOrDefault(kind, 0L)));
        }
        sink.table_();

        sink.section1_();
    }

    private void packagesSection(ProjectStats stats) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Packages");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Package", "Files", "Types", "Fields", "Methods", "Lines of code");
        for (PackageInfo pkg : stats.getPackages()) {
            row(pkg.getName(),
                    String.valueOf(pkg.getFileCount()),
                    String.valueOf(pkg.getTypeCount()),
                    String.valueOf(pkg.getFieldCount()),
                    String.valueOf(pkg.getMethodCount()),
                    String.valueOf(pkg.getCodeLines()));
        }
        sink.table_();

        sink.section1_();
    }

    private void usagesSection(ProjectStats stats) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Usages");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Package", "Type", "Method", "Class", "Usage");
        for (PackageInfo pkg : stats.getPackages()) {
            for (SourceFileInfo file : pkg.getFiles()) {
                for (ClassInfo type : file.getTypes()) {
                    for (MethodInfo method : type.getMethods()) {
                        for (ClassUsage classUsage : method.dependsOn()) {
                            row(pkg.getName(),
                                    type.getQualifiedName(),
                                    method.name(),
                                    classUsage.fullyQualifiedName(),
                                    Integer.toString(classUsage.counter()));
                        }
                    }
                }
            }
        }
        sink.table_();
        sink.section1_();
    }

    private void classesSection(ProjectStats stats) {
        sink.section1();
        sink.sectionTitle1();
        sink.text("Types");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Package", "Type", "Kind", "Nested", "Abstract", "Lines of code", "Fields", "Methods");
        for (PackageInfo pkg : stats.getPackages()) {
            for (SourceFileInfo file : pkg.getFiles()) {
                for (ClassInfo type : file.getTypes()) {
                    row(pkg.getName(),
                            type.getSimpleName(),
                            capitalize(type.getKind().name()),
                            type.isNested() ? "yes" : "no",
                            type.isAbstract() ? "yes" : "no",
                            String.valueOf(type.getLinesOfCode()),
                            String.valueOf(type.getFieldCount()),
                            String.valueOf(type.getMethodCount()));
                }
            }
        }
        sink.table_();

        sink.section1_();
    }

    private void headerRow(String... cells) {
        sink.tableRow();
        for (String cell : cells) {
            sink.tableHeaderCell();
            sink.text(cell);
            sink.tableHeaderCell_();
        }
        sink.tableRow_();
    }

    private void row(String... cells) {
        sink.tableRow();
        for (String cell : cells) {
            sink.tableCell();
            sink.text(cell);
            sink.tableCell_();
        }
        sink.tableRow_();
    }

    private String format(double value) {
        return String.format(locale, "%.1f", value);
    }

    private String capitalize(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(locale);
    }
}
