#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SKILL_DIR}/../.." && pwd)"
OUT_BIN="${1:-${SKILL_DIR}/assets/bin/gocloc}"

mkdir -p "$(dirname "${OUT_BIN}")"

GOCACHE="${GOCACHE:-/tmp/gocache}" \
GOMODCACHE="${GOMODCACHE:-/tmp/gomodcache}" \
go build -o "${OUT_BIN}" "${REPO_ROOT}"

chmod +x "${OUT_BIN}"

echo "Built binary: ${OUT_BIN}"
echo "GOOS/GOARCH: $(go env GOOS)/$(go env GOARCH)"
