---
name: gocloc-exe-cli
description: Use the bundled gocloc executable to scan a file or directory and report total/code/comment/blank line metrics with language-aware FSM parsing. Trigger this skill when asked to count LOC, compare code/comment density across languages, export JSON metrics, or produce reproducible code statistics from local source trees.
---

# gocloc-exe-cli

## Goal

Use the prebuilt `gocloc` binary in this skill to run deterministic scans without requiring a local build step. Produce concise metric summaries and include command evidence when needed.

Example invocation prompt:
`Use $gocloc-exe-cli at <skill-path> to scan ./src and summarize code/comment/blank ratios by language.`

## Quick Workflow

1. Verify the target path exists and is readable.
2. Run `assets/bin/gocloc scan <path>` with the needed flags.
3. If machine-readable output is needed, run JSON mode with `--format json --output <file>`.
4. Summarize totals and language breakdown, then call out scan errors if present.

## Commands

- `assets/bin/gocloc version`
- `assets/bin/gocloc language`
- `assets/bin/gocloc scan <path> [--format table|json] [--output output.json] [--workers N]`

## Resource Map

- `assets/bin/gocloc`: prebuilt executable included in this skill (current artifact: `darwin/x86_64`).
- `references/cli-reference.md`: full usage, flags, examples, supported languages, and design rationale.
- `scripts/rebuild_binary.sh`: rebuild and replace the bundled binary from repository source.

## Rebuild Binary

Run:
```bash
scripts/rebuild_binary.sh
```
Use `GOOS` and `GOARCH` to cross-compile when needed:
```bash
GOOS=linux GOARCH=amd64 scripts/rebuild_binary.sh
```
