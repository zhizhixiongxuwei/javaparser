package com.example.javaparser;

import java.util.List;

/**
 * Configuration flags that control which transformation rules the processor applies.
 */
public final class ProcessorConfig {
    public enum PackageFilterMode {
        EXCLUDE,
        INCLUDE
    }

    private boolean classToPublic;
    private boolean fieldToPublic;
    private boolean splitFiles;
    private PackageFilterMode packageFilterMode;
    private List<String> excludedPackages;
    private List<String> includedPackages;

    public ProcessorConfig() {
        this.classToPublic = true;
        this.fieldToPublic = true;
        this.splitFiles = true;
        this.packageFilterMode = PackageFilterMode.EXCLUDE;
        this.excludedPackages = List.of();
        this.includedPackages = List.of();
    }

    public boolean isClassToPublic() {
        return classToPublic;
    }

    public void setClassToPublic(boolean classToPublic) {
        this.classToPublic = classToPublic;
    }

    public boolean isFieldToPublic() {
        return fieldToPublic;
    }

    public void setFieldToPublic(boolean fieldToPublic) {
        this.fieldToPublic = fieldToPublic;
    }

    public boolean isSplitFiles() {
        return splitFiles;
    }

    public void setSplitFiles(boolean splitFiles) {
        this.splitFiles = splitFiles;
    }

    public PackageFilterMode getPackageFilterMode() {
        return packageFilterMode;
    }

    public void setPackageFilterMode(PackageFilterMode packageFilterMode) {
        this.packageFilterMode = packageFilterMode == null ? PackageFilterMode.EXCLUDE : packageFilterMode;
    }

    public List<String> getExcludedPackages() {
        return excludedPackages;
    }

    public void setExcludedPackages(List<String> excludedPackages) {
        this.excludedPackages = normalizePackages(excludedPackages);
    }

    public List<String> getIncludedPackages() {
        return includedPackages;
    }

    public void setIncludedPackages(List<String> includedPackages) {
        this.includedPackages = normalizePackages(includedPackages);
    }

    private static List<String> normalizePackages(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(value -> value == null ? "" : value.trim())
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
    }
}
