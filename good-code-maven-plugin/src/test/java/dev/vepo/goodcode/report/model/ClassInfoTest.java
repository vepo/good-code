package dev.vepo.goodcode.report.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassInfoTest {

    @Test
    void startsEmptyAndCountsAddedMembers() {
        ClassInfo classInfo = new ClassInfo("Foo", "sample.Foo", TypeKind.CLASS, false, false, 42);

        assertEquals("Foo", classInfo.getSimpleName());
        assertEquals("sample.Foo", classInfo.getQualifiedName());
        assertEquals(TypeKind.CLASS, classInfo.getKind());
        assertFalse(classInfo.isNested());
        assertFalse(classInfo.isAbstract());
        assertEquals(42, classInfo.getLinesOfCode());
        assertEquals(0, classInfo.getFieldCount());
        assertEquals(0, classInfo.getMethodCount());

        classInfo.addField(new FieldInfo("name", "String", false, true));
        classInfo.addField(new FieldInfo("count", "int", true, false));
        classInfo.addConstructor(new ConstructorInfo( 0 ));

        assertEquals(2, classInfo.getFieldCount());
        assertEquals(0, classInfo.getMethodCount());
        assertEquals(1, classInfo.getConstructorCount());
        assertEquals(2, classInfo.getFields().size());
    }
}
