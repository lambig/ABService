#!/usr/bin/env bash
# Stop hook: ターンの終わりに backend の整形（spotlessApply）を当てる。
#
# なぜ Stop か:
#   git の pre-commit で spotlessApply を走らせると、コミットされるのは index の内容なので
#   整形結果が commit に入らない。hook 内で `git add` して救うと、部分ステージ（git add -p）で
#   意図的に外した変更まで巻き込む。編集直後（ステージ前）に当てればどちらも起きない。
#
#   発火点を PostToolUse（編集ごと）にしないのは、編集の直後に毎回ファイルが変わると、
#   同一ファイルへ続けて編集するときに「編集前テキストの不一致」で失敗し、読み直しの往復が
#   増えるため。1ターン1回であれば編集ループを乱さない。
#
# コミットまでの整形の保証は .githooks/pre-commit の spotlessCheck が担う（本フックは
# 「ターン終了時点の作業ツリーを整形済みにしておく」ためのもので、検査の代替ではない）。
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# Java の変更が無ければ Gradle を起動しない（応答しないターンでの無駄を避ける）
if [ -z "$(git status --porcelain -- '*.java' 2>/dev/null)" ]; then
  exit 0
fi

# 整形の失敗（コンパイル不能な途中状態など）はターンを止める理由にしない
backend/gradlew -p backend --console=plain --quiet spotlessApply >/dev/null 2>&1 || exit 0

exit 0
