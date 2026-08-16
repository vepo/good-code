package dev.vepo.goodcode.report.analyzer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineCounterTest {

    @Test
    void countsPlainCodeLinesAsCode() {
        LineCounter.Result result = LineCounter.count(List.of(
                "public class Foo {",
                "    int x = 1;",
                "}"));

        assertEquals(3, result.getTotalLines());
        assertEquals(0, result.getBlankLines());
        assertEquals(0, result.getCommentLines());
    }

    @Test
    void countsBlankAndWhitespaceOnlyLinesAsBlank() {
        LineCounter.Result result = LineCounter.count(List.of(
                "int x = 1;",
                "",
                "   ",
                "\t"));

        assertEquals(4, result.getTotalLines());
        assertEquals(3, result.getBlankLines());
        assertEquals(0, result.getCommentLines());
    }

    @Test
    void countsLineCommentsAsComment() {
        LineCounter.Result result = LineCounter.count(List.of(
                "// a leading comment",
                "int x = 1; // not detected as trailing comment"));

        assertEquals(2, result.getTotalLines());
        assertEquals(1, result.getCommentLines());
    }

    @Test
    void countsSingleLineBlockCommentAsOneCommentLine() {
        LineCounter.Result result = LineCounter.count(List.of(
                "/* a single line block comment */",
                "int x = 1;"));

        assertEquals(2, result.getTotalLines());
        assertEquals(1, result.getCommentLines());
        assertEquals(0, result.getBlankLines());
    }

    @Test
    void countsMultiLineBlockCommentAsCommentUntilItCloses() {
        LineCounter.Result result = LineCounter.count(List.of(
                "/**",
                " * Javadoc line one.",
                " * Javadoc line two.",
                " */",
                "public class Foo {"));

        assertEquals(5, result.getTotalLines());
        assertEquals(4, result.getCommentLines());
        assertEquals(0, result.getBlankLines());
    }

    @Test
    void treatsCodeFollowingABlockCommentCloseOnTheSameLineAsComment() {
        // documented heuristic limitation: the whole line is counted as a
        // comment line even though it also contains code after "*/"
        LineCounter.Result result = LineCounter.count(List.of(
                "/*",
                " */ int x = 1;"));

        assertEquals(2, result.getTotalLines());
        assertEquals(2, result.getCommentLines());
    }

    @Test
    void emptyFileHasNoLines() {
        LineCounter.Result result = LineCounter.count(List.of());

        assertEquals(0, result.getTotalLines());
        assertEquals(0, result.getBlankLines());
        assertEquals(0, result.getCommentLines());
    }
}
