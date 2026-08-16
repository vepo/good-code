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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
        // This test remains as before, but we'll keep it for backward compatibility.
        // The new test below covers all scenarios.
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
                            public int sum(List<A> as) {
                              int total = 0;
                              for (A a: a) {
                                total += a.getField1();
                              }
                              return total;
                            }
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
        assertThat(stats.getTypeCount()).describedAs("Verify number of types").isEqualTo(2);
        assertThat(stats.getMethodCount()).describedAs("Verify number of methods").isEqualTo(6);
        assertThat(stats.findMethod("pkgB.B.checkA")).isPresent()
                .map(MethodInfo::dependsOn)
                .get()
                .isEqualTo(List.of(new ClassUsage("java.io.PrintStream", 5),
                        new ClassUsage("java.lang.System", 5),
                        new ClassUsage("pkgA.A", 5)));
        assertThat(stats.findMethod("pkgB.B.sum")).isPresent()
                .map(MethodInfo::dependsOn)
                .get()
                .isEqualTo(List.of(new ClassUsage("pkgA.A", 1)));
    }

    @Test
    void capturesAllClassUsages(@TempDir Path sourceRoot) throws IOException {
        // Write a complex source file that exercises every kind of class usage
        writeFile(sourceRoot, "test/Example.java", """
                package test;
                
                import java.util.*;
                import java.io.*;
                
                public class Example {
                    public void testUsages(List<String> list, Object obj, int[] arr, boolean flag) throws IOException {
                        // Method call
                        System.out.println("Hello");
                        // Field access
                        System.out.println("size: " + list.size());
                        // Object creation
                        List<String> newList = new ArrayList<>();
                        // Cast
                        String str = (String) obj;
                        // Instanceof with pattern
                        if (obj instanceof String s) {
                            System.out.println(s);
                        }
                        // Variable declaration
                        int num = 10;
                        // Class literal
                        Class<?> clazz = String.class;
                        // Method reference
                        list.forEach(System.out::println);
                        // Lambda expression
                        list.stream().map(s -> s.length()).forEach(System.out::println);
                        // For-each
                        for (String s : list) {
                            System.out.println(s);
                        }
                        // Try-with-resources
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                            br.readLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // Switch expression (Java 14+)
                        int result = switch (obj) {
                            case String s -> s.length();
                            case Integer i -> i;
                            default -> 0;
                        };
                        // Conditional expression
                        String cond = flag ? "true" : "false";
                        // Array creation
                        int[] newArr = new int[10];
                        // Array access
                        int first = arr[0];
                        // Unary
                        int neg = -num;
                        // Binary
                        int sum = num + first;
                        // Assignment
                        num = 20;
                        // For loop
                        for (int i = 0; i < 10; i++) {
                            System.out.println(i);
                        }
                        // While
                        while (num > 0) {
                            num--;
                        }
                        // Do-while
                        do {
                            num++;
                        } while (num < 5);
                        // Synchronized
                        synchronized (this) {
                            System.out.println("sync");
                        }
                        // Throw
                        if (flag) throw new IllegalArgumentException("bad");
                        // Assert
                        assert num > 0;
                        // Return
                        return;
                    }
                }
                """);

        ProjectStats stats = analyzer.analyze(sourceRoot);
        Optional<MethodInfo> method = stats.findMethod("test.Example.testUsages");
        assertTrue(method.isPresent());

        List<ClassUsage> deps = method.get().dependsOn();

        Map<String, Integer> expected = new HashMap<>();
        expected.put("java.io.PrintStream", 8);
        expected.put("java.util.Collection", 1);
        expected.put("java.lang.Iterable", 1);
        expected.put("java.util.stream.Stream", 2);
        expected.put("java.lang.Throwable", 1);
        expected.put("java.lang.String", 8);
        expected.put("java.util.ArrayList", 1);
        expected.put("java.io.InputStreamReader", 1);
        expected.put("java.lang.Class", 1);
        expected.put("java.lang.System", 7);
        expected.put("java.util.List", 2);
        expected.put("java.lang.Integer", 1);
        expected.put("java.io.BufferedReader", 3);
        expected.put("java.lang.IllegalArgumentException", 1);

        // Convert deps to map
        Map<String, Integer> actual = new HashMap<>();
        for (ClassUsage usage : deps) {
            actual.merge(usage.fullyQualifiedName(), usage.counter(), Integer::sum);
        }

        assertThat(actual).as("Dependencies do not match expected").isEqualTo(expected);
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
