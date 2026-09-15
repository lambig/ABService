# フロントエンドの配布と復旧

通常のコード配布は main の CI → frontend配布記録のpreflight → backend Deploy 成功 → Deploy frontend の順に進む。preflightはfrontendロールでpending不在とcurrentの読取り・形式を検査し、失敗するとbackendへ進まない。初回のcurrent不在は許すが、権限・通信・JSONエラーは初回扱いしない。検査helperはCI成功SHA、通常のfrontend配布helperとビルド対象はbackendが実際に配ったfull SHAに固定する。

内容の変更だけを配る場合は GitHub Actions の **Deploy frontend** を main から実行し、`action=rebuild-public` を選ぶ。公開中の public の SHA を記録から取得し、管理画面はビルド・配布しない。手動操作のhelperは起動時のmain SHAを使う。初回の配布記録が無い間は再ビルドできない。

## 操作の排他と再実行

通常配布は `deploy-production` をpreflightからbackend/frontend完了まで保持する。手動frontend再ビルド・rollbackと手動backend rollbackも同じgroupで待つ。通常の再利用frontendは親がロックを保持しているため、run固有のgroupを使って親のロックを再取得しない。

`queue: max`で最大100件の待機を保持し、後発の手動操作で待機中の通常配布を置換しない。順序はgroupで待ち始めた順であり、commit順ではない。上限超過のrunはキャンセルされるので、run結果と配布記録を確認する。Actions外の直接操作にはこの排他は効かない。[GitHubのconcurrency仕様](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)を参照。

通常配布のpreflightでは、ロック取得後に `last-normal.json` のコードSHA（通常配布として最後に受理した世代）と今回のCI成功SHAをGitの全履歴で比較する。初回・同一SHA・受理済みSHAの子孫への更新だけを許可する。許可したSHAは **backend実行前** にこの記録へ保存し、保存に失敗するとbackendへ進まない。backendの失敗、frontendのビルド失敗・部分配布、明示的なfrontend/backend rollback、内容再ビルドではこの記録を巻き戻さない。受理の記録であり、配布成功の証拠ではない。

たとえば通常配布Cの後でfrontendだけをAへrollbackしても、通常配布の基準はCのまま。遅れて祖先BのCIが成功した場合は、preflightのSummaryへstaleによるスキップを表示し、backend/frontendを実行せず全配布記録を維持する。Cの子孫Dは許可する。`git revert`による新しい子孫commitも通常配布できる。同一の受理済みSHAは再実行を許すため、明示rollback後でもCのCI/Deploy再実行はCへ戻す操作になる。

履歴が分岐、比較対象commitが不明、public/adminのコードSHAが不一致、activeのSHAが受理済み世代の履歴外の場合は停止する。`last-normal.json` の読取り不能・形式不正も停止する。`current.json` があるのに受理記録が無い場合は **active SHAから自動初期化しない**。activeはrollback済みかもしれないため、通常配布を止め、releaseバケットのversion履歴とDeploy記録から最後の受理世代を復元してから再開する。current/受理記録の両方が無い新規環境だけを初回として扱う。旧版を試験配布した環境でも、受理履歴を確定せずactive SHAを転記しない。

失敗した通常Deployを再実行するときは、pendingがあれば先に復旧し、**Re-run all jobs**を使う。preflightとbackendの結果は同じattemptだけで有効とし、「失敗したジョブだけ再実行」で以前の検査やbackend配布結果を流用して後段へ進むことは拒否する。手動backend rollbackは復旧用なのでpendingによるpreflightを迂回できるが、共通groupの排他は保つ。

## 初回設定（運用者）

Terraform 適用後の output と運用側の値を、GitHub Actions の Variables に設定する。AWS の実値は運用リポジトリで管理する。新しい配布ロールは main の OIDC だけを信頼し、画像アセット・DB・シークレットの権限を持たない。

| Variable | 出所 |
| --- | --- |
| `AWS_FRONTEND_DEPLOY_ROLE_ARN` | `github_actions_frontend_deploy_role_arn` |
| `FRONTEND_PUBLIC_BUCKET` | `frontend_public_bucket_name` |
| `FRONTEND_ADMIN_BUCKET` | `frontend_admin_bucket_name` |
| `FRONTEND_RELEASE_BUCKET` | `frontend_release_bucket_name` |
| `CLOUDFRONT_DISTRIBUTION_ID` | `cloudfront_distribution_id` |
| `PUBLIC_SITE_URL` | 正規サイトのHTTPS origin（パスなし） |
| `FRONTEND_BUILD_API_BASE_URL` | 公開Query APIへ到達できるHTTPS origin（DNS切替前はCloudFrontのorigin） |

