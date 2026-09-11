#!/usr/bin/env bash
# 生成物を持つワークスペースが、いまも生成器を持っていることを検査する。
#
# 型の同期ゲート（api-types-check）はルートの `generate:api-types` を呼び、ルートの script は
# `--workspaces --if-present` で各ワークスペースの同名 script を回す。`--if-present` は script を
# 持たないワークスペースを黙って飛ばすため、あるワークスペースから `generate:api-types` が消えると、
# そのワークスペースの生成物は作り直されないまま、差分の検査を通る。
#
# 対象は追跡中の生成物の実体から求める。ワークスペースの名前をここへ書かないので、生成物を持つ
# ワークスペースが増えても検査の側が古くならない（ルートの `generate:api-types` と同じ扱い）。
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly GENERATED="*/src/lib/api/schema.d.ts"

generated="$(git -C "$root" ls-files "$GENERATED")"

if [ -z "$generated" ]; then
  echo "No tracked $GENERATED was found." >&2
  echo "Either the generated types moved, or they are no longer committed (DECISIONS 33)." >&2
  exit 1
fi

status=0

while read -r file; do
  workspace="${file%%/*}"
  manifest="$workspace/package.json"

  if [ ! -f "$root/$manifest" ]; then
    echo "$file is tracked but $manifest does not exist." >&2
    status=1
    continue
  fi

  if ! node -e '
    const { readFileSync } = require("node:fs")
    const pkg = JSON.parse(readFileSync(process.argv[1], "utf8"))
    process.exit(pkg.scripts?.["generate:api-types"] ? 0 : 1)
  ' "$root/$manifest"; then
    echo "$file is committed as a generated file, but $manifest has no generate:api-types script." >&2
    echo "Without it the workspace is skipped by --if-present and the stale types pass the diff check." >&2
    status=1
    continue
  fi

  echo "$workspace generates $file"
done <<<"$generated"

exit "$status"
