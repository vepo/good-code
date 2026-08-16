package dev.vepo.goodcode.report.analyzer;

import java.util.List;

/**
 * Very small heuristic line counter, in the spirit of tools like {@code cloc}: it
 * classifies every physical line of a source file as blank, comment or code
 * without doing full lexical analysis. Edge cases such as a block comment that
 * starts and ends on a line that also contains code are approximated (the whole
 * line is counted as a comment line) - good enough for a first version of the
 * report.
 */
public final class LineCounter {

    public static final class Result {
        private final int totalLines;
        private final int blankLines;
        private final int commentLines;

        public Result(int totalLines, int blankLines, int commentLines) {
            this.totalLines = totalLines;
            this.blankLines = blankLines;
            this.commentLines = commentLines;
        }

        public int getTotalLines() {
            return totalLines;
        }

        public int getBlankLines() {
            return blankLines;
        }

        public int getCommentLines() {
            return commentLines;
        }
    }

    private LineCounter() {
    }

    public static Result count(List<String> lines) {
        int blank = 0;
        int comment = 0;
        boolean inBlockComment = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.isEmpty()) {
                blank++;
                continue;
            }

            if (inBlockComment) {
                comment++;
                if (line.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (line.startsWith("//")) {
                comment++;
                continue;
            }

            if (line.startsWith("/*")) {
                comment++;
                inBlockComment = line.indexOf("*/", 2) == -1;
                continue;
            }

            // anything else counts as a line of code
        }

        return new Result(lines.size(), blank, comment);
    }
}
