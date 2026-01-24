package com.example.javaparser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DiffUtil {
    private DiffUtil() {
    }

    public static List<DiffRow> diff(String leftText, String rightText) {
        List<String> leftLines = splitLines(leftText);
        List<String> rightLines = splitLines(rightText);

        int leftSize = leftLines.size();
        int rightSize = rightLines.size();

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

    private static DiffSegments diffChars(String left, String right) {
        char[] leftChars = left.toCharArray();
        char[] rightChars = right.toCharArray();
        int leftSize = leftChars.length;
        int rightSize = rightChars.length;

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

        List<DiffSegment> leftSegments = new ArrayList<>();
        List<DiffSegment> rightSegments = new ArrayList<>();

        int i = 0;
        int j = 0;
        while (i < leftSize && j < rightSize) {
            if (leftChars[i] == rightChars[j]) {
                append(leftSegments, leftChars[i], false);
                append(rightSegments, rightChars[j], false);
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                append(leftSegments, leftChars[i], true);
                i++;
            } else {
                append(rightSegments, rightChars[j], true);
                j++;
            }
        }
        while (i < leftSize) {
            append(leftSegments, leftChars[i], true);
            i++;
        }
        while (j < rightSize) {
            append(rightSegments, rightChars[j], true);
            j++;
        }

        return new DiffSegments(leftSegments, rightSegments);
    }

    private static void append(List<DiffSegment> segments, char ch, boolean highlight) {
        if (segments.isEmpty()) {
            segments.add(new DiffSegment(String.valueOf(ch), highlight));
            return;
        }
        DiffSegment last = segments.get(segments.size() - 1);
        if (last.isHighlight() == highlight) {
            segments.set(segments.size() - 1, new DiffSegment(last.getText() + ch, highlight));
        } else {
            segments.add(new DiffSegment(String.valueOf(ch), highlight));
        }
    }

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
}
