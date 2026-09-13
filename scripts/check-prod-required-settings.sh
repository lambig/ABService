#!/usr/bin/env bash
# 本番で必須の設定が、1つでも欠けたら起動しないことを検査する。
#
# 値が届かないことは compose が弾く（`docker-compose.prod.yml` の `:?`）。空文字がそのまま
# 接続URLの式へ埋まる設定があるため、欠落はコンテナを作る前に止める（DECISIONS 34、#330）。
# すべてを欠いた状態で見ると、ある1つが通る側へ戻っても残りの欠落で失敗し続けるため、戻ったことに
# 気付けない。他の値を揃えたうえで1つずつ欠き、失敗がその環境変数の名前を挙げるところまで確かめる。
#
# 前提: compose の prod スタック（COMPOSE_FILE / BACKEND_IMAGE）。
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
  # 標準入力を渡さない。渡すと docker が一覧の残りを読み尽くし、最初の1件だけ検査して緑になる
  log="$("${boot[@]}" "$name=" docker compose run --rm --no-deps backend </dev/null 2>&1)" || status=$?

  if [ "$status" -eq 124 ]; then
    echo "The container kept running without $name. The value is no longer required." >&2
    failed=1
  elif [ "$status" -eq 0 ]; then
    echo "The container exited successfully without $name." >&2
    failed=1
  elif ! printf '%s' "$log" | grep -qF "$name"; then
    echo "Starting without $name failed, but the failure does not name it (it feeds $key):" >&2
    printf '%s\n' "$log" >&2
    failed=1
  else
    # 何を試したかを1件ずつ残す。緑であることだけでは、一覧が空のまま素通りした場合と見分けが付かない
    echo "$name missing ($key): the stack refuses to start and names it"
  fi
done <<<"$settings"

if [ "$failed" -ne 0 ]; then
  exit "$failed"
fi

echo "Every required prod setting stops the container from starting when it is missing."
