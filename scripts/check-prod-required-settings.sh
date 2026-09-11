#!/usr/bin/env bash
# 本番で必須の設定が、1つでも欠けたら起動しないことを検査する。
#
# prod は設定が無いときに弱い既定値へ倒れず起動を失敗させる（DECISIONS 34）。すべてを欠いた状態で
# 見ると、ある1つが既定値を持つ側へ戻っても残りの欠落で失敗し続けるため、戻ったことに気付けない。
# 他の値を揃えたうえで1つずつ欠き、欠いた設定の名前が失敗として現れるところまで確かめる。
#
# 前提: compose の prod スタック（COMPOSE_FILE / BACKEND_IMAGE）と、起動済みの PostgreSQL。
# 設定が揃っていれば起動が進むことは、この検査の後で prod スタックを起動して確かめる。
set -euo pipefail

: "${COMPOSE_FILE:?point COMPOSE_FILE at the prod stack (see container-check in .github/workflows/ci.yml)}"
: "${BACKEND_IMAGE:?set BACKEND_IMAGE to the image built from Dockerfile.jvm}"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 欠けたことが設定名として失敗に現れる設定に限る。DB 系は接続URLの式の材料で、欠けても式は
# 組み上がり、接続先が既定値や壊れた値へ変わるだけで、どれが欠けたのかは現れない（#330）。
# #330 が直れば、この絞り込みは要らなくなる
readonly SURFACED_BY_NAME=(
  ADMIN_API_KEY
  ASSETS_BUCKET
)

# 設定が欠けた起動は数秒で終わる。倒れる側へ戻っていると起動し続けてしまうため、待ち切らずに打ち切る。
# timeout は GNU coreutils のもので、手元の macOS には無いことがある（その場合は打ち切らない）
boot=(env)
if command -v timeout >/dev/null 2>&1; then
  boot=(timeout 60 env)
fi

# プロセス置換で読むと、一覧の側が落ちても検査が素通りする（終了コードが伝わらない）
settings="$("$root/scripts/prod-required-settings.sh")"

failed=0

while read -r name key; do
  if ! printf '%s\n' "${SURFACED_BY_NAME[@]}" | grep -qxF "$name"; then
    echo "$name skipped: its absence does not surface as $key (#330)"
    continue
  fi

  status=0
  # 標準入力を渡さない。渡すと docker が一覧の残りを読み尽くし、最初の1件だけ検査して緑になる
  log="$("${boot[@]}" "$name=" docker compose run --rm --no-deps backend </dev/null 2>&1)" || status=$?

  if [ "$status" -eq 124 ]; then
    echo "The container kept running without $name. The setting no longer requires a value." >&2
    failed=1
  elif [ "$status" -eq 0 ]; then
    echo "The container exited successfully without $name." >&2
    failed=1
  elif ! printf '%s' "$log" | grep -qF "$key"; then
    echo "The container failed without $name, but the log does not name $key:" >&2
    printf '%s\n' "$log" >&2
    failed=1
  else
    # 何を試したかを1件ずつ残す。緑であることだけでは、一覧が空のまま素通りした場合と見分けが付かない
    echo "$name missing: the container failed and the log names $key"
  fi
done <<<"$settings"

if [ "$failed" -ne 0 ]; then
  exit "$failed"
fi

echo "Every checked prod setting stops the container from starting when it is missing."
