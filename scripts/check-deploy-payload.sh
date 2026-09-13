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

# SSM Run Command のパラメータは合計 100KB まで。base64 へ変えた全ファイルと、それを書き出す
# コマンド本体が同じパラメータに載るため、上限との比較は1ファイルずつではなく合計で行う
# （1つずつ見る形では、個々が上限未満でも合計が超える組み合わせを緑にしてしまう）。
# コマンド本体と JSON の構造の分を見込んで 90KB で止める
readonly MAX_ENCODED_BYTES=92160

deployed="$(sed -nE 's/.*base64 -w0 ([^)")]+).*/\1/p' "$root/$workflow" | sort -u)"

if [ -z "$deployed" ]; then
  echo "No file is sent to the host from $workflow." >&2
  echo "Expected 'base64 -w0 <path>' in the SSM command that deploys." >&2
  exit 1
fi

status=0
encoded_total=0

while read -r file; do
  if [ ! -f "$root/$file" ]; then
    echo "$file does not exist, but $workflow sends it to the host." >&2
    status=1
    continue
  fi

  bytes=$(($(wc -c <"$root/$file")))
  # 手元（BSD）と CI（GNU）で折り返しの既定が違うため、改行を落としてから数える
  encoded=$(($(base64 <"$root/$file" | tr -d '\n' | wc -c)))
  encoded_total=$((encoded_total + encoded))

  # 実機で走るのは bash。構文が壊れていると、差し替えの途中で止まる
  case "$file" in
  *.sh) bash -n "$root/$file" ;;
  esac

  echo "$file: $bytes bytes ($encoded encoded)"
done <<<"$deployed"

if [ "$encoded_total" -gt "$MAX_ENCODED_BYTES" ]; then
  echo "The files add up to $encoded_total bytes once encoded, over the $MAX_ENCODED_BYTES the deploy command can carry." >&2
  echo "Send them through an object store instead of embedding them in the SSM command." >&2
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "The deploy command carries $encoded_total encoded bytes, within $MAX_ENCODED_BYTES."
fi

exit "$status"
