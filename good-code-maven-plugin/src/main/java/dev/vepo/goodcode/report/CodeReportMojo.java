package dev.vepo.goodcode.report;

import dev.vepo.goodcode.report.analyzer.JavaSourceAnalyzer;
import dev.vepo.goodcode.report.model.ProjectStats;
import dev.vepo.goodcode.report.render.GoodCodeReportGenerator;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Analyses the project's Java sources and reports how many packages, types,
 * fields, methods and lines of code they contain, rendering the result as an
 * HTML page under {@code target/site} (bind it to the {@code site} lifecycle,
 * or run {@code mvn good-code:report} directly).
 */
@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE, requiresProject = true, threadSafe = true)
public class CodeReportMojo extends AbstractMavenReport {

    /**
     * Directory containing the Java sources to analyse.
     */
    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    private File sourceDirectory;

    /**
     * Encoding used to read the source files.
     */
    @Parameter(property = "goodcode.encoding", defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /**
     * Title shown at the top of the report and in the project reports index.
     */
    @Parameter(property = "goodcode.reportName", defaultValue = "Good Code Report")
    private String reportName;

    /**
     * Short description shown under the report title.
     */
    @Parameter(property = "goodcode.reportDescription",
            defaultValue = "Source code metrics: lines of code, packages, types, fields and methods.")
    private String reportDescription;

    /**
     * Skips report generation entirely.
     */
    @Parameter(property = "goodcode.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project.reporting.outputDirectory}", required = true, readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private Renderer siteRenderer;

    @Override
    public String getOutputName() {
        return "good-code-report";
    }

    @Override
    public String getName(Locale locale) {
        return reportName;
    }

    @Override
    public String getDescription(Locale locale) {
        return reportDescription;
    }

    @Override
    public String getCategoryName() {
        return CATEGORY_PROJECT_REPORTS;
    }

    @Override
    public boolean canGenerateReport() {
        return !skip && sourceDirectory != null && sourceDirectory.isDirectory();
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        getLog().info("good-code: analysing " + sourceDirectory);

        Charset charset = (encoding == null || encoding.isBlank())
                ? Charset.defaultCharset()
                : Charset.forName(encoding);

        ProjectStats stats;
        try {
            stats = new JavaSourceAnalyzer(getLog(), charset).analyze(sourceDirectory.toPath());
        } catch (IOException e) {
            throw new MavenReportException("good-code: failed to analyse " + sourceDirectory, e);
        }

        getLog().info("good-code: " + stats.getFileCount() + " file(s), " + stats.getTypeCount()
                + " type(s), " + stats.getFieldCount() + " field(s), " + stats.getMethodCount() + " method(s)");

        new GoodCodeReportGenerator(getSink(), locale, reportName, reportDescription).generate(stats);
    }

    @Override
    protected String getOutputDirectory() {
        return outputDirectory.getAbsolutePath();
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }
}
