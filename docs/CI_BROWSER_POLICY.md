# ブラウザ検査の実行条件（#483）

Playwrightの全件実行はrelease候補の配布前リグレッションに集約する。変更したシナリオの確認はリリース前まで延期しない。通常のlint・型・単体/統合・コンテナ・IaC検査は従来のapplication/listeningの選択を維持する。

| 起動・変更 | Playwrightの対象 |
| --- | --- |
| releaseブランチへのpush、CIの手動実行 | 全スイート・全シナリオ |
| PRのシナリオのみの変更 | 変更した `.spec.ts` ファイル |
| 共通fixture・Playwright設定・起動スクリプト | 影響するスイート全体 |
| PRの試聴アプリ/共有ライブラリ | npm workspace依存を辿ったブラウザスイート全体 |
| PRの公開/管理画面・backend・配信構成 | application E2E全体（保守的な境界） |
| mainへの通常のアプリコード統合 | 全件の重複実行を省略。基本検査とSSG/ローダー受け入れは継続 |
| mainでシナリオ・共通fixture/設定を変更 | PRと同じ例外として対象を実行 |
| シナリオ削除/rename | 残った対象スイート全体 |
| lockfile・package metadata・CIツール・未知/空の差分 | 全件。判定不能を未実行の理由にしない |

選択の正は `scripts/browser-policy.mjs`、結果はCIのSelect CI scopeのSummaryに残す。PRはmerge-baseから両側の変更名を取得し、削除やrename元も選択へ含める。対象シナリオを通常の配布とは別のコードへ差し替えず、そのCIのcheckoutで実行する。mainの初回pushなど比較元がない場合も全件へ倒す。

`scripts/browser-ci.mjs` はファイル名を正規表現としてエスケープし、シェル展開を挟まずPlaywrightへ渡す。ファイルを絞る場合は最初に `--list` で収集し、指定した全ファイルにシナリオがあることを確認する。ゼロ件を成功にしない。application E2Eは既存のpublic/admin-parallel/admin-serialの順序・worker数を維持する。[Playwright CLIのファイル指定と収集](https://playwright.dev/docs/test-cli)を参照。

applicationのジョブはPlaywrightを省いてもスタック準備と、公開データ世代/撤回済みページのSSG・初期データローダーの受け入れを実行する。ブラウザの取得とシナリオ実行だけを条件付きにし、非ブラウザの検査を一緒に省かない。新しく既存CIへ加えたAudioWorklet/Visualizerの専用ブラウザ検査も、同じ選択・リリース必須検査の対象にする。

シナリオは `e2e/src/specs/` または各ブラウザworkspaceの `e2e/` に置く。共通ヘルパーは同じテストディレクトリ、applicationの共通処理は `e2e/src/support/`・`e2e/scripts/` に置く。別workspaceから共通処理を取り込む場合はpackage依存に宣言する。新しい `test:browser` workspaceはsuite/jobを登録するまでCI選択が失敗する。applicationにシナリオを追加した場合は既存Playwright設定のproject分類にも登録する。

PR/mainのCI gateは明示的に選ばれなかったブラウザジョブだけskipを許す。releaseと手動CIは全ブラウザの選択・成功を必須とする。配布側でも全ジョブ成功に加えapplicationの `Run E2E` ステップ成功を確認し、SSG受け入れだけ成功したrunを配布の根拠にしない。候補変更・旧タグからのhotfixの手順は [RELEASE_WORKFLOW](RELEASE_WORKFLOW.md) を参照する。検査を追加した後の古い候補は、必要なCI定義も揃えて再検証する。
