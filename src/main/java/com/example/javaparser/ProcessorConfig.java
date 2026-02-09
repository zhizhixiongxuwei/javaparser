package com.example.javaparser;

/**
 * Configuration flags that control which transformation rules the processor applies.
 */
public final class ProcessorConfig {
    private boolean classToPublic;
    private boolean fieldToPublic;
    private boolean splitFiles;

    public ProcessorConfig() {
        this.classToPublic = true;
        this.fieldToPublic = true;
        this.splitFiles = true;
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
}
