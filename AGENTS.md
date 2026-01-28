# Repository Guidelines

## Project Structure & Module Organization

- Single-module Maven project; build configuration lives in `pom.xml`.
- Source code is under `src/main/java/com/example/javaparser`.
  - Core processing logic: `JavaModifierProcessor`.
  - GUI entry point: `JavaModifierGuiApp` (JavaFX).
  - Diff/preview helpers: `Diff*` and `OutputFilePreview`.
- Build outputs are written to `target/` (do not commit).
- No `src/test/java` folder exists yet; add it if introducing tests.

## Build, Test, and Development Commands

- `mvn -DskipTests package` - builds the JAR.
- `mvn -DskipTests javafx:run` - launches the JavaFX GUI.
- `mvn -DskipTests javafx:run -- --verbose` - launches the GUI with debug logging.
- `mvn test` - runs tests (once test dependencies are added).

## Coding Style & Naming Conventions

- Java 17 source/target (`maven.compiler.release` is 17).
- 4-space indentation, no tabs.
- Package naming follows `com.example.javaparser`.
- Class names: `UpperCamelCase`; methods/fields: `lowerCamelCase`.
- Keep imports grouped (JDK, third-party, project) with blank lines between groups.
- No formatter or linter is configured; keep formatting consistent with adjacent code.

## Testing Guidelines

- Add JUnit 5 (or similar) to `pom.xml` when adding tests.
- Place tests under `src/test/java` and name classes with `*Test` (e.g., `JavaModifierProcessorTest`).
- Prefer fast, deterministic unit tests for parser behavior and file output planning.

## Commit & Pull Request Guidelines

- Git history uses short, descriptive subjects; keep messages imperative and concise.
- PRs should include a summary, commands run (e.g., `mvn -DskipTests package`), and screenshots for GUI changes.
- Link related issues if applicable.

## Configuration Notes

- `javafx.platform` is set to `win` in `pom.xml`; change it for other OS targets.
- The GUI prompts when output files already exist; choose overwrite or skip.
- GUI preferences (e.g., diff split ratio) are stored in `~/.javaparser-modifier/gui.properties`.
