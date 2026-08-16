package dev.vepo.goodcode.report.model;

/**
 * A single field declared in a class/interface/enum/record.
 */
public class FieldInfo {

    private final String name;
    private final String type;
    private final boolean isStatic;
    private final boolean isFinal;

    public FieldInfo(String name, String type, boolean isStatic, boolean isFinal) {
        this.name = name;
        this.type = type;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }
}
