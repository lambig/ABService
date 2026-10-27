# ブラウザ検査の実行条件

保証する故障とテスト層の選択は [CONTRIBUTION.md](../CONTRIBUTION.md#検証の目的層終了条件) を正本とする。Playwrightを使うブラウザ結合試験と、実backend・DBを含むapplication E2Eを区別する。変更したシナリオはリリース前まで延期せず、release候補では残すと決めた全スイートを実行する。

## 変更から実行範囲を選ぶ

基本検査は `scripts/ci-policy.mjs`、ブラウザ検査は `scripts/browser-policy.mjs` が選択する。PRはmerge-base、mainはpushのbeforeから、削除・rename元も含む差分を使う。比較元がなければ全件、差分取得の失敗はCI失敗とする。選択結果はCIのSummaryへ残す。

| 起動・変更 | ブラウザ検査 |
| --- | --- |
| release push・手動実行 | 全スイート・全シナリオ |
| PR/mainのシナリオのみ | 変更したspec。削除・rename元は残った対象スイート全体 |
| 共通fixture・Playwright設定・起動処理 | 影響するスイート全体 |
| PRのアプリ・共有ライブラリ | workspace依存を辿ったスイート。公開/管理画面・backend・構成変更はapplication E2E。管理画面変更は端末準備を含むoffline-playerも選択 |
| 管理画面のVitest対象（`src/lib/**/*.test.ts`、`src/components/**/*.test.ts`）・専用config/setupだけ | ブラウザを省略し、applicationの単体・コンポーネント検査を継続。実装変更との混在時はその実装の範囲も選択 |
| mainの通常アプリコード統合 | 全件の重複実行を省略。基本検査とapplicationのSSG/ローダー受け入れは継続 |
| lockfile・package metadata・CIツール・未知/空の差分 | 全件 |

試聴成果物・CSP・入口の変更は、実際の配信経路を検査するoffline-playerのdeliveryシナリオも選択する。基本検査のapplication/listening/infrastructureは独立した選択で、ブラウザを省略しても必要なlint・型・単体/結合・コンテナ検査を省かない。IaCはinfra変更と選択不能時に実行する。

applicationジョブはブラウザ省略時もスタック準備、公開データ世代/撤回済みページのSSG、初期データローダーの受け入れを実行する。application E2Eが選ばれた場合はジョブ自体も必ず選ぶ。管理画面の単体試験だけの変更で、これら非ブラウザの作業まで省略する選択にはしていない。

## 実行と追加時の契約

`scripts/browser-ci.mjs` はspec名を正規表現としてエスケープし、シェル展開せずPlaywrightへ渡す。限定実行では `--list` で全指定ファイルにシナリオがあることを確認し、ゼロ件を成功にしない。対象コードはCIのcheckoutを使い、applicationのproject順序・worker数を維持する。

シナリオは `e2e/src/specs/` またはブラウザworkspaceの `e2e/` に置く。共通処理は同じテストディレクトリかapplicationの `e2e/src/support/`・`e2e/scripts/` に置き、別workspaceの利用はpackage依存へ宣言する。新しい `test:browser` workspaceはsuite/jobへ、applicationシナリオはPlaywrightのproject分類へ登録する。

CI gateは選択されていないジョブのskipだけを認める。releaseと手動CIは全トラック・全ブラウザと全ジョブ成功を必須とし、デプロイ前にもapplicationの `Run E2E` 成功を確認する。候補変更・旧タグからのhotfixは [RELEASE_WORKFLOW](RELEASE_WORKFLOW.md) に従う。新しい検査が必要な旧候補はCI定義も揃える。