ロール変数が空の間は配布ジョブがskipする。backend Deploy の Variables も別途必要で、初回は両者を設定してから main の CI 成功を起こす。管理画面の API の起点は空文字列で、閲覧中のサイトと同じ origin を使う。APIキーはSSGに渡さない。

## 内容を反映する

編集・公開・非公開化・削除・サイト文言変更を終えてから `rebuild-public` を実行する。ビルドは `build-public.mjs` が公開APIの保存済み世代を前後で照合する。途中に保存が入った場合は、不完全・混在したHTMLを配布せず失敗する。更新が落ち着いてから再実行する。ビルド失敗時には前回の成功メタデータも残さない。

`GET /api/v1/public-data-generation` は不透明なUUIDを `{ "generation": "..." }` で返す。公開Queryと同じ匿名契約で、管理APIキーをビルドへ渡さない。応答は `Cache-Control: no-store`、取得はキャッシュ・リダイレクトを許さず15秒で打ち切る。公開Queryはprimary DBへ接続し、CloudFrontのAPI behaviorは既存のCachingDisabledを維持する。世代・データを異なるレプリカやキャッシュへ分けない。

世代は業務の編集revisionやコードSHAとは別。DBの10個の参照テーブル（作品・曲目・チューン構成・チューン・外部音源・記事・作品参照・タグ・タグ付与・サイト文言）へのINSERT/UPDATE/DELETE/TRUNCATEと同じトランザクションで変わる。取り消した保存では世代も戻る。下書き・結果0件の更新も保守的に含むため、世代の変更が必ず公開HTMLの変更を意味するわけではなく、大小比較や変更回数の算出には使わない。

ビルド成功時の `source/public-build.json` は世代・選択したコードSHA・全ファイルのSHA-256を持ち、配布前に成果物と照合する。これはdistの外に置き、サイトへ配らずGitへも入れない。アーカイブ後、liveへの書込み直前、invalidation完了後にも保存済み世代を再取得する。APIの失敗・形式不正・世代の不一致を検出した場合は、配布完了として受理しない。

成功は、成果物の同期と CloudFront invalidation の完了までを意味する。削除・非公開化した旧HTMLも同期で消し、元の閲覧URLと書換え後のキーの両方を失効させる。公開内容だけの再ビルドでは管理画面とアセットバケットを操作しない。`_astro/` の古いハッシュ付き資産は、開いたままの画面が使うため残す。

S3への複数ファイルの同期は原子的ではなく、DBの保存とも排他ではない。配布中の短時間は新旧が混在し得る。配布中の更新やAPI停止を検出した場合はcurrentを進めずpendingを残し、追加の内容ビルド・通常配布を止める。最後の確認直後の保存は次の世代として未反映になるため、「保存と同時に公開URLが失効する」保証ではない。更新を止めず必ずビルドを成功させるスナップショットや原子的な配信切替は今回導入しない。

## 配布記録と切り戻し

非公開のreleaseバケットが、各配布のファイルとSHA-256、public/adminそれぞれのコードSHAを保持する。manifestのversion 2はさらに `public.generation` を持つ。`manifests/<run番号>-<attempt>.json` はその操作の対象、`current.json` は最後に配布完了した組合せ。`last-normal.json` は `{ "version": 1, "codeSha": "<full SHA>" }` 形式の独立した通常配布受理記録で、過去のmanifestには含めず、rollbackでも書き戻さない。操作開始時は `pending.json` を先に書き、全処理成功後に消す。

Actions Summaryの `savedGeneration` は照会時のDB保存済み世代、`deliveredGeneration` はcurrentの配布完了世代、`codeSha` はそのpublicコード。`needsRebuild` は世代不一致・旧形式・pendingでtrueとなる。pendingがある間はcurrentも実際のliveの状態を保証しない。Summaryは照会時点の観測であり、その後の編集を自動追跡しない。必要なら同じ環境設定で `node infra/release/frontend.mjs status` を実行する（`GITHUB_STEP_SUMMARY` に出力ファイルを指定）。管理画面への状態表示・自動再ビルド・通知は別の残作業。

