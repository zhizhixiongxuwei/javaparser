package com.example.javaparser;

/**
 * A contiguous text segment in a diff row, optionally highlighted.
 */
public final class DiffSegment {
    private final String text;
    private final boolean highlight;

    public DiffSegment(String text, boolean highlight) {
        this.text = text;
        this.highlight = highlight;
    }

    public String getText() {
        return text;
    }

    public boolean isHighlight() {
        return highlight;
    }
}
