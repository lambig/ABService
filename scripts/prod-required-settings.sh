#!/usr/bin/env bash
# 本番で外から値を受け取らなければならない設定を、application.properties の宣言と突き合わせて返す。
#
# 出力は「環境変数名 設定キー」の行。値の運搬（docker-compose.prod.yml と deploy.sh）と、
# 欠けたときに起動しないことの検査は、どちらもこの出力を対象にする。名前を写す側を作らないため、
# 必須設定の一覧はここだけが持つ。
#
# 一覧に挙げた設定が %prod で既定値なしに宣言されていなければ落とす。既定値を持たせる変更
# （`${VAR}` から `${VAR:...}` へ戻す、%prod の行を消す）は、本番が開発向けの弱い値
# ——既定のバケット名や開発用の API キー——で動くことを意味する。
set -euo pipefail

# 減らしてよいのは設定そのものを本番から無くしたときだけ。「既定値を持たせたから外す」は上のとおり本番の事故
readonly REQUIRED=(
  ADMIN_API_KEY
  ASSETS_BUCKET
  DB_HOST
  DB_PORT
  DB_NAME
  DB_USERNAME
  DB_PASSWORD
)

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties="backend/src/main/resources/application.properties"

status=0

for name in "${REQUIRED[@]}"; do
  found=0
  # 既定値なしの宣言だけを拾う。`${VAR:...}` は値が届かなくても起動するため、必須の宣言ではない
  pattern='^%prod\.[^=]+=\$\{'"$name"'\}[[:space:]]*$'

  while read -r line; do
    setting="${line%%=*}"
    printf '%s %s\n' "$name" "${setting#%prod.}"
    found=1
  done < <(grep -E "$pattern" "$root/$properties")

  if [ "$found" -eq 0 ]; then
    echo "$name must be required in production, but $properties has no '%prod.<setting>=\${$name}' declaration." >&2
    status=1
  fi
done

exit "$status"
