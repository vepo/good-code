package dev.vepo.goodcode.report.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageInfoTest {

    @Test
    void nullOrEmptyNameBecomesDefaultPackageLabel() {
        assertEquals("(default package)", new PackageInfo(null).getName());
        assertEquals("(default package)", new PackageInfo("").getName());
    }

    @Test
    void namedPackageKeepsItsName() {
        assertEquals("dev.vepo.goodcode.sample", new PackageInfo("dev.vepo.goodcode.sample").getName());
    }

    @Test
    void aggregatesMetricsAcrossItsFiles() {
        PackageInfo pkg = new PackageInfo("sample");

        SourceFileInfo fileA = new SourceFileInfo("A.java", "sample", 10, 1, 2);
        ClassInfo classA = new ClassInfo("A", "sample.A", TypeKind.CLASS, false, false, 8);
        classA.addField(new FieldInfo("x", "int", false, false));
        classA.addConstructor(new ConstructorInfo(0));
        fileA.addType(classA);

        SourceFileInfo fileB = new SourceFileInfo("B.java", "sample", 5, 0, 0);
        ClassInfo classB = new ClassInfo("B", "sample.B", TypeKind.INTERFACE, false, false, 5);
        classB.addMethod(new MethodInfo("doIt", 1, false, true, 0, Collections.emptyList()));
        fileB.addType(classB);

        pkg.addFile(fileA);
        pkg.addFile(fileB);

        assertEquals(2, pkg.getFileCount());
        assertEquals(2, pkg.getTypeCount());
        assertEquals(1, pkg.getFieldCount());
        assertEquals(1, pkg.getMethodCount());
        assertEquals(1, pkg.getConstructorCount());
        assertEquals(15, pkg.getTotalLines());
        assertEquals(12, pkg.getCodeLines());
    }
}
