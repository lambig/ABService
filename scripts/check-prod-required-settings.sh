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
  status=0
  log="$("${boot[@]}" "$name=" docker compose run --rm --no-deps backend 2>&1)" || status=$?

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
  fi
done <<<"$settings"

if [ "$failed" -ne 0 ]; then
  exit "$failed"
fi

echo "Every required prod setting stops the container from starting when it is missing."