移行時は旧version 1のcurrentを読んで通常配布・内容再ビルドを開始できるが、旧成果物へのrollbackは拒否する。新backendの世代APIが未配布・利用不能なら新helperのビルド・復旧も失敗する。APIとV47の移行を通常backend配布で先に適用する。世代の欠落を「初回」として自動補完しない。

切り戻しは同じworkflowで `action=rollback` と `release_id=<戻すrun番号>-<attempt>` を指定する。**現在の保存済み世代と一致する記録だけ**、コードを再ビルドせず保存済み成果物へ戻せる。旧形式・異なる世代・世代APIの失敗ではliveへ書き始めない。アーカイブのファイル集合とハッシュも検査する。失敗した配布で新たに生まれたURLも失効対象にする。

- frontendの切り戻しは **public/admin両方**を指定記録へ戻す。内容だけを更新した記録でも、対応するadminの世代を保持している。
- backend・DB・画像の復元は別操作。DB自体の過去への復元は撤回前の世代も戻すため、この照合だけでは防げない。#130の復旧手順で扱う。
- 初回配布が途中失敗した場合は、作成済みmanifestの成果物が正しければ、そのIDへのrollbackで配布を完了できる。初回配布には「前の正常配布」が無い。
- pendingを手で消して次へ進まない。同世代の正常な成果物が無い場合（途中更新・撤回を含む）は `action=recover` でpendingの対象コードSHAと最新データからpublic/adminを再ビルドする。初回配布失敗でcurrentが無い場合も使える。世代照合・両画面の配布・invalidationが完了してからcurrentを更新してpendingを消す。再度失敗しても、以前の途中配布が作ったURLを失効対象から落とさない。pendingが無いとrecoverは拒否する。通常配布受理のコードSHAは変更しない。
- 保存期間によるアーカイブの自動削除は設定しない。実際に戻せる記録を決めてから運用側で整理する。

## 受け入れ確認

配布処理の失敗・取り消し・復旧とworkflowの依存・条件・SHA選択のローカル検査は `node --test infra/release/*.test.mjs`。AWSを模した境界試験とworkflow設定の検査であり、Actionsの実キュー・IAM・CloudFront伝播の実証ではない。本番準備後に次を確認し、結果は運用リポジトリへ保存する。

CIのE2EジョブはPlaywrightの後に `node infra/release/public-generation-acceptance.mjs` を実行する。これは専用E2E DBと実backendへテスト作品・記事を保存し、実Astroビルドで公開HTML、SSG中の保存による棄却、作品の非公開化に伴う記事・一覧・参照の消失を確認する。ローカルでもE2Eスタックを起動した後に実行できる。テストデータとdistを書き換える検査であり、本番APIへ向けない。S3/CDN自体の失効は上の境界試験と下の実AWS受け入れで別途確認する。

1. 初回の両画面配布、管理画面からのAPI呼出しとS3画像PUT。
2. 記事を編集・非公開化して内容再ビルド。世代の未反映→一致、旧HTMLの失効、admin記録の不変を確認。ビルド中の更新は配布せず拒否すること。
3. 意図的に配布を失敗させ、currentが進まずpendingが残ること。同世代の成果物へのrollback、撤回前の世代へのrollback拒否、最新データのrecoverをそれぞれ確認。
4. publicの未存在URLの404本文と、APIの403/404のProblem Detailsを別々に確認（#125）。
5. pendingがある状態で通常Deployを起動し、preflight失敗・backend未実行を確認。通常配布中に手動frontend操作を待機させ、両画面の配布完了後に進むことを確認。
6. 通常配布C→frontend rollback Aの後で古い祖先BのCIを再実行し、staleのSummaryとbackend/frontendのskip、current/last-normal不変を確認。次の子孫Dは許可されることも確認する。backend失敗・frontend部分失敗の後も受理記録が残ることを確認する。スキップしたrunや受理記録を配布成功の証跡として扱わない。

静的S3の未存在キーを公開サイトの404本文に変換する経路は #125 の残件。distribution全体のcustom error responseはAPI応答までHTMLに変えるため採用していない。CSP（#240）・通知（#168）・実環境の復旧演習（#130）を含む残条件の正は各Issue。
