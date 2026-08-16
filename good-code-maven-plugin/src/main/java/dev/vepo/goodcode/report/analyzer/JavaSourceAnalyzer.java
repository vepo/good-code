package dev.vepo.goodcode.report.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.vepo.goodcode.report.model.*;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;

/**
 * Walks a Java source tree and turns every {@code .java} file into a
 * {@link SourceFileInfo}, grouping the results by package into a {@link ProjectStats}.
 */
public class JavaSourceAnalyzer {

    private final Log log;
    private final Charset sourceEncoding;

    public JavaSourceAnalyzer(Log log, Charset sourceEncoding) {
        this.log = log;
        this.sourceEncoding = sourceEncoding;
    }

    public ProjectStats analyze(Path sourceRoot) throws IOException {
        // Set up type solver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot.toFile())); // for your project sources

        ParserConfiguration config = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        StaticJavaParser.setConfiguration(config);

        Map<String, PackageInfo> packagesByName = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            var javaFiles = paths.filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();

            javaFiles.forEach(file -> {
                try {
                    SourceFileInfo fileInfo = analyzeFile(sourceRoot, file);
                    packagesByName.computeIfAbsent(fileInfo.getPackageName(), PackageInfo::new)
                            .addFile(fileInfo);
                } catch (Exception e) {
                    log.warn("good-code: could not analyse " + file + " (" + e.getMessage() + "), skipping it");
                }
            });
        }

        ProjectStats stats = new ProjectStats();
        packagesByName.values().stream()
                .sorted(Comparator.comparing(PackageInfo::getName))
                .forEach(stats::addPackage);
        return stats;
    }

    private SourceFileInfo analyzeFile(Path sourceRoot, Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, sourceEncoding);
        LineCounter.Result lineStats = LineCounter.count(lines);

        CompilationUnit unit = StaticJavaParser.parse(file);
        String packageName = unit.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");

        String relativePath = sourceRoot.relativize(file).toString().replace('\\', '/');
        SourceFileInfo fileInfo = new SourceFileInfo(relativePath, packageName, lineStats.getTotalLines(), lineStats.getBlankLines(), lineStats.getCommentLines());

        unit.findAll(TypeDeclaration.class).forEach(type -> fileInfo.addType(toClassInfo(type)));
        return fileInfo;
    }

    private ClassInfo toClassInfo(TypeDeclaration<?> type) {
        var classInfo = ClassInfo.builder()
                .simpleName(type.getNameAsString())
                .qualifiedName(type.getFullyQualifiedName().orElseGet(type::getNameAsString))
                .kind(kindOf(type))
                .isAbstract(type.getModifiers()
                        .stream()
                        .anyMatch(m -> m.getKeyword() == Modifier.Keyword.ABSTRACT))
                .nested(!type.isTopLevelType())
                .linesOfCode(type.getRange()
                        .map(Range::getLineCount)
                        .orElse(0))
                .build();


        if (type instanceof RecordDeclaration record) {
            // record components are implicit private final fields, but JavaParser
            // models them as parameters rather than FieldDeclaration members
            record.getParameters().forEach(component -> classInfo.addField(
                    new FieldInfo(component.getNameAsString(), component.getTypeAsString(), false, true)));
        }

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof FieldDeclaration field) {
                for (VariableDeclarator variable : field.getVariables()) {
                    classInfo.addField(new FieldInfo(
                            variable.getNameAsString(),
                            variable.getTypeAsString(),
                            field.isStatic(),
                            field.isFinal()));
                }
            } else if (member instanceof MethodDeclaration method) {
                classInfo.addMethod(new MethodInfo(method.getNameAsString(),
                        method.getParameters().size(),
                        method.isStatic(),
                        method.isAbstract(),
                        method.getBody().map(JavaSourceAnalyzer::extractUsage).orElseGet(Collections::emptyList)
                ));
            } else if (member instanceof ConstructorDeclaration constructor) {
                classInfo.addConstructor(new ConstructorInfo(constructor.getParameters().size()));
            }
        }

        return classInfo;
    }

    private static String extractUsage(Node node) {
        try {
            if (node instanceof MethodCallExpr mce) {
                return mce.resolve().declaringType().getQualifiedName();
            } else if (node instanceof FieldAccessExpr fae) {
                return fae.resolve().asField().declaringType().getQualifiedName();
            } else {
                return null;
            }
        } catch (Exception e) {
            // If resolution fails, skip this invocation
            return null;
        }
    }

    private static List<ClassUsage> extractUsage(BlockStmt block) {
        return block.getStatements()
                .stream()
                .flatMap(JavaSourceAnalyzer::expand)
                .map(JavaSourceAnalyzer::extractUsage)
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(Function.identity(), Collectors.counting()),
                        map -> map.entrySet().stream()
                                .map(entry -> new ClassUsage(entry.getKey(), entry.getValue().intValue()))
                                .collect(Collectors.toList())
                ));
    }

    private static Stream<Node> expand(Statement stmt) {
        if (stmt.isIfStmt()) {
            return expand(stmt.asIfStmt());
        } else if (stmt.isBlockStmt()) {
            return stmt.asBlockStmt()
                    .getStatements()
                    .stream()
                    .flatMap(JavaSourceAnalyzer::expand);
        } else if (stmt.isExpressionStmt()) {
            return expand(stmt.asExpressionStmt()
                    .getExpression());
        }
        return Stream.of(stmt);
    }

    private static Stream<Node> expand(Expression expressionStmt) {
        if (expressionStmt.isBinaryExpr()) {
            return Stream.concat(expand(expressionStmt.asBinaryExpr().getLeft()),
                    expand(expressionStmt.asBinaryExpr().getRight()));
        } else if (expressionStmt.isMethodCallExpr()) {
            return Stream.concat(Stream.of(expressionStmt.asMethodCallExpr().asMethodCallExpr()),
                    expressionStmt.asMethodCallExpr()
                            .asMethodCallExpr()
                            .getArguments().stream()
                            .flatMap(JavaSourceAnalyzer::expand));
        } else if (expressionStmt.isFieldAccessExpr()) {
            return Stream.of(expressionStmt);
        }
        return Stream.empty();
    }

    private static Stream<Node> expand(IfStmt stmt) {
        var conditionStmt = stmt.getCondition();
        var elseStmt = stmt.getElseStmt();
        if (elseStmt.isPresent() && elseStmt.get().isIfStmt()) {
            return concat(concat(expand(conditionStmt), expand(stmt.getThenStmt())),
                    expand(elseStmt.get().asIfStmt()));
        } else {
            return elseStmt.map(elseStmtBody -> concat(concat(expand(conditionStmt), expand(stmt.getThenStmt())),
                            expand(elseStmtBody)))
                    .orElseGet(() -> concat(expand(conditionStmt), expand(stmt.getThenStmt())));
        }
    }

    private TypeKind kindOf(TypeDeclaration<?> type) {
        return switch (type) {
            case ClassOrInterfaceDeclaration cid when cid.isInterface() -> TypeKind.INTERFACE;
            case EnumDeclaration _ -> TypeKind.ENUM;
            case RecordDeclaration _ -> TypeKind.RECORD;
            case AnnotationDeclaration _ -> TypeKind.ANNOTATION;
            default -> TypeKind.CLASS;
        };
    }
}
