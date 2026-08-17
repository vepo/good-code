package dev.vepo.goodcode.report.analyzer;

import com.github.javaparser.ast.stmt.BlockStmt;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Cyclomatic complexity calculator based on the number of decision points.
 * See <a href="https://dl.acm.org/doi/abs/10.1145/2557833.2557853">...</a>
 */
public class CyclomaticComplexityCalculator {

    /**
     * Measures the cyclomatic complexity of a given block.
     * Complexity = 1 + number of decision points found inside the block.
     *
     * @param block the block to analyse (may be null)
     * @return the cyclomatic complexity (≥ 1)
     */
    public int measure(BlockStmt block) {
        if (block == null) {
            return 0;
        }

        DecisionCounter visitor = new DecisionCounter();
        visitor.visit(block, null);
        // Cyclomatic complexity = 1 (for the single entry point) + number of decisions
        return 1 + visitor.getDecisionCount();
    }

    /**
     * Visitor that traverses the AST and counts all decision‑making constructs.
     * Counting rules:
     * <ul>
     *   <li>if, while, do, for statements → +1 each</li>
     *   <li>each switch case (except default) → +1</li>
     *   <li>ternary conditional ( ? : ) → +1</li>
     *   <li>logical AND/OR ( &amp;&amp; , || ) in any expression → +1 per operator</li>
     *   <li>each catch block → +1 (optional, but often counted)</li>
     * </ul>
     */
    private static class DecisionCounter extends VoidVisitorAdapter<Void> {
        private int decisions = 0;

        public int getDecisionCount() {
            return decisions;
        }

        @Override
        public void visit(IfStmt n, Void arg) {
            // The 'if' condition itself is a decision point
            decisions++;
            // Count logical operators inside the condition
            n.getCondition().accept(this, arg);
            // Continue traversing the 'then' and 'else' branches (the else branch
            // may contain another if, which will be counted in its own visit)
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            decisions++;
            n.getCondition().accept(this, arg);
            super.visit(n, arg);
        }

        @Override
        public void visit(DoStmt n, Void arg) {
            decisions++;
            n.getCondition().accept(this, arg);
            super.visit(n, arg);
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            decisions++;
            // The compare part of a for loop may contain logical operators
            n.getCompare().ifPresent(expr -> expr.accept(this, arg));
            super.visit(n, arg);
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            // Count each case label (excluding 'default') as a decision point
            int caseCount = (int) n.getEntries().stream()
                    .filter(entry -> !entry.isDefault())
                    .count();
            decisions += caseCount;
            // Also count logical operators inside the selector expression
            n.getSelector().accept(this, arg);
            super.visit(n, arg);
        }

        @Override
        public void visit(CatchClause n, Void arg) {
            // Each catch block represents an alternative path
            decisions++;
            super.visit(n, arg);
        }

        @Override
        public void visit(ConditionalExpr n, Void arg) {
            // Ternary operator ? : is a decision point
            decisions++;
            n.getCondition().accept(this, arg);
            // Also traverse the two branches (they may contain further decisions)
            super.visit(n, arg);
        }

        @Override
        public void visit(BinaryExpr n, Void arg) {
            // Logical AND / OR are decision points (short‑circuit)
            if (n.getOperator() == BinaryExpr.Operator.AND
                    || n.getOperator() == BinaryExpr.Operator.OR) {
                decisions++;
            }
            // Always traverse both sides to find nested operators
            super.visit(n, arg);
        }
    }
}