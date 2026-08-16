package dev.vepo.goodcode.report.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metrics collected for a single {@code .java} source file.
 */
public class SourceFileInfo {

    private final String fileName;
    private final String packageName;
    private final int totalLines;
    private final int blankLines;
    private final int commentLines;
    private final int codeLines;
    private final List<ClassInfo> types = new ArrayList<>();

    public SourceFileInfo(String fileName, String packageName, int totalLines, int blankLines, int commentLines) {
        this.fileName = fileName;
        this.packageName = packageName;
        this.totalLines = totalLines;
        this.blankLines = blankLines;
        this.commentLines = commentLines;
        this.codeLines = totalLines - blankLines - commentLines;
    }

    public void addType(ClassInfo type) {
        types.add(type);
    }

    public String getFileName() {
        return fileName;
    }

    public String getPackageName() {
        return packageName;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getBlankLines() {
        return blankLines;
    }

    public int getCommentLines() {
        return commentLines;
    }

    public int getCodeLines() {
        return codeLines;
    }

    public List<ClassInfo> getTypes() {
        return types;
    }
}
