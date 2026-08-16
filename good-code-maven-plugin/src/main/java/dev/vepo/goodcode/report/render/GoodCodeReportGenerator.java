package dev.vepo.goodcode.report.render;

import dev.vepo.goodcode.report.model.*;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;

import java.util.*;

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

    // ----------------------------------------
    // Summary, type breakdown, packages – unchanged
    // ----------------------------------------

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
        // Build a structured view: package -> type -> method -> list of class usages
        Map<String, Map<String, Map<String, List<ClassUsage>>>> data = new LinkedHashMap<>();

        for (PackageInfo pkg : stats.getPackages()) {
            String pkgName = pkg.getName();
            Map<String, Map<String, List<ClassUsage>>> typesMap = new LinkedHashMap<>();
            for (SourceFileInfo file : pkg.getFiles()) {
                for (ClassInfo type : file.getTypes()) {
                    String typeQName = type.getQualifiedName();
                    Map<String, List<ClassUsage>> methodsMap = new LinkedHashMap<>();
                    for (MethodInfo method : type.getMethods()) {
                        if (!method.dependsOn().isEmpty()) {
                            methodsMap.put(method.name(), method.dependsOn());
                        }
                    }
                    if (!methodsMap.isEmpty()) {
                        typesMap.put(typeQName, methodsMap);
                    }
                }
            }
            if (!typesMap.isEmpty()) {
                data.put(pkgName, typesMap);
            }
        }

        if (data.isEmpty()) {
            // No usages to show, maybe skip the section entirely
            return;
        }

        sink.section1();
        sink.sectionTitle1();
        sink.text("Usages");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Package", "Type", "Method", "Class", "Usage");

        for (Map.Entry<String, Map<String, Map<String, List<ClassUsage>>>> pkgEntry : data.entrySet()) {
            String pkgName = pkgEntry.getKey();
            int pkgRowCount = pkgEntry.getValue().values().stream()
                    .mapToInt(methods -> methods.values().stream().mapToInt(List::size).sum())
                    .sum();

            boolean firstPkgRow = true;
            for (Map.Entry<String, Map<String, List<ClassUsage>>> typeEntry : pkgEntry.getValue().entrySet()) {
                String typeQName = typeEntry.getKey();
                int typeRowCount = typeEntry.getValue().values().stream().mapToInt(List::size).sum();

                boolean firstTypeRow = true;
                for (Map.Entry<String, List<ClassUsage>> methodEntry : typeEntry.getValue().entrySet()) {
                    String methodName = methodEntry.getKey();
                    List<ClassUsage> usages = methodEntry.getValue();
                    int methodRowCount = usages.size();

                    boolean firstMethodRow = true;
                    for (ClassUsage usage : usages) {
                        sink.tableRow();

                        // Package cell – span only on first row of package
                        if (firstPkgRow) {
                            cellWithRowSpan(pkgName, pkgRowCount);
                            firstPkgRow = false;
                        }

                        // Type cell – span only on first row of type
                        if (firstTypeRow) {
                            cellWithRowSpan(typeQName, typeRowCount);
                            firstTypeRow = false;
                        }

                        // Method cell – span only on first row of method
                        if (firstMethodRow) {
                            cellWithRowSpan(methodName, methodRowCount);
                            firstMethodRow = false;
                        }

                        // Class and counter cells (no span)
                        cell(usage.fullyQualifiedName());
                        cell(Integer.toString(usage.counter()));

                        sink.tableRow_();
                    }
                }
            }
        }

        sink.table_();
        sink.section1_();
    }

    private void classesSection(ProjectStats stats) {
        // Group types by package
        Map<String, List<ClassInfo>> packageToTypes = new LinkedHashMap<>();
        for (PackageInfo pkg : stats.getPackages()) {
            String pkgName = pkg.getName();
            List<ClassInfo> types = new ArrayList<>();
            for (SourceFileInfo file : pkg.getFiles()) {
                types.addAll(file.getTypes());
            }
            if (!types.isEmpty()) {
                packageToTypes.put(pkgName, types);
            }
        }

        if (packageToTypes.isEmpty()) {
            return;
        }

        sink.section1();
        sink.sectionTitle1();
        sink.text("Types");
        sink.sectionTitle1_();

        sink.table();
        headerRow("Package", "Type", "Kind", "Nested", "Abstract", "Lines of code", "Fields", "Methods");

        for (Map.Entry<String, List<ClassInfo>> pkgEntry : packageToTypes.entrySet()) {
            String pkgName = pkgEntry.getKey();
            List<ClassInfo> types = pkgEntry.getValue();
            int pkgRowCount = types.size();

            boolean firstRow = true;
            for (ClassInfo type : types) {
                sink.tableRow();

                if (firstRow) {
                    cellWithRowSpan(pkgName, pkgRowCount);
                    firstRow = false;
                }

                cell(type.getSimpleName());
                cell(capitalize(type.getKind().name()));
                cell(type.isNested() ? "yes" : "no");
                cell(type.isAbstract() ? "yes" : "no");
                cell(String.valueOf(type.getLinesOfCode()));
                cell(String.valueOf(type.getFieldCount()));
                cell(String.valueOf(type.getMethodCount()));

                sink.tableRow_();
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

    // Renders a normal table cell
    private void cell(String text) {
        sink.tableCell();
        sink.text(text);
        sink.tableCell_();
    }

    // Renders a table cell with a rowspan attribute
    private void cellWithRowSpan(String text, int rowspan) {
        var attrs = new SinkEventAttributeSet();
        attrs.addAttribute(SinkEventAttributes.ROWSPAN, rowspan);
        sink.tableCell(attrs);
        sink.text(text);
        sink.tableCell_();
    }

    private String format(double value) {
        return String.format(locale, "%.1f", value);
    }

    private String capitalize(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(locale);
    }
}