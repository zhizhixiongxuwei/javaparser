package com.example.javaparser;

import java.util.List;

/**
 * Planned changes for a single top-level class.
 */
public final class ClassChangePlan {
    private final String className;
    private final boolean moveToNewFile;
    private final boolean addPublic;
    private final List<FieldChangePlan> fieldChanges;

    public ClassChangePlan(String className, boolean moveToNewFile, boolean addPublic, List<FieldChangePlan> fieldChanges) {
        this.className = className;
        this.moveToNewFile = moveToNewFile;
        this.addPublic = addPublic;
        this.fieldChanges = List.copyOf(fieldChanges);
    }

    public String getClassName() {
        return className;
    }

    public boolean isMoveToNewFile() {
        return moveToNewFile;
    }

    public boolean isAddPublic() {
        return addPublic;
    }

    public List<FieldChangePlan> getFieldChanges() {
        return fieldChanges;
    }

    /**
     * @return true if this class will be moved, have public added, or fields updated.
     */
    public boolean hasChanges() {
        return moveToNewFile || addPublic || !fieldChanges.isEmpty();
    }
}
