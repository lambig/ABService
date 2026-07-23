#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# `git commit` の直前に、ステージ内容を見てドキュメント関連の注意喚起を返す。
# 常に非ブロッキング（continue は止めない。additionalContext のみ注入する）。
#
# 目的: コードとドキュメント（STATUS_AND_ROADMAP.md 等）の意味的な同期、および
#       「現在形記述・作業ログ禁止・残タスク一覧は完了項目を削除してバーンダウンする」
#       （docs/README.md のドキュメント記述規約）は機械強制できない。
#       乖離・逸脱を作り込みやすいコミットに限って見直しを促し、アラート疲れを避ける。
#
# 分岐:
#   A) docs/ 配下の *.md がステージされている
#      → バーンダウン規約（完了項目は削除・作業ログ禁止）の見直しを促す。
#   B) docs は未ステージだが、ソース/設定がステージされている
#      → ドキュメントとの乖離が無いか確認を促す（従来の挙動）。
#   C) いずれでもない → 何も出さない。

cmd=$(jq -r '.tool_input.command // empty')

# git commit 以外では何もしない（settings.json 側の if でも絞っているが二重に防ぐ）
if ! printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

staged=$(git diff --cached --name-only 2>/dev/null)
if [ -z "$staged" ]; then
  exit 0
fi

# A) docs/ 配下の .md がステージされている場合はバーンダウン規約の確認を促す
if printf '%s\n' "$staged" | grep -qE '^docs/.*\.md$'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"docs/ 配下の変更がステージされています。コミット前に docs/README.md のドキュメント記述規約に沿っているか確認してください: (1) 完了した項目は「✅完了」等のマークを付けて残すのではなく、残タスク一覧・ロードマップから削除したか（正は git コミット履歴）。(2) 「〜を実装した／追加した」等の作業ログではなく、現在の状態を記述しているか。(3) STATUS_AND_ROADMAP.md 等のロードマップ系ドキュメントは、作業進捗に伴って記載事項が減っていき、最終的に記載事項ゼロへ収束する構造を保てているか（セクション丸ごと解消済みになった場合はセクション自体を削除する）。問題なければそのまま進めて構いません。"}}
JSON
  exit 0
fi

# B) ソース/設定がステージされているか（docsは未ステージの場合のみ従来の乖離チェックを出す）
if printf '%s\n' "$staged" | grep -qE '^(backend/src/|backend/config/|backend/build\.gradle|backend/settings\.gradle|gradle\.properties|\.github/workflows/|\.claude/)'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"このコミットはソース/設定を含みますが docs/ の更新を伴っていません。コミット前に、変更が docs/STATUS_AND_ROADMAP.md（§1 状態表・§5 残タスク棚卸し・§6 ロードマップ）や関連ドキュメントの記述と乖離していないか確認してください。乖離があれば同じコミットで docs を現在形に更新し（作業ログは書かない）、乖離がなければそのまま進めて構いません。"}}
JSON
  exit 0
fi

exit 0
