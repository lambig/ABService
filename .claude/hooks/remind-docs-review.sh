#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# `git commit` の直前に、ステージ内容を見て「ソース/設定は変わったが docs/ は未更新」
# = ドキュメント乖離の疑いがあるときだけ、非ブロッキングの注意喚起を返す。
#
# 目的: コードとドキュメント（STATUS_AND_ROADMAP.md 等）の意味的な同期は機械強制できない。
#       乖離を作り込みやすいコミットに限って見直しを促し、アラート疲れを避ける。
#
# 挙動:
#   - permissionDecision は返さない（コミットは止めない）。additionalContext のみ注入する。
#   - docs/ か .md を同じコミットに含めている場合、および無関係なコミットでは何も出さない。

cmd=$(jq -r '.tool_input.command // empty')

# git commit 以外では何もしない（settings.json 側の if でも絞っているが二重に防ぐ）
if ! printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

staged=$(git diff --cached --name-only 2>/dev/null)
if [ -z "$staged" ]; then
  exit 0
fi

# ソース/設定がステージされているか
if ! printf '%s\n' "$staged" | grep -qE '^(backend/src/|backend/config/|backend/build\.gradle|backend/settings\.gradle|gradle\.properties|\.github/workflows/|\.claude/)'; then
  exit 0
fi

# docs（docs/ 配下 or 任意の .md）を同じコミットに含めていれば注意喚起しない
if printf '%s\n' "$staged" | grep -qE '(^docs/|\.md$)'; then
  exit 0
fi

cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"このコミットはソース/設定を含みますが docs/ の更新を伴っていません。コミット前に、変更が docs/STATUS_AND_ROADMAP.md（§1 状態表・§5 残タスク棚卸し・§6 ロードマップ）や関連ドキュメントの記述と乖離していないか確認してください。乖離があれば同じコミットで docs を現在形に更新し（作業ログは書かない）、乖離がなければそのまま進めて構いません。"}}
JSON
