package dev.vepo.goodcode.report.analyzer;

import dev.vepo.goodcode.report.model.*;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceAnalyzerTest {

    private final JavaSourceAnalyzer analyzer = new JavaSourceAnalyzer(new SystemStreamLog(), StandardCharsets.UTF_8);

    @Test
    void groupsFilesByPackage(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot, "pkgone/A.java", """
                package pkgone;
                public class A {
                }
                """);
        writeFile(sourceRoot, "pkgtwo/B.java", """
                package pkgtwo;
                public class B {
                }
                """);
        writeFile(sourceRoot, "Default.java", """
                public class Default {
                }
                """);

        ProjectStats stats = analyzer.analyze(sourceRoot);

        assertEquals(3, stats.getPackageCount());
        List<String> packageNames = stats.getPackages().stream().map(PackageInfo::getName).toList();
        assertEquals(List.of("(default package)", "pkgone", "pkgtwo"), packageNames);
    }

    @Test
    void classUsedStateTest(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot,
                "pkgA/A.java",
                """
                        package pkgA;
                        public class A {
                            private int field1;
                            public String value;
                            public A(int field1, String value) {
                               this.field1 = field1;
                               this.value = value;
                            }
                        
                            public int getField1() { return this.field1; }
                            public void setField1(int field1) { this.field1 = field1; }
                        }
                        """);
        writeFile(sourceRoot,
                "pkgB/B.java",
                """
                        package pkgB;
                        
                        import pkgA.A;
                        
                        public class B {
                            private boolean field1;
                            public B(boolean field1) {
                               this.field1 = field1;
                            }
                        
                            public boolean isField1() { return this.field1; }
                            public void setField1(boolean field1) { this.field1 = field1; }
                            public void checkA(A a) {
                                if (a.getField1() > 10) {
                                    System.out.println("A is high!");
                                } else if (a.getField1() < 0) {
                                    System.out.println("A is freeze!");
                                } else if (a.getField1() == 5 && field1) {
                                    System.out.println("A is halt! value = " + a.value);
                                } else {
                                    System.out.println("A is fine!");
                                }
                                System.out.println("A: field1=" + a.getField1());
                            }
                        }
                        """);
        var stats = analyzer.analyze(sourceRoot);
        assertThat(stats.getTypeCount()).isEqualTo(2);
        assertThat(stats.getMethodCount()).isEqualTo(5);
        assertThat(stats.findMethod("pkgB.B.checkA")).isPresent()
                .map(MethodInfo::dependsOn)
                .get()
                .isEqualTo(List.of(new ClassUsage("java.io.PrintStream", 5),
                        new ClassUsage("pkgA.A", 5)));
    }

    @Test
    void recognisesEveryTypeKind(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot, "sample/Kinds.java", """
                package sample;
                
                class SampleClass {
                }
                
                interface SampleInterface {
                }
                
                enum SampleEnum {
                    A, B
                }
                
                record SampleRecord(String name, int age) {
                }
                
                @interface SampleAnnotation {
                }
                """);

        ProjectStats stats = analyzer.analyze(sourceRoot);

        assertEquals(5, stats.getTypeCount());
        List<ClassInfo> types = stats.getPackages().get(0).getFiles().get(0).getTypes();
        assertEquals(TypeKind.CLASS, kindOf(types, "SampleClass"));
        assertEquals(TypeKind.INTERFACE, kindOf(types, "SampleInterface"));
        assertEquals(TypeKind.ENUM, kindOf(types, "SampleEnum"));
        assertEquals(TypeKind.RECORD, kindOf(types, "SampleRecord"));
        assertEquals(TypeKind.ANNOTATION, kindOf(types, "SampleAnnotation"));
    }

    @Test
    void recordComponentsAreReportedAsFinalFields(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot, "sample/Point.java", """
                package sample;
                public record Point(int x, int y) {
                }
                """);

        ProjectStats stats = analyzer.analyze(sourceRoot);

        ClassInfo point = stats.getPackages().get(0).getFiles().get(0).getTypes().get(0);
        assertEquals(2, point.getFieldCount());
        for (FieldInfo field : point.getFields()) {
            assertTrue(field.isFinal());
            assertFalse(field.isStatic());
        }
    }

    @Test
    void detectsNestedTypesFieldsAndMethods(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot, "sample/Outer.java", """
                package sample;
                
                public abstract class Outer {
                
                    private static final int COUNT = 1;
                    private String name;
                
                    public Outer(String name) {
                        this.name = name;
                    }
                
                    public abstract void doWork();
                
                    public void greet() {
                    }
                
                    public static class Inner {
                    }
                }
                """);

        ProjectStats stats = analyzer.analyze(sourceRoot);

        List<ClassInfo> types = stats.getPackages().get(0).getFiles().get(0).getTypes();
        ClassInfo outer = types.stream().filter(t -> t.getSimpleName().equals("Outer")).findFirst().orElseThrow();
        ClassInfo inner = types.stream().filter(t -> t.getSimpleName().equals("Inner")).findFirst().orElseThrow();

        assertFalse(outer.isNested());
        assertTrue(outer.isAbstract());
        assertTrue(inner.isNested());

        assertEquals(2, outer.getFieldCount());
        FieldInfo count = outer.getFields().stream().filter(f -> f.getName().equals("COUNT")).findFirst().orElseThrow();
        assertTrue(count.isStatic());
        assertTrue(count.isFinal());

        assertEquals(2, outer.getMethodCount());
        var constructor = outer.getConstructors().stream().findFirst().orElseThrow();
        assertEquals(1, constructor.parameterCount());

        MethodInfo doWork = outer.getMethods().stream().filter(m -> m.name().equals("doWork")).findFirst().orElseThrow();
        assertTrue(doWork.isAbstract());
    }

    @Test
    void skipsFilesThatFailToParseInsteadOfFailingTheBuild(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot, "sample/Good.java", """
                package sample;
                public class Good {
                }
                """);
        writeFile(sourceRoot, "sample/Broken.java", "this is not valid java at all {{{");

        ProjectStats stats = analyzer.analyze(sourceRoot);

        assertEquals(1, stats.getFileCount());
        SourceFileInfo file = stats.getPackages().get(0).getFiles().get(0);
        assertEquals("sample/Good.java", file.getFileName());
    }

    @Test
    void computesLineCountsUsingTheLineCounterHeuristic(@TempDir Path sourceRoot) throws IOException {
        writeFile(sourceRoot, "sample/Commented.java", """
                package sample;
                
                // a leading comment
                public class Commented {
                }
                """);

        ProjectStats stats = analyzer.analyze(sourceRoot);

        SourceFileInfo file = stats.getPackages().get(0).getFiles().get(0);
        assertEquals(5, file.getTotalLines());
        assertEquals(1, file.getBlankLines());
        assertEquals(1, file.getCommentLines());
        assertEquals(3, file.getCodeLines());
    }

    private static TypeKind kindOf(List<ClassInfo> types, String simpleName) {
        return types.stream()
                .filter(t -> t.getSimpleName().equals(simpleName))
                .map(ClassInfo::getKind)
                .findFirst()
                .orElseThrow();
    }

    private static void writeFile(Path root, String relativePath, String content) {
        try {
            var file = root.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException ioe) {
            Assertions.fail("Fail to create file!", ioe);
        }
    }
}
