#!/usr/bin/env bash
# 本番で必須の設定が、宣言から実際に値を運ぶ経路まで通っていることを検査する。
#
# 本番の値は Parameter Store から deploy.sh が取り、export したものを compose が環境として
# コンテナへ渡す。宣言（application.properties の %prod）だけがあっても、この経路のどちらかが
# 欠ければ値は届かない。検査する設定は宣言から自動で拾う（prod-required-settings.sh）。
#
# 検査は2方向。宣言した値を compose が渡すこと、compose が読む値を deploy.sh が export すること。
# この2つで、宣言から export までの連鎖が繋がっていることが言える。
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose="docker-compose.prod.yml"
deploy="infra/templates/deploy.sh.tpl"

required="$("$root/scripts/prod-required-settings.sh" | cut -d' ' -f1)"

# compose がコンテナへ渡す値。イメージの参照も同じ環境変数として渡るため区別しない
passed="$(grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*' "$root/$compose" | sed -E 's/^\$\{//' | sort -u)"

# compose を呼ぶ手前で deploy.sh が export する値
exported="$(sed -nE 's/^export ([A-Za-z_][A-Za-z0-9_]*)=.*/\1/p' "$root/$deploy" | sort -u)"

if [ -z "$passed" ]; then
  echo "No environment references were found in $compose." >&2
  exit 1
fi

if [ -z "$exported" ]; then
  echo "No exported variables were found in $deploy." >&2
  exit 1
fi

status=0

while read -r name; do
  if ! printf '%s\n' "$passed" | grep -qxF "$name"; then
    echo "$name is required by the prod profile but $compose does not pass it to the container." >&2
    status=1
  fi
done <<<"$required"

while read -r name; do
  if ! printf '%s\n' "$exported" | grep -qxF "$name"; then
    echo "$name is read by $compose but $deploy does not export it." >&2
    status=1
  fi
done <<<"$passed"

if [ "$status" -ne 0 ]; then
  echo "The prod configuration does not reach the container. Add the missing value to the path above." >&2
  exit "$status"
fi

echo "Every required prod setting is passed by $compose and exported by $deploy."
