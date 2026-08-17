package dev.vepo.goodcode.report.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectStatsTest {

    @Test
    void aggregatesEmptyProjectAsZeroes() {
        ProjectStats stats = new ProjectStats();
        assertEquals(0, stats.getPackageCount());
        assertEquals(0, stats.getFileCount());
        assertEquals(0, stats.getTypeCount());
        assertEquals(0.0, stats.getAverageLinesPerFile());
        assertEquals(0.0, stats.getAverageMethodsPerType());
        assertEquals(0.0, stats.getAverageFieldsPerType());
    }

    @Test
    void aggregatesAcrossPackagesFilesAndTypes() {
        PackageInfo pkgOne = new PackageInfo("one");
        SourceFileInfo fileA = new SourceFileInfo("A.java", "one", 20, 2, 3);
        ClassInfo classA = new ClassInfo("A", "one.A", TypeKind.CLASS, false, false, 15);
        classA.addField(new FieldInfo("x", "int", false, false));
        classA.addField(new FieldInfo("y", "int", false, false));
        classA.addConstructor(new ConstructorInfo(0));
        classA.addMethod(new MethodInfo("getX", 0, false, false, 0, Collections.emptyList()));
        fileA.addType(classA);
        pkgOne.addFile(fileA);

        PackageInfo pkgTwo = new PackageInfo("two");
        SourceFileInfo fileB = new SourceFileInfo("B.java", "two", 10, 0, 0);
        ClassInfo classB = new ClassInfo("B", "two.B", TypeKind.RECORD, false, false, 10);
        classB.addField(new FieldInfo("z", "int", false, true));
        fileB.addType(classB);
        pkgTwo.addFile(fileB);

        ProjectStats stats = new ProjectStats();
        stats.addPackage(pkgOne);
        stats.addPackage(pkgTwo);

        assertEquals(2, stats.getPackageCount());
        assertEquals(2, stats.getFileCount());
        assertEquals(2, stats.getTypeCount());
        assertEquals(3, stats.getFieldCount());
        assertEquals(1, stats.getMethodCount());
        assertEquals(1, stats.getConstructorCount());
        assertEquals(30, stats.getTotalLines());
        assertEquals(25, stats.getCodeLines());
        assertEquals(2, stats.getBlankLines());
        assertEquals(3, stats.getCommentLines());

        Map<TypeKind, Long> byKind = stats.getTypeCountByKind();
        assertEquals(1L, byKind.get(TypeKind.CLASS));
        assertEquals(1L, byKind.get(TypeKind.RECORD));
        assertEquals(0L, byKind.get(TypeKind.INTERFACE));

        assertEquals(12.5, stats.getAverageLinesPerFile());
        assertEquals(0.5, stats.getAverageMethodsPerType());
        assertEquals(1.5, stats.getAverageFieldsPerType());
    }
}
