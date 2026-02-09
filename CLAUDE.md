# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JavaParser-Modifier is a JavaFX GUI application that rewrites Java source files using JavaParser 3.28.0. It enforces consistent access modifiers (makes top-level classes public, promotes private/protected fields to public) and splits multi-class files into one-class-per-file.

## Build & Run Commands

```bash
mvn -DskipTests package              # Build JAR
mvn -DskipTests javafx:run           # Launch GUI
mvn -DskipTests javafx:run -- --verbose  # Launch with debug logging
mvn test                             # Run tests (no tests exist yet)
```

`javafx.platform` is set to `win` in `pom.xml` — change it for other OS targets.

## Tech Stack

- Java 17, Maven
- JavaParser 3.28.0 with symbol solver for AST parsing/transformation
- JavaFX 17.0.10 + MaterialFX 11.17.0 for UI
- SLF4J + Logback for logging

## Architecture

All source is in `src/main/java/com/example/javaparser/` (single package, single Maven module).

**Processing pipeline:**

1. **JavaModifierGuiApp** (860 lines) — JavaFX Application entry point. Handles UI layout, event binding, and background task execution. Main class: `com.example.javaparser.JavaModifierGuiApp`.
2. **JavaModifierProcessor** (404 lines) — Core logic. `analyze(inputDir)` scans `.java` files and returns `FileChangePlan` objects describing what to change. Configures JavaParser with Java 17 language level and a CombinedTypeSolver.
3. **Plan objects** (`FileChangePlan`, `ClassChangePlan`, `FieldChangePlan`, `OutputFilePreview`) — Immutable descriptions of planned transformations. `FileChangePlan` aggregates per-file changes.
4. **DiffUtil** — LCS-based diff engine producing `DiffRow` objects with character-level highlighting for the side-by-side preview.

**Key enums:** `SplitMode` (NONE, SPLIT_ALL, SPLIT_OTHERS), `OutputConflictStrategy` (OVERWRITE, SKIP_EXISTING), `DiffType`.

**File splitting logic:**
- SPLIT_ALL: file has only non-public top-level classes — each gets its own file
- SPLIT_OTHERS: file has one public class plus others — non-primary classes move to new files
- NONE: single class or mixed declarations

## Coding Conventions

- 4-space indentation, no tabs
- No formatter or linter configured; match adjacent code style
- Imports grouped: JDK, third-party, project (blank lines between groups)
- Tests go in `src/test/java/` with `*Test` naming (JUnit 5 preferred); no tests exist yet

## Runtime Configuration

GUI preferences (diff split ratio, theme) persist to `~/.javaparser-modifier/gui.properties` via `GuiPreferences`. Three CSS themes in `src/main/resources/styles/`: light, high-contrast, warm.
