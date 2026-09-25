#!/usr/bin/env bash
# Isolated regression cases for the shared commit identity hook.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
hook=${1:-"$script_dir/../.claude/hooks/require-local-git-identity.sh"}
fixture=$(mktemp -d "${TMPDIR:-/tmp}/abservice-identity.XXXXXX")
trap 'rm -rf -- "$fixture"' EXIT
export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL="$fixture/global-config"
git -C "$fixture" init -q
cd "$fixture"

expect_hook() {
  local title="$1" command="$2" expected="$3" result actual
  result=$(jq -nc --arg command "$command" '{tool_input:{command:$command}}' | bash "$hook")
  actual=allow
  if [ -n "$result" ]; then
    actual=$(printf '%s' "$result" | jq -r '.hookSpecificOutput.permissionDecision')
  fi
  if [ "$actual" != "$expected" ]; then
    printf 'FAIL %s: expected %s, got %s\n' "$title" "$expected" "$actual" >&2
    exit 1
  fi
  printf 'PASS %s\n' "$title"
}

expect_hook non-commit 'git status' allow
expect_hook missing-local-config 'git commit -m fixture' deny
git config --global user.name 'Global Fixture'
git config --global user.email global@example.test
git config --global abservice.publicEmail global@example.test
expect_hook global-only 'git commit -m fixture' deny
git config --local user.email public@example.test
expect_hook missing-local-expectation 'git commit -m fixture' deny
git config --local abservice.publicEmail other@example.test
expect_hook mismatch 'git commit -m fixture' deny
git config --local abservice.publicEmail public@example.test
expect_hook matching-email-global-name-only 'git commit -m fixture' deny
git config --local user.name ''
expect_hook matching-email-empty-local-name 'git commit -m fixture' deny
git config --local user.name 'Public Fixture'
expect_hook matching-local-identity 'git commit -m fixture' allow
git config --local abservice.publicEmail ''
expect_hook empty-expectation 'git commit -m fixture' deny
git config --local abservice.publicEmail public@example.test
git config --local user.email ''
expect_hook empty-local-email 'git commit -m fixture' deny

