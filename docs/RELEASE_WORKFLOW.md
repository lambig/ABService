# mainへの統合と短命なリリース候補（#480）

mainは唯一の継続的な統合先。機能が提供可能になるまでマージを待つ運用にはせず、未完成の入口を閉じて小さく統合する。公開制御と互換性の条件は [CONTRIBUTION](../CONTRIBUTION.md#統合とリリース) に従う。releaseブランチは候補の検証期間だけ存在し、独立した開発系列にはしない。

## 候補を作り、配布する

1. mainのCIと対象issueを確認し、候補に含まれる未完成機能のAPI・直接URL・アセット取得が閉じていること、DB移行・起動・ジョブが既存機能を壊さないことを確認する。
2. mainに含まれるSHAから `release/1.10` のようなブランチを作り、pushする。mainとreleaseへのpushは全件CI、PRは変更範囲に応じたCIを実行する。ブランチ名・版番号はここでは例であり、作成の指示ではない。
3. 候補を検証する。修正はまずmainに統合し、releaseをmainへfast-forwardする。releaseだけにmerge commitを作る場合も、そのcommit自体がmainへ統合済みでなければ配布できない。cherry-pickやforce-pushで候補の履歴を分岐させない。
4. SHAが変わるたびにrelease候補の全件CIと受け入れをやり直す。通常はreleaseへのpushで起動し、移行前のタグにpushトリガーがない場合は下記の手順で候補ブランチのCIを手動起動する。CIの再実行は **Re-run all jobs** を使う。部分的な再実行で必要なジョブが欠けたattemptは配布の根拠にできない。
5. 受け入れ済みのfull SHAに `v1.10.0` のような新しいタグを付けてpushする。タグは `v<major>.<minor>.<patch>`、候補は対応する `release/<major>.<minor>` または `release/<major>.<minor>.<patch>` に限る。既存タグは動かさない。タグ固定後に修正が必要になったら新しい版の候補として検証する。
6. Actionsの **Deploy** を **main** から実行する。`action=release`、`commit_sha=<候補full SHA>`、`release_branch=<候補ブランチ>`、`release_tag=<固定タグ>`、`ci_run_id=<そのブランチのpushまたは手動実行で全件成功したCI run ID>` を指定する。
7. backendとfrontend両方の成功、実環境の受け入れ、配布記録を確認してから候補ブランチを削除する。タグ・ECRイメージ・非公開releaseバケットの記録は残し、実際のSHA・タグ・CI/Deployのrun/attempt・受け入れ結果をABAffairsへ記録する。

タグ作成やCI成功は配布成功を意味しない。実配布はmain上の制御workflowが明示的に行う。AWS認証前に、同じリポジトリのCI workflow・pushまたはworkflow_dispatchイベント・ブランチ・full SHA・最新attemptの全ジョブ成功・タグの解決先・mainへの統合を照合する。照合不能、失敗、skip、候補の移動・削除では停止する。以後の処理は検証したSHAに固定され、待機中にmainが進んでも配布対象を差し替えない。

CI定義の全ジョブ名をmainの制御側から照合するため、CI構成を変えた後の古い候補は再検証が必要になる場合がある。CIのジョブ名は一意な固定値とし、matrix化など構造を変えるときは候補検証も更新する。権限はGitHubのcontents/actions読取りと既存AWSロールを使い、releaseブランチへOIDCの信頼先を広げない。

## 失敗、再配布、緊急修正

検証・preflightに失敗した場合はbackendへ進まない。通常配布は共通の `deploy-production` 排他を保持し、既存の前進のみを許す受理記録 `last-normal.json` を維持する。古い祖先候補はskip、分岐した候補は停止する。失敗した受理済み候補や明示rollbackでも受理記録は巻き戻さない。

通常Deployの再実行は、pendingがあれば先に復旧してから **Re-run all jobs** を使う。候補ブランチ・タグ・CIを再検証し、preflight/backend/frontendは同じattemptだけで連携する。同じSHAのbackendイメージは再利用する。候補ブランチ削除後に通常配布をやり直すなら、タグのSHAから候補を作り直して全件CIを通す。backendの既存イメージへの再配布は `Deploy` の `action=rollback` とfull SHAで行え、候補ブランチやCI runを必要としない。

frontendの `rebuild-public` は現在配布中のpublicのSHAを使う。`rollback` は保存済み成果物と現在の公開データ世代を照合し、`recover` はpendingのSHAから再生成する。いずれもmain上の **Deploy frontend** から行い、候補ブランチの存続には依存しない。backendとfrontend/DBの切り戻しは別操作。[配布・復旧手順](../infra/release/README.md) と [backendの復旧](../infra/README.md#ロールバックbackendデプロイ) を参照する。

緊急修正は、本番タグから短命なhotfixブランチを作る。そこで直したcommitをPRでmainへ **commitを保持するmerge** で統合し、同じhotfixのSHAからpatch用release候補を作る。squash/rebaseでSHAを置き換えると候補がmainの祖先にならないため使わない。mainへ進んだ他の機能を取り込まずに、修正commitを共有できる。releaseの全件CI・タグ固定・明示配布は通常と同じ。完了後にhotfix/releaseブランチを削除する。

移行前の本番タグにはreleaseへのpushトリガーがない。このタグからのhotfixでは、候補ブランチをpushした後にActionsの **CI** → **Run workflow** で **候補のreleaseブランチ** を選ぶ。CIの手動実行も全件検査になる。完了したrunのブランチ・full SHAが候補と一致することを確認し、そのrun IDをmainから起動するDeployへ渡す。mainやタグを選んだCI、PRのCIは代用できない。手動CIでも最新attemptの必要な全ジョブ成功・タグ・mainへの統合の照合は省略しない。古いCI定義に現在必要な検査が欠ける場合は、hotfix上の通常の修正commitでCI定義を揃え、そのcommitもmainへ統合してから候補を再検証する。

ただし、本番タグより先の候補を既に受理した後にrollbackしている場合、古い本番タグからのhotfixは受理履歴と分岐する。その場合は受理済みcommitの子孫で必要な変更をrevertしてから修正し、mainに統合して検証する。受理記録の削除・上書きで前進制約を回避しない。

## 導入と確認

この変更をmainへ統合すると、mainのCI完了による自動配布が止まる。この変更自体の本番配布やAWSの設定変更は移行の前提にしない。旧workflowで起動済み・待機中のDeployは自動で取り消されないため、切替時に完了を確認してから新候補を配布する。#312は従来経路での配布を妨げない既存変更として移行対象外。

ローカル/CIでは候補の不一致・CI失敗/省略・候補更新・同一SHA再実行・排他・受理履歴・保存成果物のrollbackを回帰検査する。実AWSでの最初の候補配布、再ビルド、復旧と削除後の追跡は運用側の受け入れとして残し、コードの検査だけで完了扱いしない。失敗したrunや `candidates/<Deploy run>-<attempt>.json` は検証の証跡であり、成功した配布の証拠ではない。
