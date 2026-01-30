# javaparser-modifier

A JavaFX GUI that rewrites Java source files using JavaParser 3.28.0, styled with MaterialFX.

## Requirements

- JDK 17
- Maven

## Build

mvn -DskipTests package

## Run

mvn -DskipTests javafx:run

Tip: pass `--verbose` to enable debug logging, or toggle "Verbose logs" in the UI.

Note: `javafx.platform` is set to `win` in `pom.xml`. Change it for other OSes if needed.

The GUI only scans and lists changes until you click "Apply Selected" or "Apply All". It also shows a
line-numbered, character-highlighted original vs modified preview in the Diff tab.
Use the "Diff split" slider in the bottom toolbar to adjust left/right preview widths; the setting
is saved to `~/.javaparser-modifier/gui.properties`.
Use the "Theme" selector to switch between Light, High Contrast, and Warm styles (also persisted).

## Behavior

- Adds `public` to any top-level class that is not public.
- Changes `private`/`protected` fields in top-level classes to `public`.
- If a file contains only top-level classes and none are public, the file is split into multiple files,
  each containing one public class named after the class.
- If a file has a top-level public class, all other top-level classes are moved to new files in the same
  package (named after each class).
- Output is written to the output directory, preserving the input directory structure.
