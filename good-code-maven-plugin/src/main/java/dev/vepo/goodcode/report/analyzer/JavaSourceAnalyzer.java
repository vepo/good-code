package dev.vepo.goodcode.report.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
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

    private static Stream<Node> expand(Statement stmt) {
        if (stmt.isBlockStmt()) {
            return stmt.asBlockStmt().getStatements().stream().flatMap(JavaSourceAnalyzer::expand);
        } else if (stmt.isIfStmt()) {
            return expand(stmt.asIfStmt());
        } else if (stmt.isForEachStmt()) {
            var forEach = stmt.asForEachStmt();
            return concat(expand(forEach.getIterable()), expand(forEach.getBody()));
        } else if (stmt.isForStmt()) {
            var forStmt = stmt.asForStmt();
            Stream<Node> init = forStmt.getInitialization().stream().flatMap(JavaSourceAnalyzer::expand);
            Stream<Node> compare = forStmt.getCompare().map(JavaSourceAnalyzer::expand).orElse(Stream.empty());
            Stream<Node> update = forStmt.getUpdate().stream().flatMap(JavaSourceAnalyzer::expand);
            Stream<Node> body = expand(forStmt.getBody());
            return concat(concat(concat(init, compare), update), body);
        } else if (stmt.isWhileStmt()) {
            var whileStmt = stmt.asWhileStmt();
            return concat(expand(whileStmt.getCondition()), expand(whileStmt.getBody()));
        } else if (stmt.isDoStmt()) {
            var doStmt = stmt.asDoStmt();
            return concat(expand(doStmt.getBody()), expand(doStmt.getCondition()));
        } else if (stmt.isSwitchStmt()) {
            var switchStmt = stmt.asSwitchStmt();
            return concat(expand(switchStmt.getSelector()),
                    switchStmt.getEntries().stream().flatMap(entry -> {
                        Stream<Node> labels = entry.getLabels().stream().flatMap(JavaSourceAnalyzer::expand);
                        Stream<Node> stmts = entry.getStatements().stream().flatMap(JavaSourceAnalyzer::expand);
                        return concat(labels, stmts);
                    }));
        } else if (stmt.isTryStmt()) {
            var tryStmt = stmt.asTryStmt();
            Stream<Node> resources = tryStmt.getResources().stream().flatMap(JavaSourceAnalyzer::expand);
            Stream<Node> body = expand(tryStmt.getTryBlock());
            Stream<Node> catches = tryStmt.getCatchClauses().stream()
                    .flatMap(cc -> concat(expand(cc.getParameter()), expand(cc.getBody())));
            Stream<Node> finallyBlock = tryStmt.getFinallyBlock().map(JavaSourceAnalyzer::expand).orElse(Stream.empty());
            return concat(concat(concat(resources, body), catches), finallyBlock);
        } else if (stmt.isSynchronizedStmt()) {
            var sync = stmt.asSynchronizedStmt();
            return concat(expand(sync.getExpression()), expand(sync.getBody()));
        } else if (stmt.isReturnStmt()) {
            return stmt.asReturnStmt().getExpression().map(JavaSourceAnalyzer::expand).orElse(Stream.empty());
        } else if (stmt.isThrowStmt()) {
            return expand(stmt.asThrowStmt().getExpression());
        } else if (stmt.isAssertStmt()) {
            var assertStmt = stmt.asAssertStmt();
            Stream<Node> check = expand(assertStmt.getCheck());
            Stream<Node> msg = assertStmt.getMessage().map(JavaSourceAnalyzer::expand).orElse(Stream.empty());
            return concat(check, msg);
        } else if (stmt.isLabeledStmt()) {
            return expand(stmt.asLabeledStmt().getStatement());
//        } else if (stmt.isVariableDeclarationStmt()) {
//            return stmt.asVariableDeclarationStmt().getVariables().stream().flatMap(JavaSourceAnalyzer::expand);
        } else if (stmt.isYieldStmt()) {
            return expand(stmt.asYieldStmt().getExpression());
        } else if (stmt.isExpressionStmt()) {
            return expand(stmt.asExpressionStmt().getExpression());
        } else {
            // fallback: traverse children generically (e.g. for empty, break, continue – nothing to do)
            return Stream.empty();
        }
    }

    private static Stream<Node> expand(Parameter parameter) {
        return Stream.empty();
    }

    private static Stream<Node> expand(Expression expr) {
        if (expr.isBinaryExpr()) {
            var bin = expr.asBinaryExpr();
            return concat(expand(bin.getLeft()), expand(bin.getRight()));
        } else if (expr.isMethodCallExpr()) {
            var call = expr.asMethodCallExpr();
            if (call.getScope().isPresent()) {
                return concat(expand(call.getScope().get()), concat(Stream.of(call),
                        call.getArguments().stream().flatMap(JavaSourceAnalyzer::expand)));
            } else {
                return concat(Stream.of(call),
                        call.getArguments().stream().flatMap(JavaSourceAnalyzer::expand));
            }
        } else if (expr.isFieldAccessExpr()) {
            return Stream.of(expr); // field access itself is a usage
        } else if (expr.isObjectCreationExpr()) {
            var newExpr = expr.asObjectCreationExpr();
            return concat(Stream.of(newExpr),
                    newExpr.getArguments().stream().flatMap(JavaSourceAnalyzer::expand));
        } else if (expr.isCastExpr()) {
            var cast = expr.asCastExpr();
            return concat(Stream.of(cast), expand(cast.getExpression()));
        } else if (expr.isInstanceOfExpr()) {
            var inst = expr.asInstanceOfExpr();
            return concat(Stream.of(inst), expand(inst.getExpression()));
        } else if (expr.isVariableDeclarationExpr()) {
            return expr.asVariableDeclarationExpr().getVariables().stream()
                    .flatMap(varDecl -> {
                        Stream<Node> typeNode = Stream.of(varDecl);
                        Stream<Node> init = varDecl.getInitializer()
                                .map(JavaSourceAnalyzer::expand)
                                .orElse(Stream.empty());
                        return concat(typeNode, init);
                    });
        } else if (expr.isAssignExpr()) {
            var assign = expr.asAssignExpr();
            return concat(expand(assign.getTarget()), expand(assign.getValue()));
        } else if (expr.isConditionalExpr()) {
            var cond = expr.asConditionalExpr();
            return concat(concat(expand(cond.getCondition()), expand(cond.getThenExpr())),
                    expand(cond.getElseExpr()));
        } else if (expr.isLambdaExpr()) {
            var lambda = expr.asLambdaExpr();
            return concat(lambda.getParameters().stream().flatMap(JavaSourceAnalyzer::expand), expand(lambda.getBody()));
        } else if (expr.isMethodReferenceExpr()) {
            var ref = expr.asMethodReferenceExpr();
            return concat(Stream.of(ref), expand(ref.getScope()));
        } else if (expr.isClassExpr()) {
            return Stream.of(expr);
        } else if (expr.isArrayCreationExpr()) {
            var arr = expr.asArrayCreationExpr();
            Stream<Node> type = Stream.of(arr.getElementType());
            Stream<Node> dims = arr.getLevels().stream()
                    .flatMap(level -> level.getDimension()
                            .map(JavaSourceAnalyzer::expand)
                            .orElse(Stream.empty()));
            Stream<Node> init = arr.getInitializer()
                    .stream()
                    .flatMap(initExpr -> initExpr.getValues().stream().flatMap(JavaSourceAnalyzer::expand));
            return concat(concat(type, dims), init);
        } else if (expr.isArrayAccessExpr()) {
            var access = expr.asArrayAccessExpr();
            return concat(expand(access.getName()), expand(access.getIndex()));
        } else if (expr.isUnaryExpr()) {
            return expand(expr.asUnaryExpr().getExpression());
        } else if (expr.isEnclosedExpr()) {
            return expand(expr.asEnclosedExpr().getInner());
        } else if (expr.isSwitchExpr()) {
            var switchExpr = expr.asSwitchExpr();
            Stream<Node> selector = expand(switchExpr.getSelector());
            Stream<Node> entries = switchExpr.getEntries().stream()
                    .flatMap(entry -> {
                        Stream<Node> labels = entry.getLabels().stream()
                                .flatMap(JavaSourceAnalyzer::expand);
                        Stream<Node> stmts = entry.getStatements().stream()
                                .flatMap(JavaSourceAnalyzer::expand);
                        return concat(labels, stmts);
                    });
            return concat(selector, entries);
        } else if (expr.isTypePatternExpr()) {
            var pattern = expr.asTypePatternExpr();
            // The pattern's type is the interesting part; the variable name is not a class usage.
            return concat(Stream.of(pattern), Stream.of(pattern.getType()));
        } else {
            // fallback: traverse children if any (though most common are covered)
            return Stream.empty();
//            return expr.getChildNodes().stream().flatMap(child -> {
//                if (child instanceof Expression) {
//                    return expand((Expression) child);
//                } else if (child instanceof Statement) {
//                    return expand((Statement) child);
//                } else {
//                    return Stream.empty();
//                }
//            });
        }
    }

    private static Stream<Node> expand(IfStmt stmt) {
        // keep original logic
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

    private static String extractUsage(Node node) {
        try {
            if (node instanceof MethodCallExpr mce) {
                return mce.resolve().declaringType().getQualifiedName();
            } else if (node instanceof FieldAccessExpr fae) {
                return fae.resolve().asField().declaringType().getQualifiedName();
            } else if (node instanceof ObjectCreationExpr oce) {
                return oce.resolve().declaringType().getQualifiedName();
            } else if (node instanceof CastExpr cast) {
                return cast.getType().resolve().asReferenceType().getTypeDeclaration().map(ResolvedTypeDeclaration::getQualifiedName).orElse(null);
            } else if (node instanceof InstanceOfExpr inst) {
                return inst.getType().resolve().asReferenceType().getTypeDeclaration().map(ResolvedTypeDeclaration::getQualifiedName).orElse(null);
            } else if (node instanceof VariableDeclarator varDecl) {
                return varDecl.getType().resolve().asReferenceType().getTypeDeclaration().map(ResolvedTypeDeclaration::getQualifiedName).orElse(null);
            } else if (node instanceof ClassExpr classExpr) {
                return classExpr.getType().resolve().asReferenceType().getTypeDeclaration().map(ResolvedTypeDeclaration::getQualifiedName).orElse(null);
            } else if (node instanceof MethodReferenceExpr methodRef) {
                // resolve the type of the method reference's scope
                return methodRef.getScope().calculateResolvedType().asReferenceType().getQualifiedName();
            } else if (node instanceof TypePatternExpr pattern) {
                return pattern.getType().resolve().asReferenceType().getTypeDeclaration().map(ResolvedTypeDeclaration::getQualifiedName).orElse(null);
            }
            return null;
        } catch (Exception e) {
            // resolution failed, skip this usage
            return null;
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