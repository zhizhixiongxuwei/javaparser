package com.example.javaparser;

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
