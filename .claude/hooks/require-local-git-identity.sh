#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# git commit を、リポジトリのローカル git config user.email が
# 公開用の identity に明示設定されていない場合に拒否する。
#
# 目的: グローバル git config は業務 identity のため、このリポジトリで
#       --local 設定を怠るとコミットに業務 identity が漏洩する。
#       再クローン時に --local 設定が失われて再発した実績があるため、
#       エージェントの記憶に頼らず hook で機械確認する。

cmd=$(jq -r '.tool_input.command // empty')

if ! printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

expected="ceoolnua51@gmail.com"
email=$(git config --local user.email 2>/dev/null)

if [ "$email" != "$expected" ]; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"リポジトリのローカル git config user.email が公開用identity(ceoolnua51@gmail.com)になっていません（未設定、またはグローバルの業務identityを継承しています）。`git config --local user.email ceoolnua51@gmail.com` と `git config --local user.name Lambig` を実行してから再度コミットしてください。"}}
JSON
  exit 0
fi

exit 0
