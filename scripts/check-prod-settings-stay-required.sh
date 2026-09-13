#!/usr/bin/env bash
# 本番で外から値を受け取り続けるべき設定が、いまも必須のまま宣言されていることを検査する。
#
# 必須設定の列挙（prod-required-settings.sh）は application.properties の宣言を正にしているため、
# 宣言を弱める変更——`${VAR}` を `${VAR:...}` へ戻す、宣言そのものを消す——は「必須が1つ減った」と
# しか見えず素通りする。本番が開発向けの弱い値（既定のバケット名、開発用の API キー、ローカルの
# データベース）で動くことを意味する設定は、ここで名指しして守る。
set -euo pipefail

# 減らしてよいのは、その設定を本番から無くしたときだけ。「既定値を持たせたから外す」は上のとおり本番の事故
readonly MUST_STAY_REQUIRED=(
  ADMIN_API_KEY
  ASSETS_BUCKET
  ORIGIN_VERIFY_TOKEN
  DB_HOST
  DB_PORT
  DB_NAME
  DB_USERNAME
  DB_PASSWORD
)

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties="backend/src/main/resources/application.properties"

required="$("$root/scripts/prod-required-settings.sh" | cut -d' ' -f1)"

status=0

for name in "${MUST_STAY_REQUIRED[@]}"; do
  if ! printf '%s\n' "$required" | grep -qxF "$name"; then
    echo "$name must stay required in production, but $properties has no required prod declaration for \${$name}." >&2
    status=1
  fi
done

if [ "$status" -ne 0 ]; then
  echo "A default value on those declarations lets production run on a development value." >&2
  exit "$status"
fi

echo "Every setting that must stay required is still declared without a default."
