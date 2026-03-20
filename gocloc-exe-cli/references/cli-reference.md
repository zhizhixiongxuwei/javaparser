# gocloc CLI Reference

## Purpose

`gocloc` is an FSM-based code metrics CLI. It counts:
- `total`: all lines
- `code`: lines containing executable code
- `comment`: lines containing comments
- `blank`: empty lines

Unlike regex-only tools, one line can count as both `code` and `comment` (for example, inline comments).

## Binary Included In This Skill

- Path: `assets/bin/gocloc`
- Current artifact: `darwin/x86_64` (Mach-O)
- Check version:
  ```bash
  assets/bin/gocloc version
  ```

## Core Commands

### 1) Show version

```bash
assets/bin/gocloc version
```

### 2) List supported languages and extensions

```bash
assets/bin/gocloc language
```

### 3) Scan a path

```bash
assets/bin/gocloc scan <path> [--format table|json] [--output output.json] [--workers N]
```

Examples:
```bash
assets/bin/gocloc scan .
assets/bin/gocloc scan ./internal --workers 8
assets/bin/gocloc scan . --format json --output result.json
```

## Command Parameters

- `--format`
  - Allowed: `table` (default), `json`
  - `table` prints human-readable output.
  - `json` prints JSON and writes a JSON file.
- `--output`
  - JSON export file path.
  - Default: `output.json`
  - Effective when `--format json` is used.
- `--workers`
  - Number of concurrent scan workers.
  - Default: CPU core count.
  - Must be greater than `0`.

## Supported Languages

- Go: `.go`
- JavaScript: `.js`, `.mjs`, `.cjs`
- TypeScript: `.ts`, `.tsx`
- Python: `.py`
- Rust: `.rs`
- Ruby: `.rb`
- Java: `.java`
- C/C++: `.c`, `.cc`, `.cpp`, `.cxx`, `.h`, `.hh`, `.hpp`, `.hxx`
- SQL: `.sql`

## Design Rationale

1. FSM per language
- Each language has its own state machine analyzer.
- Avoid false positives when comment tokens appear inside strings.

2. Accurate dual-counting
- A single line may contribute to both `code` and `comment` metrics.
- Better reflects real source structure.

3. Concurrent scanning
- Directory files are distributed to worker goroutines.
- Improves throughput on large trees.

4. Streaming file read
- Files are analyzed line-by-line instead of loading fully into memory.
- Keeps memory usage stable for large files.

5. Registry-based language dispatch
- File extension maps to analyzer via a central registry.
- Easy to extend by adding analyzers and extension mappings.

## Typical Agent Workflow

1. Run `scan` on the user target.
2. If needed, rerun with `--format json --output <file>` for machine-readable artifacts.
3. Report totals, per-language breakdown, and any scan errors.
4. If extending language support, add analyzer + registry mapping + tests, then rerun `go test ./...`.
