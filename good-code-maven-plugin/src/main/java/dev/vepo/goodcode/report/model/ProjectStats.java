package dev.vepo.goodcode.report.model;

import java.util.*;

/**
 * Top-level result of analysing a source tree: one {@link PackageInfo} per Java
 * package plus the totals used in the report's summary table.
 */
public class ProjectStats {

    private final List<PackageInfo> packages = new ArrayList<>();

    public void addPackage(PackageInfo pkg) {
        packages.add(pkg);
    }

    public List<PackageInfo> getPackages() {
        return packages;
    }

    private List<SourceFileInfo> allFiles() {
        List<SourceFileInfo> files = new ArrayList<>();
        packages.forEach(p -> files.addAll(p.getFiles()));
        return files;
    }

    private List<ClassInfo> allTypes() {
        List<ClassInfo> types = new ArrayList<>();
        allFiles().forEach(f -> types.addAll(f.getTypes()));
        return types;
    }

    public int getPackageCount() {
        return packages.size();
    }

    public int getFileCount() {
        return allFiles().size();
    }

    public int getTotalLines() {
        return allFiles().stream().mapToInt(SourceFileInfo::getTotalLines).sum();
    }

    public int getCodeLines() {
        return allFiles().stream().mapToInt(SourceFileInfo::getCodeLines).sum();
    }

    public int getBlankLines() {
        return allFiles().stream().mapToInt(SourceFileInfo::getBlankLines).sum();
    }

    public int getCommentLines() {
        return allFiles().stream().mapToInt(SourceFileInfo::getCommentLines).sum();
    }

    public int getTypeCount() {
        return allTypes().size();
    }

    public int getFieldCount() {
        return allTypes().stream().mapToInt(ClassInfo::getFieldCount).sum();
    }

    public int getMethodCount() {
        return allTypes().stream().mapToInt(ClassInfo::getMethodCount).sum();
    }

    public int getConstructorCount() {
        return allTypes().stream().mapToInt(ClassInfo::getConstructorCount).sum();
    }

    public Map<TypeKind, Long> getTypeCountByKind() {
        Map<TypeKind, Long> result = new EnumMap<>(TypeKind.class);
        for (TypeKind kind : TypeKind.values()) {
            result.put(kind, allTypes().stream().filter(t -> t.getKind() == kind).count());
        }
        return result;
    }

    public double getAverageLinesPerFile() {
        int files = getFileCount();
        return files == 0 ? 0 : (double) getCodeLines() / files;
    }

    public double getAverageMethodsPerType() {
        int types = getTypeCount();
        return types == 0 ? 0 : (double) getMethodCount() / types;
    }

    public double getAverageFieldsPerType() {
        int types = getTypeCount();
        return types == 0 ? 0 : (double) getFieldCount() / types;
    }

    public Optional<MethodInfo> findMethod(String methodSignature) {
        int methodSeparator = methodSignature.lastIndexOf('.');
        if (methodSeparator >= 1) {
            var methodName = methodSignature.substring(methodSeparator + 1);
            var classFqn = methodSignature.substring(0, methodSeparator);
            return allTypes().stream()
                    .filter(clz -> clz.getQualifiedName().equals(classFqn))
                    .findFirst()
                    .flatMap(clz -> clz.getMethods().stream().filter(m -> m.name().equals(methodName))
                            .findFirst());


        }
        return Optional.empty();
    }
}
