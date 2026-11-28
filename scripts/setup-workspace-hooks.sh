#!/usr/bin/env bash
set -euo pipefail
root="$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)"
cd "$root"
common="$(git -C "$root" rev-parse --path-format=absolute --git-common-dir)"
bash scripts/setup-workspace-jj.sh --check-identity
hooks="$common/abservice-hooks"
mkdir -p "$hooks"
cp "$root/scripts/publication-check.mjs" "$root/scripts/jj-git.mjs" "$hooks/"
printf '%s\n' '#!/usr/bin/env bash' 'set -euo pipefail' 'exec node "$(dirname "$0")/publication-check.mjs" check "$@"' > "$hooks/pre-push"
cp "$root/.githooks/pre-commit" "$hooks/pre-commit"
chmod +x "$hooks/pre-push" "$hooks/pre-commit" "$hooks/jj-git.mjs"
git -C "$root" config --local core.hooksPath "$hooks"
bash scripts/setup-workspace-jj.sh
printf 'Installed Git/JJ publication checks in %s\n' "$hooks"
