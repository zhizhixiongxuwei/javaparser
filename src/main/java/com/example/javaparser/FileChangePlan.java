package com.example.javaparser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileChangePlan {
    private final Path sourceFile;
    private final Path relativePath;
    private final SplitMode splitMode;
    private final String primaryPublicClass;
    private final List<ClassChangePlan> classPlans;

    public FileChangePlan(
        Path sourceFile,
        Path relativePath,
        SplitMode splitMode,
        String primaryPublicClass,
        List<ClassChangePlan> classPlans
    ) {
        this.sourceFile = sourceFile;
        this.relativePath = relativePath;
        this.splitMode = splitMode;
        this.primaryPublicClass = primaryPublicClass;
        this.classPlans = List.copyOf(classPlans);
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public SplitMode getSplitMode() {
        return splitMode;
    }

    public String getPrimaryPublicClass() {
        return primaryPublicClass;
    }

    public List<ClassChangePlan> getClassPlans() {
        return classPlans;
    }

    public boolean hasChanges() {
        return countPublicAdditions() > 0 || countFieldChanges() > 0 || countMovedClasses() > 0;
    }

    public int countPublicAdditions() {
        int count = 0;
        for (ClassChangePlan plan : classPlans) {
            if (plan.isAddPublic()) {
                count++;
            }
        }
        return count;
    }

    public int countFieldChanges() {
        int count = 0;
        for (ClassChangePlan plan : classPlans) {
            count += plan.getFieldChanges().size();
        }
        return count;
    }

    public int countMovedClasses() {
        int count = 0;
        for (ClassChangePlan plan : classPlans) {
            if (plan.isMoveToNewFile()) {
                count++;
            }
        }
        return count;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(relativePath.toString());
        List<String> parts = new ArrayList<>();
        int publicAdds = countPublicAdditions();
        int fieldChanges = countFieldChanges();
        int movedClasses = countMovedClasses();
        if (publicAdds > 0) {
            parts.add("public+" + publicAdds);
        }
        if (fieldChanges > 0) {
            parts.add("fields+" + fieldChanges);
        }
        if (movedClasses > 0) {
            parts.add("move+" + movedClasses);
        }
        if (!parts.isEmpty()) {
            sb.append(" (").append(String.join(", ", parts)).append(")");
        }
        return sb.toString();
    }

    public List<String> detailLines() {
        List<String> lines = new ArrayList<>();
        lines.add("File: " + relativePath);
        String targetDir = relativePath.getParent() == null ? "" : relativePath.getParent().toString() + java.io.File.separator;
        if (splitMode == SplitMode.SPLIT_ALL) {
            lines.add("Split mode: all top-level classes to separate files");
            lines.add("Original file will not be written to output");
        } else if (splitMode == SplitMode.SPLIT_OTHERS) {
            lines.add("Split mode: move non-primary classes to separate files");
            if (primaryPublicClass != null) {
                lines.add("Primary public class: " + primaryPublicClass);
            }
        }

        for (ClassChangePlan plan : classPlans) {
            if (plan.isMoveToNewFile()) {
                lines.add("Move class " + plan.getClassName() + " -> " + targetDir + plan.getClassName() + ".java");
            }
            if (plan.isAddPublic()) {
                lines.add("Add public to class " + plan.getClassName());
            }
            for (FieldChangePlan fieldChange : plan.getFieldChanges()) {
                lines.add("Field change in " + plan.getClassName() + ": " + fieldChange.describe());
            }
        }

        return lines;
    }
}
