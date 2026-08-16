package dev.vepo.goodcode.report.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates every {@link SourceFileInfo} that belongs to the same Java package.
 */
public class PackageInfo {

    private final String name;
    private final List<SourceFileInfo> files = new ArrayList<>();

    public PackageInfo(String name) {
        this.name = name == null || name.isEmpty() ? "(default package)" : name;
    }

    public void addFile(SourceFileInfo file) {
        files.add(file);
    }

    public String getName() {
        return name;
    }

    public List<SourceFileInfo> getFiles() {
        return files;
    }

    public int getFileCount() {
        return files.size();
    }

    public int getTypeCount() {
        return files.stream().mapToInt(f -> f.getTypes().size()).sum();
    }

    public int getFieldCount() {
        return files.stream().flatMap(f -> f.getTypes().stream()).mapToInt(ClassInfo::getFieldCount).sum();
    }

    public int getMethodCount() {
        return files.stream().flatMap(f -> f.getTypes().stream()).mapToInt(ClassInfo::getMethodCount).sum();
    }

    public int getConstructorCount() {
        return files.stream().flatMap(f -> f.getTypes().stream()).mapToInt(ClassInfo::getConstructorCount).sum();
    }

    public int getCodeLines() {
        return files.stream().mapToInt(SourceFileInfo::getCodeLines).sum();
    }

    public int getTotalLines() {
        return files.stream().mapToInt(SourceFileInfo::getTotalLines).sum();
    }


}
