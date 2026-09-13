#!/usr/bin/env bash
# デプロイが叩く AWS API に対して、それを実行する主体のロールが権限を持っているかを検査する。
#
# 権限の不足は実行して初めて分かり、しかもそれはデプロイやロールバックの最中になる。呼び出す側
# （ワークフローとホストのスクリプト）から必要なアクションを求め、対応するロールの定義に在ることを見る。
#
# 必要なアクションは2通りの求め方をする。AWS CLI の呼び出しは、コマンド名が API の名前と綴りが
# 対応するため機械的に導く（get-command-invocation → GetCommandInvocation）。docker や
# actions が AWS を叩く分は綴りに現れないため、下の表に持つ。
#
# 見るのはアクションの有無までで、条件やリソースの範囲は実際の実行が受け持つ。
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

# CLI の呼び出しとして現れないもの。docker が ECR に対して行う操作と、action が取る認証トークン。
# 「アクション 由来」の行で持つ
actions_beyond_cli() {
  case "$1" in
  ".github/workflows/deploy.yml")
    cat <<'ACTIONS'
ecr:GetAuthorizationToken amazon-ecr-login
ecr:BatchCheckLayerAvailability docker push
ecr:InitiateLayerUpload docker push
ecr:UploadLayerPart docker push
ecr:CompleteLayerUpload docker push
ecr:PutImage docker push
ecr:BatchGetImage docker push
ACTIONS
    ;;
  "infra/host/deploy.sh")
    cat <<'ACTIONS'
ecr:BatchGetImage docker compose pull
ecr:GetDownloadUrlForLayer docker compose pull
ecr:BatchCheckLayerAvailability docker compose pull
ACTIONS
    ;;
  esac
}

to_action() {
  awk -F- '{ for (i = 1; i <= NF; i++) printf "%s%s", toupper(substr($i, 1, 1)), substr($i, 2) }' <<<"$1"
}

# AWS CLI の呼び出しを「アクション 由来」の行にする
actions_from_cli() {
  local file="$1"

  # コメントの中の例示を拾わないよう、# から行末を落としてから探す
  sed -E 's/#.*$//' "$file" |
    grep -oE '\baws [a-z0-9]+ [a-z0-9-]+' |
    sed -E 's/^aws //' |
    sort -u |
    while read -r service command; do
      action="$(resolve_exception "$service $command")"
      [ -n "$action" ] || action="$service:$(to_action "$command")"
      echo "$action aws $service $command"
    done
}

status=0

for pair in "${CALLERS[@]}"; do
  caller="${pair%%:*}"
  role="${pair#*:}"

  required="$(
    actions_from_cli "$root/$caller"
    actions_beyond_cli "$caller"
  )"

  if [ -z "$required" ]; then
    echo "$caller needs no AWS permission. If that is intentional, drop it from this check." >&2
    status=1
    continue
  fi

  while read -r action source; do
    if ! grep -qF "\"$action\"" "$root/$role"; then
      echo "$caller needs $action for '$source', but $role does not allow it." >&2
      status=1
      continue
    fi

    echo "$caller: $source -> $action"
  done <<<"$required"
done

exit "$status"
