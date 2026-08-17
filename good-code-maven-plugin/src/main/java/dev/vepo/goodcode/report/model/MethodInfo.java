package dev.vepo.goodcode.report.model;

import java.util.List;

/**
 * A single method (or constructor) declared in a class/interface/enum/record.
 */
public record MethodInfo(String name,
                         int parameterCount,
                         boolean isStatic,
                         boolean isAbstract,
                         int cyclomaticComplexity,
                         List<ClassUsage> dependsOn) {
}
