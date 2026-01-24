package com.example.javaparser;

import java.nio.file.Path;

public final class OutputFilePreview {
    private final Path relativePath;
    private final String content;

    public OutputFilePreview(Path relativePath, String content) {
        this.relativePath = relativePath;
        this.content = content;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public String getContent() {
        return content;
    }
}
