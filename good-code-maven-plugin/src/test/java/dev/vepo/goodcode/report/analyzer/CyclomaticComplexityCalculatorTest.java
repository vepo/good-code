package dev.vepo.goodcode.report.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CyclomaticComplexityCalculatorTest {

    private final CyclomaticComplexityCalculator calculator = new CyclomaticComplexityCalculator();

    /**
     * Helper to parse a snippet as a BlockStmt.
     * The snippet is wrapped in a block: "{" + code + "}".
     */
    private BlockStmt parseBlock(String code) {
        return StaticJavaParser.parseBlock("{" + code + "}");
    }

    @Test
    void emptyBlock_shouldReturnOne() {
        BlockStmt block = parseBlock("");
        assertEquals(1, calculator.measure(block));
    }

    @Test
    void nullBlock_shouldReturnZero() {
        assertEquals(0, calculator.measure(null));
    }

    @Test
    void ifStatement_countedAsOneDecision() {
        BlockStmt block = parseBlock("if (a) {}");
        assertEquals(2, calculator.measure(block)); // 1 (entry) + 1 (if)
    }

    @Test
    @Disabled
    void ifWithAndOperator_countsAndOnce() {
        BlockStmt block = parseBlock("if (a && b) {}");
        // 1 (if) + 1 (&&) = 2 decisions → complexity = 3
        assertEquals(3, calculator.measure(block));
    }

    @Test
    @Disabled
    void ifWithOrOperator_countsOrOnce() {
        BlockStmt block = parseBlock("if (a || b) {}");
        assertEquals(3, calculator.measure(block));
    }

    @Test
    @Disabled
    void ifWithMultipleLogicalOperators_countsEach() {
        BlockStmt block = parseBlock("if (a && b || c) {}");
        // 1 (if) + 2 (&&, ||) = 3 decisions → complexity = 4
        assertEquals(4, calculator.measure(block));
    }

    @Test
    void nestedIfs_countBoth() {
        BlockStmt block = parseBlock("if (a) { if (b) {} }");
        // 2 ifs → 2 decisions → complexity = 3
        assertEquals(3, calculator.measure(block));
    }

    @Test
    void whileStatement_countedAsOneDecision() {
        BlockStmt block = parseBlock("while (a) {}");
        assertEquals(2, calculator.measure(block));
    }

    @Test
    @Disabled
    void whileWithLogicalCondition_countsOperators() {
        BlockStmt block = parseBlock("while (a && b) {}");
        assertEquals(3, calculator.measure(block));
    }

    @Test
    void doWhileStatement_countedAsOneDecision() {
        BlockStmt block = parseBlock("do {} while (a);");
        assertEquals(2, calculator.measure(block));
    }

    @Test
    void forStatement_countedAsOneDecision() {
        BlockStmt block = parseBlock("for (int i=0; i<10; i++) {}");
        // The compare part is a decision point, so +1 for 'for'
        assertEquals(2, calculator.measure(block));
    }

    @Test
    @Disabled
    void forWithLogicalCompare_countsOperators() {
        BlockStmt block = parseBlock("for (int i=0; i<10 && flag; i++) {}");
        // 1 (for) + 1 (&&) = 2 decisions → complexity = 3
        assertEquals(3, calculator.measure(block));
    }

    @Test
    void switchStatement_countsEachCase() {
        BlockStmt block = parseBlock("switch (x) { case 1: break; case 2: break; default: break; }");
        // 2 non‑default cases → 2 decisions → complexity = 3
        assertEquals(3, calculator.measure(block));
    }

    @Test
    void switchWithOnlyDefault_countsZero() {
        BlockStmt block = parseBlock("switch (x) { default: break; }");
        // 0 decisions → complexity = 1
        assertEquals(1, calculator.measure(block));
    }

    @Test
    void ternaryConditional_countsAsOneDecision() {
        BlockStmt block = parseBlock("int x = a ? 1 : 2;");
        assertEquals(2, calculator.measure(block));
    }

    @Test
    @Disabled
    void ternaryWithLogicalInCondition_countsOperators() {
        BlockStmt block = parseBlock("int x = (a && b) ? 1 : 2;");
        // 1 (ternary) + 1 (&&) = 2 decisions → complexity = 3
        assertEquals(3, calculator.measure(block));
    }

    @Test
    void catchClause_countsAsOneDecision() {
        BlockStmt block = parseBlock("try { } catch (Exception e) { }");
        // 1 catch block → 1 decision → complexity = 2
        assertEquals(2, calculator.measure(block));
    }

    @Test
    void multipleCatchBlocks_countEach() {
        BlockStmt block = parseBlock("try { } catch (IOException e) { } catch (Exception e) { }");
        // 2 catches → 2 decisions → complexity = 3
        assertEquals(3, calculator.measure(block));
    }

    @Test
    @Disabled
    void combinationOfSeveralConstructs() {
        String code = """
                if (a && b) {
                    while (c || d) {
                        do { } while (e);
                    }
                }
                switch (f) {
                    case 1: break;
                    case 2: break;
                }
                """;
        BlockStmt block = parseBlock(code);
        // Count decisions manually:
        // if (1) + && (1) + while (1) + || (1) + do-while (1) + 2 switch cases (2)
        // Total decisions = 1+1+1+1+1+2 = 7 → complexity = 8
        assertEquals(8, calculator.measure(block));
    }

    @Test
    void logicalOperatorsInExpressionsAreCountedRegardlessOfContext() {
        // && inside an assignment (not a condition) – should still be counted
        BlockStmt block = parseBlock("boolean x = a && b;");
        // 1 (&&) → complexity = 2
        assertEquals(2, calculator.measure(block));
    }

    @Test
    @Disabled
    void nestedLogicalOperators_countEach() {
        BlockStmt block = parseBlock("if (a && (b || c)) {}");
        // 1 (if) + 1 (&&) + 1 (||) = 3 decisions → complexity = 4
        assertEquals(4, calculator.measure(block));
    }
}