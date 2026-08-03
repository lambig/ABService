#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# 素の `docker run` （docker-compose/docker compose 経由でない即席起動）を拒否する。
#
# 目的: ローカルインフラ（DB等）は docker-compose の標準手順（docker/README.md）で
#       起動する方針。即席 docker run はプロジェクト標準手順から外れ、ポート/env/
#       initスクリプト等の設定が標準手順と食い違う環境を作り込みやすい。
#       この制約はエージェントの記憶依存であり、hook で機械的に拒否する。
#
# `docker compose run` / `docker-compose run` は対象外（compose 経由のため許可）。

cmd=$(jq -r '.tool_input.command // empty')

if printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_-])docker[[:space:]]+run([[:space:]]|$)'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"素の `docker run` は禁止です。ローカルインフラは docker-compose の標準手順（docker/README.md、例: `docker compose up -d postgres`）で起動してください。標準手順に無いものが必要な場合はユーザーに相談してください。"}}
JSON
  exit 0
fi

exit 0
