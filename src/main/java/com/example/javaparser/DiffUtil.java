package com.example.javaparser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple diff utility that performs line-level LCS and optional character-level
 * highlighting for changed lines.
 */
public final class DiffUtil {
    private DiffUtil() {
    }

    /**
     * Compute a list of diff rows to render side-by-side.
     */
    public static List<DiffRow> diff(String leftText, String rightText) {
        List<String> leftLines = splitLines(leftText);
        List<String> rightLines = splitLines(rightText);

        int leftSize = leftLines.size();
        int rightSize = rightLines.size();

        // Build LCS table for line-level diff.
        int[][] lcs = new int[leftSize + 1][rightSize + 1];
        for (int i = leftSize - 1; i >= 0; i--) {
            for (int j = rightSize - 1; j >= 0; j--) {
                if (leftLines.get(i).equals(rightLines.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        // Walk the LCS table to produce a sequence of edit operations.
        List<DiffOp> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < leftSize && j < rightSize) {
            String left = leftLines.get(i);
            String right = rightLines.get(j);
            if (left.equals(right)) {
                ops.add(new DiffOp(DiffOpType.EQUAL, left));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                ops.add(new DiffOp(DiffOpType.DELETE, left));
                i++;
            } else {
                ops.add(new DiffOp(DiffOpType.INSERT, right));
                j++;
            }
        }
        while (i < leftSize) {
            ops.add(new DiffOp(DiffOpType.DELETE, leftLines.get(i++)));
        }
        while (j < rightSize) {
            ops.add(new DiffOp(DiffOpType.INSERT, rightLines.get(j++)));
        }

        // Convert edit ops into rows, pairing deletions with insertions as changes.
        List<DiffRow> rows = new ArrayList<>();
        LineCounter counter = new LineCounter();
        int index = 0;
        while (index < ops.size()) {
            DiffOp op = ops.get(index);
            if (op.type == DiffOpType.EQUAL) {
                rows.add(buildRow(op.value, op.value, DiffType.SAME, DiffType.SAME, counter));
                index++;
                continue;
            }

            if (op.type == DiffOpType.DELETE) {
                List<String> deletes = new ArrayList<>();
                while (index < ops.size() && ops.get(index).type == DiffOpType.DELETE) {
                    deletes.add(ops.get(index).value);
                    index++;
                }

                List<String> inserts = new ArrayList<>();
                int insertIndex = index;
                while (insertIndex < ops.size() && ops.get(insertIndex).type == DiffOpType.INSERT) {
                    inserts.add(ops.get(insertIndex).value);
                    insertIndex++;
                }

                int pairs = Math.min(deletes.size(), inserts.size());
                for (int p = 0; p < pairs; p++) {
                    rows.add(buildRow(deletes.get(p), inserts.get(p), DiffType.CHANGED, DiffType.CHANGED, counter));
                }
                for (int p = pairs; p < deletes.size(); p++) {
                    rows.add(buildRow(deletes.get(p), "", DiffType.REMOVED, DiffType.EMPTY, counter));
                }
                for (int p = pairs; p < inserts.size(); p++) {
                    rows.add(buildRow("", inserts.get(p), DiffType.EMPTY, DiffType.ADDED, counter));
                }

                index = insertIndex;
                continue;
            }

            if (op.type == DiffOpType.INSERT) {
                rows.add(buildRow("", op.value, DiffType.EMPTY, DiffType.ADDED, counter));
                index++;
            }
        }

        return rows;
    }

    /**
     * Build a single diff row with line numbers and optionally highlighted segments.
     */
    private static DiffRow buildRow(
        String left,
        String right,
        DiffType leftType,
        DiffType rightType,
        LineCounter counter
    ) {
        int leftLine = leftType == DiffType.EMPTY ? 0 : counter.nextLeft();
        int rightLine = rightType == DiffType.EMPTY ? 0 : counter.nextRight();

        DiffSegments segments;
        if (leftType == DiffType.CHANGED && rightType == DiffType.CHANGED) {
            segments = diffChars(left, right);
        } else {
            segments = new DiffSegments(
                List.of(new DiffSegment(left, false)),
                List.of(new DiffSegment(right, false))
            );
        }

        return new DiffRow(left, right, leftType, rightType, leftLine, rightLine, segments.left, segments.right);
    }

    /**
     * Compute character-level differences for a changed line.
     */
    private static DiffSegments diffChars(String left, String right) {
        char[] leftChars = left.toCharArray();
        char[] rightChars = right.toCharArray();
        int leftSize = leftChars.length;
        int rightSize = rightChars.length;

        // LCS for character-level diff.
        int[][] lcs = new int[leftSize + 1][rightSize + 1];
        for (int i = leftSize - 1; i >= 0; i--) {
            for (int j = rightSize - 1; j >= 0; j--) {
                if (leftChars[i] == rightChars[j]) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        SegmentBuilder leftBuilder = new SegmentBuilder();
        SegmentBuilder rightBuilder = new SegmentBuilder();

        int i = 0;
        int j = 0;
        while (i < leftSize && j < rightSize) {
            if (leftChars[i] == rightChars[j]) {
                leftBuilder.append(leftChars[i], false);
                rightBuilder.append(rightChars[j], false);
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                leftBuilder.append(leftChars[i], true);
                i++;
            } else {
                rightBuilder.append(rightChars[j], true);
                j++;
            }
        }
        while (i < leftSize) {
            leftBuilder.append(leftChars[i], true);
            i++;
        }
        while (j < rightSize) {
            rightBuilder.append(rightChars[j], true);
            j++;
        }

        return new DiffSegments(leftBuilder.build(), rightBuilder.build());
    }

    /**
     * Split text into lines, keeping trailing empty line if present.
     */
    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String[] lines = text.split("\\R", -1);
        return Arrays.asList(lines);
    }

    private enum DiffOpType {
        EQUAL,
        DELETE,
        INSERT
    }

    private static final class LineCounter {
        private int left = 1;
        private int right = 1;

        private int nextLeft() {
            return left++;
        }

        private int nextRight() {
            return right++;
        }
    }

    private static final class DiffSegments {
        private final List<DiffSegment> left;
        private final List<DiffSegment> right;

        private DiffSegments(List<DiffSegment> left, List<DiffSegment> right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class DiffOp {
        private final DiffOpType type;
        private final String value;

        private DiffOp(DiffOpType type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    /**
     * Accumulates characters into segments efficiently using StringBuilder,
     * avoiding O(n^2) string concatenation.
     */
    private static final class SegmentBuilder {
        private final List<DiffSegment> segments = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();
        private boolean currentHighlight;
        private boolean hasContent;

        private void append(char ch, boolean highlight) {
            if (hasContent && highlight != currentHighlight) {
                flush();
            }
            buffer.append(ch);
            currentHighlight = highlight;
            hasContent = true;
        }

        private void flush() {
            if (buffer.length() > 0) {
                segments.add(new DiffSegment(buffer.toString(), currentHighlight));
                buffer.setLength(0);
            }
        }

        private List<DiffSegment> build() {
            flush();
            return segments;
        }
    }
}
