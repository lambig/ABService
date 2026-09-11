#!/usr/bin/env bash
# デプロイが叩く AWS API に対して、それを実行する主体のロールが権限を持っているかを検査する。
#
# 権限の不足は実行して初めて分かり、しかもそれはデプロイの最中になる。呼び出す側（ワークフローと
# ホストのスクリプト）から必要なアクションを求め、対応するロールの定義に在ることを見る。
#
# CLI のコマンド名は API の名前と綴りが対応する（get-command-invocation → GetCommandInvocation）。
# 対応しないものだけを下の対応表に持つ。ロールの側は Terraform の定義を文字列として見るだけで、
# 条件やリソースの範囲までは見ない（それは terraform validate と実際の実行が受け持つ）。
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 「叩く側:その権限を持つべきロールを定義したファイル」
readonly CALLERS=(
  ".github/workflows/deploy.yml:infra/cicd.tf"
  "infra/host/deploy.sh:infra/compute.tf"
)

# CLI の綴りから API の名前が導けないもの
resolve_exception() {
  case "$1" in
  "ecr get-login-password") echo "ecr:GetAuthorizationToken" ;;
  *) echo "" ;;
  esac
}

to_action() {
  awk -F- '{ for (i = 1; i <= NF; i++) printf "%s%s", toupper(substr($i, 1, 1)), substr($i, 2) }' <<<"$1"
}

status=0

for pair in "${CALLERS[@]}"; do
  caller="${pair%%:*}"
  role="${pair#*:}"

  # コメントの中の例示を拾わないよう、# から行末を落としてから探す
  calls="$(sed -E 's/#.*$//' "$root/$caller" | grep -oE '\baws [a-z0-9]+ [a-z0-9-]+' | sed -E 's/^aws //' | sort -u)"

  if [ -z "$calls" ]; then
    echo "$caller calls no AWS API. If that is intentional, drop it from this check." >&2
    status=1
    continue
  fi

  while read -r service command; do
    action="$(resolve_exception "$service $command")"
    [ -n "$action" ] || action="$service:$(to_action "$command")"

    if ! grep -qF "\"$action\"" "$root/$role"; then
      echo "$caller calls 'aws $service $command', but $role does not allow $action." >&2
      status=1
      continue
    fi

    echo "$caller: aws $service $command -> $action"
  done <<<"$calls"
done

exit "$status"
