# フロントエンドの配布と復旧

通常のコード配布は main の CI → backend Deploy 成功 → Deploy frontend の順に進む。checkout は backend が実際に配った full SHA に固定する。内容の変更だけを配る場合は GitHub Actions の **Deploy frontend** を main から実行し、`action=rebuild-public` を選ぶ。公開中の public の SHA を記録から取得し、管理画面はビルド・配布しない。初回の配布記録が無い間は再ビルドできない。

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

編集・公開・非公開化・削除・サイト文言変更を終えてから `rebuild-public` を実行する。**ビルドから配布終了までは内容を編集しない。** 現行APIは読み取りスナップショットを持たず、ビルド途中の変更まで一貫した世代として取得する保証は無い。

成功は、成果物の同期と CloudFront invalidation の完了までを意味する。削除・非公開化した旧HTMLも同期で消し、元の閲覧URLと書換え後のキーの両方を失効させる。公開内容だけの再ビルドでは管理画面とアセットバケットを操作しない。`_astro/` の古いハッシュ付き資産は、開いたままの画面が使うため残す。

S3への複数ファイルの同期は原子的ではない。配布中の短時間は新旧が混在し得る。途中失敗を成功として記録することはせず、復旧するまで追加の内容ビルド・通常配布を止める。

## 配布記録と切り戻し

非公開のreleaseバケットが、各配布のファイルとSHA-256、public/adminそれぞれのコードSHAを保持する。`manifests/<run番号>-<attempt>.json` はその操作の対象、`current.json` は最後に配布完了した組合せ。操作開始時は `pending.json` を先に書き、全処理成功後に消す。GitHubの成功表示だけで判断せず、失敗時はこの記録とrunを照合する。

切り戻しは同じworkflowで `action=rollback` と `release_id=<戻すrun番号>-<attempt>` を指定する。**コードを再ビルドせず、公開内容を含む保存済み成果物を戻す**。アーカイブのファイル集合とハッシュを検査してからliveバケットへ書く。失敗した配布で新たに生まれたURLも失効対象にする。

- frontendの切り戻しは **public/admin両方**を指定記録へ戻す。内容だけを更新した記録でも、対応するadminの世代を保持している。
- backend・DB・画像の復元は別操作。特に撤回した内容が旧成果物で再公開され得るため、復旧先の内容を確認する。
- 初回配布が途中失敗した場合は、作成済みmanifestの成果物が正しければ、そのIDへのrollbackで配布を完了できる。初回配布には「前の正常配布」が無い。
- pendingを手で消して次へ進まない。記録済みの成果物へ復旧する。archive自体に問題があれば、その記録を使わず別の検証済み記録を選ぶ。
- 保存期間によるアーカイブの自動削除は設定しない。実際に戻せる記録を決めてから運用側で整理する。

## 受け入れ確認

配布処理の失敗・取り消し・復旧のローカル検査は `node --test infra/release/frontend.test.mjs`。AWSを模した境界試験であり、IAM・CloudFront伝播の実証ではない。本番準備後に次を確認し、結果は運用リポジトリへ保存する。

1. 初回の両画面配布、管理画面からのAPI呼出しとS3画像PUT。
2. 記事を編集・非公開化して内容再ビルド。旧HTMLが失効し、adminの記録が変わらないこと。
3. 意図的に配布を失敗させ、currentが進まずpendingが残ること。記録済み成果物へ戻して再照会。
4. publicの未存在URLの404本文と、APIの403/404のProblem Detailsを別々に確認（#125）。

静的S3の未存在キーを公開サイトの404本文に変換する経路は #125 の残件。distribution全体のcustom error responseはAPI応答までHTMLに変えるため採用していない。CSP（#240）・通知（#168）・実環境の復旧演習（#130）を含む残条件の正は各Issue。
