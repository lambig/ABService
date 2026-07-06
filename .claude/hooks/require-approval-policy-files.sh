#!/usr/bin/env bash
# PreToolUse(Edit|Write|NotebookEdit) hook:
# 品質ゲート／プロジェクト方針に関わる「ポリシーファイル」への編集を検知したら
# permissionDecision:"ask" を返して明示承認を強制する。
# （allow / acceptEdits を上書きし、必ず承認プロンプトを出す）
#
# 目的: これらの変更は「テストを緑にする作業の一部」ではなく設計方針の決定であり、
#       ユーザーの明示的な許可なしに自律的に適用してはならない。
#
# 対象:
#   - config/checkstyle/**            (checkstyle.xml, suppressions.xml)
#   - config/spotless/**              (フォーマッタ設定)
#   - build.gradle / settings.gradle / gradle.properties / gradle/wrapper/**
#   - .claude/settings.json           (ハーネス方針)
#   - .github/workflows/**            (CI 設定)

path=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty')

if [ -z "$path" ]; then
  exit 0
fi

if printf '%s' "$path" | grep -qE '(/config/checkstyle/|/config/spotless/|/build\.gradle$|/settings\.gradle$|/gradle\.properties$|/gradle/wrapper/|/\.claude/settings\.json$|/\.github/workflows/)'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask","permissionDecisionReason":"ポリシー/品質ゲート設定ファイルへの編集です（checkstyle・spotless・build.gradle・gradle.properties・.claude/settings.json・CI 等）。これは設計方針の決定にあたるため、自律的に適用せず明示承認が必要です。承認前に、変更内容と代替案・トレードオフをユーザーに説明して合意を得てください。"}}
JSON
fi
