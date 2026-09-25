#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# git commit を、リポジトリのローカル git config user.email が
# ローカルに指定した公開用 identity と一致しない場合に拒否する。
#
# 目的: グローバル設定の identity を意図せず公開コミットへ使わない。
#       期待値も --local で明示し、個人の値を共有コードへ固定しない。
#       設定手順は CONTRIBUTION.md の「公開用 Git identity」を参照。

cmd=$(jq -r '.tool_input.command // empty')

if ! printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

expected=$(git config --local abservice.publicEmail 2>/dev/null)
email=$(git config --local user.email 2>/dev/null)

if [ -z "$expected" ] || [ -z "$email" ] || [ "$email" != "$expected" ]; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"公開用Git identityのローカル設定が未設定または不一致です。CONTRIBUTION.mdに従い、公開するメールアドレスを確認して `git config --local abservice.publicEmail <公開用メール>` と `git config --local user.email <同じ公開用メール>`、`git config --local user.name <公開用名義>` を設定してください。グローバル設定は代用しません。"}}
JSON
  exit 0
fi

exit 0
