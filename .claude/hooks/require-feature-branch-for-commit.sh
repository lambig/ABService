#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# git commit を、現在ブランチが main/master のとき拒否する。
#
# 目的: ABService はソロリポジトリでも必ずフィーチャーブランチを切ってから
#       コミットする方針だが、この制約はエージェントの記憶依存であり
#       「直近の履歴がmain直コミットだったので慣行と誤認した」再発実績がある。
#       hook で機械的に拒否する。

cmd=$(jq -r '.tool_input.command // empty')

if ! printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)

case "$branch" in
  main|master)
    cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"main/master への直接コミットは禁止です。`git switch -c <feature-branch>` で作業内容が分かるブランチ名を付けて作成してから、改めてコミットしてください。"}}
JSON
    exit 0
    ;;
esac

exit 0
