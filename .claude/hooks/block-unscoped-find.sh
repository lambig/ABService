#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# find コマンドがファイルシステムルート（/）から実行されようとした場合に拒否する。
#
# 目的: ツール利用規約は「find は . または特定のパスから実行し、/ からのスキャンは
#       避ける」と定めているが、この制約はエージェントの記憶（instruction）依存であり
#       強制力がない（実際に find / を実行しようとした事例がある）。hook で機械的に拒否する。

cmd=$(jq -r '.tool_input.command // empty')

if printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])find[[:space:]]+/([[:space:]]|$)'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"find はファイルシステムルート（/）からではなく、. または具体的なパスから実行してください。無制限スキャンはシステムリソースを消耗します。"}}
JSON
  exit 0
fi

exit 0
