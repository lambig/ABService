#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
expected="$(git -C "$root" config --local --get abservice.publicEmail || true)"
email="$(git -C "$root" config --local --get user.email || true)"
name="$(git -C "$root" config --local --get user.name || true)"
if [[ ! "$expected" =~ [^[:space:]] || "$email" != "$expected" || ! "$name" =~ [^[:space:]] ]]; then
    echo 'Set the approved Git-local user.name and matching user.email and abservice.publicEmail before configuring JJ.' >&2
    exit 1
fi
if [[ "$(jj --version | cut -d' ' -f2 | cut -d- -f1)" != 0.47.1 ]]; then
    echo 'Workspace publication checks require JJ 0.47.1.' >&2
    exit 1
fi
if [[ "${1:-}" == --check-identity ]]; then exit 0; fi

common="$(git -C "$root" rev-parse --path-format=absolute --git-common-dir)"
hooks="$common/abservice-hooks"
installed="$(git -C "$root" config --local --get core.hooksPath || true)"
if [[ "$installed" != "$hooks" || ! -f "$hooks/jj-git.mjs" || ! -x "$hooks/jj-git.mjs" \
    || ! -f "$hooks/pre-push" || ! -x "$hooks/pre-push" || ! -f "$hooks/publication-check.mjs" ]]; then
    echo 'The shared Git publication hook or JJ wrapper is missing or not selected. Run scripts/setup-workspace-hooks.sh on the host.' >&2
    exit 1
fi

set_repo_string() {
    jj --repository "$root" config set --repo -- "$1" "$(node -p 'JSON.stringify(process.argv[1])' -- "$2")"
}
set_repo_string user.email "$email"
set_repo_string user.name "$name"
set_repo_string git.executable-path "$hooks/jj-git.mjs"
