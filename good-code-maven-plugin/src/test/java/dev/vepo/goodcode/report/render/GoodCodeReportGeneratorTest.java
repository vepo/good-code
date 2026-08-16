package dev.vepo.goodcode.report.render;

import dev.vepo.goodcode.report.model.ClassInfo;
import dev.vepo.goodcode.report.model.FieldInfo;
import dev.vepo.goodcode.report.model.MethodInfo;
import dev.vepo.goodcode.report.model.PackageInfo;
import dev.vepo.goodcode.report.model.ProjectStats;
import dev.vepo.goodcode.report.model.SourceFileInfo;
import dev.vepo.goodcode.report.model.TypeKind;
import org.apache.maven.doxia.sink.Sink;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodCodeReportGeneratorTest {

    /**
     * The Sink interface has dozens of structural (void) methods; a
     * java.lang.reflect.Proxy that only records the text passed to
     * {@code text(String)} is far cheaper to maintain than a hand-written
     * fake that implements every method.
     */
    private static final class RecordingSink implements InvocationHandler {
        private final List<String> texts = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if ("text".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof String s) {
                texts.add(s);
            }
            return null;
        }
    }

    private Sink newRecordingSink(RecordingSink handler) {
        return (Sink) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Sink.class}, handler);
    }

    @Test
    void rendersTitleSummaryAndTypeDetails() {
        PackageInfo pkg = new PackageInfo("sample");
        SourceFileInfo file = new SourceFileInfo("Foo.java", "sample", 10, 1, 1);
        ClassInfo type = new ClassInfo("Foo", "sample.Foo", TypeKind.CLASS, false, false, 8);
        type.addField(new FieldInfo("x", "int", false, false));
        type.addMethod(new MethodInfo("doIt", 0, false, false, Collections.emptyList()));
        file.addType(type);
        pkg.addFile(file);

        ProjectStats stats = new ProjectStats();
        stats.addPackage(pkg);

        RecordingSink handler = new RecordingSink();
        Sink sink = newRecordingSink(handler);

        new GoodCodeReportGenerator(sink, Locale.ENGLISH, "My Report", "My Description").generate(stats);

        List<String> texts = handler.texts;
        assertTrue(texts.contains("My Report"));
        assertTrue(texts.contains("My Description"));
        assertTrue(texts.contains("Summary"));
        assertTrue(texts.contains("Types by kind"));
        assertTrue(texts.contains("Packages"));
        assertTrue(texts.contains("Types"));

        // summary values
        assertTrue(texts.contains("1")); // packages / files / types / fields / methods all equal 1 here
        assertTrue(texts.contains("8")); // lines of code

        // package + type rows
        assertTrue(texts.contains("sample"));
        assertTrue(texts.contains("Foo"));
        assertTrue(texts.contains("Class"));
        assertTrue(texts.contains("no")); // nested/abstract columns
    }
}
