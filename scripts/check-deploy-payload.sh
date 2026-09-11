#!/usr/bin/env bash
# デプロイのたびに SSM Run Command が実機へ配るファイルを検査する。
#
# この2つはリポジトリの中でもコンテナの中でも動かないため、壊れていても他の検査には現れず、
# イメージを差し替えている最中の実機で初めて分かる。構文と、SSM のコマンドが一度に運べる
# 大きさに収まることを見る。
#
# 対象は .github/workflows/deploy.yml が base64 にして埋め込むファイルから求める。ここへ写すと、
# 配るファイルが増えたときに一覧だけが古くなる。
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow=".github/workflows/deploy.yml"

# SSM Run Command のパラメータは合計 100KB まで。base64 で約4/3へ膨らむ分と、コマンド本体の分を
# 見込んで、運ぶ中身は 60KB までに収める。超えるならオブジェクトストア経由へ変える合図
readonly MAX_BYTES=61440

deployed="$(sed -nE 's/.*base64 -w0 ([^)")]+).*/\1/p' "$root/$workflow" | sort -u)"

if [ -z "$deployed" ]; then
  echo "No file is sent to the host from $workflow." >&2
  echo "Expected 'base64 -w0 <path>' in the SSM command that deploys." >&2
  exit 1
fi

status=0

while read -r file; do
  if [ ! -f "$root/$file" ]; then
    echo "$file does not exist, but $workflow sends it to the host." >&2
    status=1
    continue
  fi

  bytes=$(($(wc -c <"$root/$file")))

  if [ "$bytes" -gt "$MAX_BYTES" ]; then
    echo "$file is $bytes bytes, over the $MAX_BYTES the deploy command can carry." >&2
    status=1
    continue
  fi

  # 実機で走るのは bash。構文が壊れていると、差し替えの途中で止まる
  case "$file" in
  *.sh) bash -n "$root/$file" ;;
  esac

  echo "$file: $bytes bytes"
done <<<"$deployed"

exit "$status"
