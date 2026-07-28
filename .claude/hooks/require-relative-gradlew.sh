#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# backend/gradlew を絶対パスで呼び出そうとした場合に拒否し、許可済みの相対パス形を促す。
#
# 目的: settings.json の allowlist は `Bash(backend/gradlew:*)`（相対パスの前方一致）
#       のみを許可している。絶対パス化すると allowlist にマッチせずプロンプトが発生する上、
#       CWD が backend/ でないと `-p backend` を省いた誤呼び出しにもつながる。この表記を
#       避ける制約はエージェントの記憶依存であり、同一セッション内で再発した実績がある
#       ため hook で機械的に拒否する。

cmd=$(jq -r '.tool_input.command // empty')

if printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_/])/[^[:space:]]*/backend/gradlew([[:space:]]|$)'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"backend/gradlew は絶対パスではなく、リポジトリルートから相対パスで `backend/gradlew -p backend <task...>` の形で呼び出してください。"}}
JSON
  exit 0
fi

exit 0
