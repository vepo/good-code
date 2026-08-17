package dev.vepo.goodcode.report.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single top-level or nested type declaration (class, interface, enum, record
 * or annotation) found while analysing a source file.
 */
public class ClassInfo {

    public static final class ClassInfoBuilder {
        private String simpleName;
        private String qualifiedName;
        private boolean isAbstract;
        private TypeKind kind;
        private boolean nested;
        private int linesOfCode;

        private ClassInfoBuilder() {
            this.simpleName = null;
            this.qualifiedName = null;
            this.isAbstract = false;
            this.kind = null;
            this.nested = false;
            this.linesOfCode = 0;
        }

        public ClassInfoBuilder kind(TypeKind kind) {
            this.kind = kind;
            return this;
        }

        public ClassInfoBuilder simpleName(String simpleName) {
            this.simpleName = simpleName;
            return this;
        }

        public ClassInfoBuilder qualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
            return this;
        }

        public ClassInfoBuilder isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return this;
        }

        public ClassInfoBuilder nested(boolean nested) {
            this.nested = nested;
            return this;
        }

        public ClassInfoBuilder linesOfCode(int linesOfCode) {
            this.linesOfCode = linesOfCode;
            return this;
        }

        public ClassInfo build() {
            return new ClassInfo(simpleName, qualifiedName, kind, nested, isAbstract, linesOfCode);
        }
    }

    public static ClassInfoBuilder builder() {
        return new ClassInfoBuilder();
    }

    private final String simpleName;
    private final String qualifiedName;
    private final TypeKind kind;
    private final boolean nested;
    private final boolean isAbstract;
    private final int linesOfCode;
    private final List<FieldInfo> fields = new ArrayList<>();
    private final List<MethodInfo> methods = new ArrayList<>();
    private final List<ConstructorInfo> constructors = new ArrayList<>();

    public ClassInfo(String simpleName, String qualifiedName, TypeKind kind, boolean nested,
                     boolean isAbstract, int linesOfCode) {
        this.simpleName = simpleName;
        this.qualifiedName = qualifiedName;
        this.kind = kind;
        this.nested = nested;
        this.isAbstract = isAbstract;
        this.linesOfCode = linesOfCode;
    }

    public void addField(FieldInfo field) {
        fields.add(field);
    }

    public void addMethod(MethodInfo method) {
        methods.add(method);
    }

    public void addConstructor(ConstructorInfo constructor) {
        this.constructors.add(constructor);
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public TypeKind getKind() {
        return kind;
    }

    public boolean isNested() {
        return nested;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public int getLinesOfCode() {
        return linesOfCode;
    }

    public List<FieldInfo> getFields() {
        return fields;
    }

    public List<MethodInfo> getMethods() {
        return methods;
    }

    public List<ConstructorInfo> getConstructors() {
        return constructors;
    }

    public int getFieldCount() {
        return fields.size();
    }

    public int getMethodCount() {
        return methods.size();
    }

    public int getConstructorCount() {
        return constructors.size();
    }

    public int getCyclomaticComplexity() {
        return methods.stream().mapToInt(MethodInfo::cyclomaticComplexity).sum();
    }

}
