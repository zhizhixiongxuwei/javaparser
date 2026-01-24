package com.example.javaparser;

public final class DiffRow {
    private final String left;
    private final String right;
    private final DiffType leftType;
    private final DiffType rightType;
    private final int leftLineNumber;
    private final int rightLineNumber;
    private final java.util.List<DiffSegment> leftSegments;
    private final java.util.List<DiffSegment> rightSegments;

    public DiffRow(
        String left,
        String right,
        DiffType leftType,
        DiffType rightType,
        int leftLineNumber,
        int rightLineNumber,
        java.util.List<DiffSegment> leftSegments,
        java.util.List<DiffSegment> rightSegments
    ) {
        this.left = left;
        this.right = right;
        this.leftType = leftType;
        this.rightType = rightType;
        this.leftLineNumber = leftLineNumber;
        this.rightLineNumber = rightLineNumber;
        this.leftSegments = java.util.List.copyOf(leftSegments);
        this.rightSegments = java.util.List.copyOf(rightSegments);
    }

    public String getLeft() {
        return left;
    }

    public String getRight() {
        return right;
    }

    public DiffType getLeftType() {
        return leftType;
    }

    public DiffType getRightType() {
        return rightType;
    }

    public int getLeftLineNumber() {
        return leftLineNumber;
    }

    public int getRightLineNumber() {
        return rightLineNumber;
    }

    public java.util.List<DiffSegment> getLeftSegments() {
        return leftSegments;
    }

    public java.util.List<DiffSegment> getRightSegments() {
        return rightSegments;
    }
}
