#!/usr/bin/env bash
# Enable the versioned, read-only pre-commit quality gate for this clone.
# git config also works from linked worktrees; no .git directory is assumed.
set -euo pipefail

PROJECT_ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
test -x "$PROJECT_ROOT/.githooks/pre-commit"
git -C "$PROJECT_ROOT" config --local core.hooksPath .githooks

echo "Enabled .githooks/pre-commit (Spotless / Checkstyle / PMD / unit+ArchUnit)."
echo "Use Java 25 and run backend/gradlew -p backend spotlessApply before staging Java edits."
