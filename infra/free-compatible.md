# CloudFront Free定額向け互換構成（検証中）

`cloudfront_free_compatible = true` は、定額Freeで利用可能な機能を使う候補構成を選ぶ。既定値はfalseで、従来構成を維持する。**料金プランへの加入や無料適用を行う変数ではない。** このTerraformをapplyすると、既存定義のEC2/RDS/WAF等も作成対象になる。互換性検証だけを目的として実環境へ全体applyしない。

採用判断は #438 と非公開運用側の検証で行う。AWSに受理された配信設定、対象アカウントの契約資格、対象distribution/WebACL/Route53 zoneの対応、Free契約の有効状態、実配信、料金監視を別々に確認する。互換出力 `cloudfront_free_validation` は対象を照合するための値で、契約成功の証拠ではない。Lambda@Edge等のプラン外費用も全体見積もりに含める。

## 配信設定の差

4 behavior・4 origin、WAF、S3 OAC、origin識別ヘッダー、DNS切替と検索公開のゲートは維持する。独自レスポンスヘッダーポリシーとlegacy forwarded_valuesは互換構成では使用しない。

| 経路 | AWS管理キャッシュポリシー | 注意点 |
| --- | --- | --- |
| public（既定）・`/admin*` | CachingDisabled | HTMLの`max-age=0,must-revalidate`や404の`no-store`を最低TTLで上書きしない。これらのbehavior内の`_astro`等もCDNではキャッシュしない。ブラウザ向けCache-Controlは変更しない |
| `/assets/*` | CachingOptimized | 確定済みの不変アセット用。最低TTLは1秒で、no-store/privateでさえ上書きされる。機密・可変・一時アップロードをこの経路へ置かない |
| `/api/*` | CachingDisabled | Authorizationとqueryを転送し、更新系メソッドを維持する |

public/adminの候補をCachingOptimizedにすると、少なくとも1秒はキャッシュするため既存のHTML再検証契約を保てない。UseOriginCacheControlHeadersは最低TTLが0だが、viewerのHostとCookieもキャッシュキーに含み転送するため、S3 OAC向けの単純な代替にはしていない。キャッシュ無効化に伴うS3取得・Lambda呼び出し・遅延増加は実測と料金見積もりの対象にする。配布のinvalidationと撤回確認は省略しない。

APIでは管理ポリシーAllViewerExceptHostHeaderを使う。従来のAuthorization/queryだけに比べ、Cookieや他のviewerヘッダーもoriginへ届く。Hostはorigin用になる。認証・CORS・本文・更新処理・proxyヘッダーの扱いを実APIで受け入れるまで適合完了にしない。

## セキュリティヘッダーとエラー

ヘッダー値の正は引き続き`headers/security.json`で、純粋な共通生成器をローカル配信、CloudFront Functions、Lambda@Edgeが使う。

- viewer-responseのCloudFront Functionを用途別に生成し、キャッシュ済み応答でも現在のCSP/noindexを反映する。
- viewer-responseはoriginの400以上では呼ばれないため、4 originすべてのorigin-responseにLambda@Edgeを関連付ける。用途はviewerのHost/URIではなく構成したoriginのドメインから判断する。
- 静的404本文への変換はpublic/adminだけで行う。API/assetsのステータス・本文・既存ヘッダーを保持する。404取得失敗の503にもセキュリティヘッダーを付ける。
- WAF拒否、CloudFront自身のエラー、Function/Lambda失敗はこのorigin-response経路を通る保証がない。この方式だけで全エラーに独自CSP/noindexが付くとは保証しない。各応答の実測と保証範囲の受け入れが必要。
- エラーキャッシュは通常のキャッシュ設定と別に評価する。CDN設定の反映中・検索公開の変更時は旧ヘッダーがキャッシュに残る可能性も確認し、必要なinvalidation完了後に判定する。

## ローカル検証

```sh
node --test infra/release/*.test.mjs infra/functions/*.test.mjs infra/headers/*.test.mjs infra/monitoring/*.test.mjs
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
terraform -chdir=infra test
python3 -m zipfile -e infra/.terraform/static-page-404.zip /tmp/free-edge-package
node infra/functions/check-edge-package.mjs /tmp/free-edge-package
```

Terraform testはAWS/random/tlsをmockにし、互換性検査ではarchiveだけ実際のローカルproviderを使う。テスト用のtargeted applyは依存値を確定するためのmock操作で、実環境用の適用手順ではない。生成したzipを展開し、entry pointのimport、同梱設定、全originの通常・エラー応答を検査する。既存の既定構成テストも継続する。

これらは実CloudFrontランタイム、関連付け、WAF、AWS伝播、実ブラウザの代わりにはならない。実AWSではFunctionsのDEVELOPMENT stageでのTestFunction、正規originでのCSP/S3 PUT/CORS/外部埋め込み、認証、撤回、失敗・復旧と通知を検証する。一時リソースの削除まで証跡に含める。

## 適用・切り戻し

料金監視の設定を含む準備条件と実配信の受け入れを揃えてから、運用側の手順で公開する。既存配信への導入・撤回時は、policy/Function/Lambdaの関連付け差分と伝播を確認する。falseへの変更は従来の独自policyを復元するので、Free契約を維持したまま受け入れられるとは限らない。契約変更と設定変更の順序をAWSで確認し、課金の変化も含めて切り戻し手順を作る。Lambdaの複製削除待ちも考慮し、DNSやstateを機械的に巻き戻さない。

## 公式資料

- [定額プランの機能・制限・費用](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/flat-rate-pricing-plan.html)
- [AWS管理キャッシュポリシー](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html)
- [AWS管理origin requestポリシー](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-origin-request-policies.html)
- [Edge functionの制限](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/edge-function-restrictions-all.html)
- [エラーキャッシュ](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/custom-error-pages-expiration.html)
