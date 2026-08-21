#!/usr/bin/env bash
# PreToolUse(Bash) hook:
# `git commit` の直前に、ステージ内容を見てドキュメント関連の注意喚起を返す。
# 常に非ブロッキング（continue は止めない。additionalContext のみ注入する）。
#
# 目的: docs/README.md のドキュメント記述規約（実装が正になった事実は文書に書かない／
#       現在形で書く／作業ログを書かない／残タスクはバーンダウンする）は機械強制できない。
#       逸脱を作り込みやすいコミットに限って見直しを促し、アラート疲れを避ける。
#
# 分岐:
#   A) docs/ 配下の *.md が新規追加されている
#      → その文書が「実装から導けない判断」だけを含むか、既存文書へ統合できないかを問う
#        （実装の再記述は必ず乖離するため、文書を増やす前に疑う）。
#   B) docs/ 配下の *.md が変更されている
#      → 実装の再記述になっていないか、実装が入って役目を終えた節が残っていないか、
#        相互参照先が陳腐化していないかの見直しを促す。
#   C) docs は未ステージだが、ソース/設定がステージされている
#      → 文書側に古くなった記述が残っていないか確認を促す。
#   D) いずれでもない → 何も出さない。

cmd=$(jq -r '.tool_input.command // empty')

# git commit 以外では何もしない（settings.json 側の if でも絞っているが二重に防ぐ）
if ! printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_])git[[:space:]]+commit([[:space:]]|$)'; then
  exit 0
fi

staged=$(git diff --cached --name-only 2>/dev/null)
if [ -z "$staged" ]; then
  exit 0
fi

added=$(git diff --cached --name-only --diff-filter=A 2>/dev/null)

# A) docs/ 配下の .md が新規追加されている場合は、文書を増やす妥当性そのものを問う
if printf '%s\n' "$added" | grep -qE '^docs/.*\.md$'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"docs/ 配下に新規ドキュメントが追加されています。コミット前に確認してください: (1) 内容は『実装から導けないもの』（なぜその構造にしたかの判断・どう書くかの意図・まだ実装がない計画）だけになっているか。テーブル定義・型階層・クラス構成・APIの形・コード例は実装が正であり、文書に書くと必ず乖離する。(2) 既存文書（DECISIONS.md / ARCHITECTURE.md / CODING_GUIDELINES.md / STATUS_AND_ROADMAP.md）へ1節として統合できないか。文書の本数を増やすほど相互参照が陳腐化する。(3) 実装が入った時点で役目を終える文書ではないか（そうなら実装と同時に削除する前提を文書内に明記する）。妥当ならそのまま進めて構いません。"}}
JSON
  exit 0
fi

# B) docs/ 配下の .md が変更された場合は記述規約の確認を促す
if printf '%s\n' "$staged" | grep -qE '^docs/.*\.md$'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"docs/ 配下の変更がステージされています。コミット前に docs/README.md のドキュメント記述規約に沿っているか確認してください: (1) 実装が正になった事実（テーブル定義・型階層・クラス構成・APIの形・コード例）を再記述していないか。(2) 実装が入って役目を終えた節が残っていないか（残すなら判断だけを DECISIONS.md へ移して本体は削除する）。(3) 完了項目は『完了』マークではなく削除したか。作業ログ（〜を実装した）ではなく現在の状態を現在形で書いているか。(4) 相互参照先（README.md・他のdocs・infra/README.md・docker/README.md）の記述や見出し参照が今回の変更で古くなっていないか。問題なければそのまま進めて構いません。"}}
JSON
  exit 0
fi

# C) ソース/設定がステージされているか（docsは未ステージの場合のみ乖離チェックを出す）
if printf '%s\n' "$staged" | grep -qE '^(backend/src/|backend/config/|backend/build\.gradle|backend/settings\.gradle|gradle\.properties|\.github/workflows/|\.claude/)'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"このコミットはソース/設定を含みますが docs/ の更新を伴っていません。コミット前に、文書側に古くなった記述が残っていないか確認してください（docs/STATUS_AND_ROADMAP.md の残タスク・docs/ARCHITECTURE.md の構成や経路・docs/DECISIONS.md の判断が今回の変更で覆っていないか）。実装が正になった事実は文書に書き足さず、必要なら該当記述を削除してください。乖離がなければそのまま進めて構いません。"}}
JSON
  exit 0
fi

exit 0
