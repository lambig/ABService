#!/usr/bin/env bash
# 本番で外から値を受け取る設定を、application.properties の宣言から列挙する。
#
# 必須とみなすのは既定値を持たない宣言（`${VAR}`）だけ。既定値を持つ宣言（`${VAR:...}`）は値が
# 届かなくても起動するため、運搬が切れていることがそのまま事故になるのは既定値なしの宣言に限られる。
#
# 出力は「環境変数名 設定キー」の行。宣言を正にして自動で拾うので、新しい必須設定を足したときも
# 検査の側へ名前を写す作業は要らない。
#
# ここが見るのは「いま何が必須か」であって「何が必須であり続けるべきか」ではない。宣言を弱める変更
# （`${VAR}` を `${VAR:...}` へ戻す、%prod の行を消す）は、この列挙からその設定が消えるだけなので、
# check-prod-settings-stay-required.sh が別に名指しで守る。
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties="backend/src/main/resources/application.properties"

settings="$(sed -nE 's/^%prod\.([^=]+)=\$\{([A-Za-z_][A-Za-z0-9_]*)\}[[:space:]]*$/\2 \1/p' "$root/$properties")"

if [ -z "$settings" ]; then
  echo "No required prod settings were found in $properties." >&2
  echo "Expected declarations of the form '%prod.<setting>=\${VAR}'." >&2
  exit 1
fi

printf '%s\n' "$settings"
