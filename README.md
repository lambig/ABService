# ABService

WebService/Site implementation for my own use

## 技術スタック

- **バックエンド**: Quarkus（Reactive）/ Java（Amazon Corretto）/ Gradle
- **データベース**: PostgreSQL（Flyway・Hibernate Reactive Panache）
- **オブジェクトストレージ**: S3互換（本番はS3、開発はMinIO）
- **フロントエンド**: Astro + Svelte（管理画面・公開画面とも静的ビルド）
- **インフラ**: AWS（EC2 + CloudFront + RDS + S3）/ Docker Compose（ローカル）

固定バージョンの運用方針は [CONTRIBUTION.md](CONTRIBUTION.md) の「技術スタックの固定と昇格」、実際の版は `backend/gradle.properties` / `backend/build.gradle` / `backend/gradle/wrapper/gradle-wrapper.properties` が正。

## 設計と規約

現行の構造・スキーマ・APIの正は実装（実クラス / `backend/src/main/resources/db/migration/` / 静的解析の設定）であり、ドキュメントには再記述しない。

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - 構成・境界・経路（CloudFrontのパスベースルーティング、認証方式、アセットの経路）
- [docs/DECISIONS.md](docs/DECISIONS.md) - 設計判断の記録（なぜその構造にしたか）
- [docs/CODING_GUIDELINES.md](docs/CODING_GUIDELINES.md) - 設計上の意図と、静的解析で強制しているルールの索引
- [Issues](https://github.com/lambig/ABService/issues)・[milestone](https://github.com/lambig/ABService/milestones) - 進捗と受け入れ。順序と依存は[ロードマップ #224](https://github.com/lambig/ABService/issues/224)を参照
- [backend/TEST_GUIDE.md](backend/TEST_GUIDE.md) - テスト分離規約
- [docs/README.md](docs/README.md) - ドキュメント記述規約（何を文書に書き、何を書かないか）

## 本番リリースと復旧

管理画面からの試聴用FLAC登録は [登録・関連付け・復旧マニュアル](docs/ADMIN_AUDIO_REGISTRATION.md) を参照してください。

ABService 側の構築・本番リリース・ロールバック機構の技術的な操作と契約は [infra/README.md](infra/README.md) を正とします。設定項目と実行コマンドはそこを入口に参照してください。

実環境での作業順序・停止条件・切替判断・証跡は、非公開の運用リポジトリ（ABAffairs）を正とします。実環境の設定値・投入する本文や画像もそこで管理します。公開リポジトリには実ドメインやアカウント固有値を置かず、秘密値は Git に保存しません。

開発側の依存関係とリリース阻害条件は [ロードマップ #224](https://github.com/lambig/ABService/issues/224)・各 issue・milestone、v1.0 のスコープは [#132](https://github.com/lambig/ABService/issues/132) を正とします。進捗は各 issue と milestone で管理します。CI 通過と本番受け入れは別で、実環境での配信・復旧・監視の確認が必要です。

## 開発

### JJ workspaceで並行開発する

WSL2/Linux x86_64、Git 2.42以上、Docker Compose v2、ホストのNodeを使います。アプリ用のJDK・Node・npm・JJは `docker/workspace/Dockerfile` が揃えます。対応するJJは0.47.1です。WSLのLinuxファイルシステム内に専用checkoutを用意し、既存の未コミット変更を別作業へコピーしません。

```bash
bash scripts/install-jj.sh "$HOME/.local/bin"
export PATH="$HOME/.local/bin:$PATH"
# 既存checkoutにJJ管理を追加する。公開用Git identityはCONTRIBUTION.mdに従って設定する。
jj git init --colocate
bash scripts/setup-workspace-hooks.sh
npm run workspace -- create alpha
npm run workspace -- create beta
```

Windowsから短いWSLコマンドを繰り返し呼ぶ場合、native DockerのコンテナだけではWSLの稼働が維持されない構成があります。PowerShellで `./scripts/wsl-session.ps1 start <distribution>` を初期操作として実行し、`status` で確認します。対象環境を停止した後に `stop` でこの補助プロセスだけを終了します。ディストリビューション全体や他のWSLセッションは停止しません。

新しいworkspaceは元のcheckoutの隣へ作られます。既定の親は `main@origin` です。別の親を選ぶときは `create alpha <revision>` と明示します。作業ごとに別のworking-copy commitを持ち、共有するのは履歴です。初期化済みのworkspaceへ環境だけを登録する場合は、その中で `npm run workspace -- init <id>` を使います。

各workspace内で日常操作を行います。

```bash
npm run workspace -- up alpha
npm run workspace -- status alpha
npm run workspace -- exec alpha bash backend/gradlew -p backend test
npm run workspace -- exec alpha npm run test:patterns
npm run workspace -- logs alpha apps
npm run workspace -- stop alpha
npm run workspace -- up alpha
# 対象環境のコンテナと永続データを破棄する。ソースとJJ履歴は残る。
npm run workspace -- destroy alpha
```

起動時と `status` に表示されるURLを使います。ポートの記録は `.dev-env/state.json`、アプリのログは `.dev-env/logs/` です。ポートが別プロセスに使用されていれば起動は失敗し、そのプロセスを停止しません。`stop` はデータを保持し、`destroy` は対象のComposeプロジェクトだけを削除します。従来のComposeファイルや自動overrideは読み込まず、既存環境のデータも移し替えません。設定済みworkspaceでは `scripts/dev.sh` も同じ起動入口を使います。

DevContainerを使う場合は、WSLで環境を初期化・起動してからそのworkspaceを開きます。`.devcontainer/devcontainer.json` は生成済みComposeの `tools` に接続します。終了時の停止・破棄は上の入口で明示します。

統合テストは `exec <id> bash backend/gradlew -p backend integrationTest` で環境内の専用DBを使います。E2Eはブラウザの準備を含む既存の[E2Eガイド](e2e/README.md)に従います。同じポートを使う開発サーバーとE2E配信を同時には起動せず、E2E用backendにはテスト画面のoriginを `CORS_ORIGINS` で明示してください。`tools` とアプリはその環境のネットワークを共有するため、E2E起動スクリプトのローカル専用DB保護は維持します。同じworkspaceで同じ生成先を使うビルドを同時実行する場合の競合は、別workspace間の分離とは別です。

#### 履歴・公開前確認

```bash
jj status
jj describe -m 'feat: describe this task'
jj bookmark create feat/example -r @
# 他workspaceでの履歴変更によりstaleとなった場合
jj workspace update-stale
```

`setup-workspace-hooks.sh` は公開用のローカルidentityをJJへ引き継ぎ、共有Git管理領域へ検査を設置します。JJ 0.47.1は内部のGit pushへ `--no-verify` を渡すため、専用のGit実行入口でこれを取り除き、GitとJJの送信前に同じ検査を実行します。スクリプト更新時はセットアップを再実行します。Javaの変更では、JJのsnapshotがGitのpre-commitを実行しないため、必要な品質検査を `exec` から実行して結果を公開前の記録へ対応付けます。

公開前には、完全なbase/head SHAを固定して次の入口から差分・途中コミット・レビュー雛形を生成します。`base` は現在のremote main、`head` は対象bookmarkのcommit IDです。

```bash
node scripts/publication-check.mjs prepare <base> <head> origin refs/heads/feat/example
jj git push --bookmark feat/example
```

生成先は共有Git管理領域の `abservice-publication/<head>/` です。送信前に `diff.patch` と `history.patch` の全内容・追加ファイル・コミットのメタデータを確認し、`review.template.json` を元に `review.json` を保存します。ファイル別所見、機密確認、実行済みの検証を記録し、正確な送信先と証拠をレビュー担当が読める場所で提示します。公開する本文・画像等は別途確認して `materials` にそのファイル・SHA-256・所見を対応付けます。確認前の雛形を機械的に合格へ書き換えません。GitHubへのpushは既存の開発用Git接続を持つWSLホストから行い、コンテナへホストの資格情報を持ち込みません。PRはそのbookmarkのブランチから作成・更新し、保存された本文を読み戻します。

検査は証拠の欠落・変更、remoteの変化、既知のトークン形式や模擬機微情報を拒否します。内容の意味や全種類の秘密を自動判定するものではなく、公開前の内容確認は必要です。main・releaseブランチ・タグ・削除はこの入口で扱いません。GitHubコネクタ等のGitフックを通らない公開にも同じ内容確認を行います。フックを無効化した操作や別のGit実行入口に対するセキュリティ隔離ではありません。

#### エージェントの実行場所と権限

編集は対象workspace、コマンドは `workspace exec` または接続した `tools` 内で行います。ソース、JJの共有管理領域、Git管理領域を同じ絶対パスでマウントし、キャッシュ・一時ファイル・生成物は各環境へ保持します。別workspaceの履歴を書き換えられるため、JJのworkspaceを信頼境界として扱いません。

コンテナへDockerソケット、ホストのホーム全体、本番資格情報を渡しません。Dockerによる作成・起動・停止はホスト側の入口が行います。Dockerを操作するホスト側の権限はこのコンテナ境界より広いため、エージェントの通常作業は対象環境へ限定します。PRマージ・mainへの直接統合と本番リリースは通常開発の許可に含めません。

Codexをコンテナ内部で実行する場合は、コンテナを隔離境界にする設定を利用者が管理します。[公式の実行権限ガイド](https://learn.chatgpt.com/docs/agent-approvals-security)に従い、コンテナ内で `--sandbox danger-full-access --ask-for-approval never` を使う構成と、ホストの権限を区別してください。この設定で管理者・実行基盤の強制規則を上書きすることはできません。認証は利用者の明示確認後に開始し、GitHubの読取りは接続済みコネクタを優先します。実行環境が操作ごとの追加承認を強制する場合、その制約の管理主体と未確認の経路を残件として扱います。

### ビルドとテスト

```bash
# バックエンドのビルド
cd backend
./gradlew build

# テスト実行
./gradlew test

# 開発モードで起動
./gradlew quarkusDev
```

### コード品質

実DB・ストレージを使う画面テストの起動と停止は[E2Eガイド](e2e/README.md)を参照してください。

PR の CI は変更箇所と workspace の依存関係から検査対象を選ぶ。レビュー時は集約チェック **CI gate** を確認する。対象外の検査のスキップは許容するが、対象の検査の失敗・中断・スキップは通さない。GitHub 側で必須チェックを指定する場合も CI gate を使う。選択と判定の検査は `node --test scripts/ci-policy.test.mjs`。

main と手動実行は全件を検査する。本番デプロイは引き続き main の CI 成功を前提とする。

使用する検査と設定の入口は[コーディングガイドライン](docs/CODING_GUIDELINES.md#1-静的解析の参照先)、検査を選ぶ基準は[開発ルール](CONTRIBUTION.md#検証の目的層終了条件)を参照してください。

#### コード品質チェック

```bash
# すべてのコード品質チェックを実行
./gradlew check

# Checkstyleのみ実行
./gradlew checkstyleMain checkstyleTest

# Spotlessフォーマットチェック
./gradlew spotlessCheck
```

#### コードフォーマット

```bash
# コードを自動フォーマット
./gradlew spotlessApply
```

`build` は整形を当てません（検査は `check` の `spotlessCheck`）。ビルドが tracked ファイルを書き換えると、同じ入力で作業ツリーが変わり、`git bisect` や「作業ツリーが汚れているか」の判断に副作用が出るためです。整形が崩れているときは `spotlessApply` を明示的に実行します（コミット前は pre-commit の `spotlessCheck` が気付かせます）。

#### Git Hooks

コミット前に自動でコード品質チェックを実行できます。必要なJava版は[build定義](backend/build.gradle)を参照してください：

```bash
# プロジェクトルートで実行（初回のみ）
./scripts/setup-git-hooks.sh
```

**導入されるhooks:**
- `pre-commit`: 品質ゲート（Spotless / Checkstyle / PMD / ユニット+ArchUnit テスト）を実行

JJ workspaceでは上記の公開前検査のセットアップへ委譲します。それ以外では `core.hooksPath=.githooks` を設定し、バージョン管理された検査専用フックを有効にします。
フックは整形や再ステージを行いません。Java の変更はステージ前に `backend/gradlew -p backend spotlessApply` で整形してください。
GitHub API など Git フックを経由しない反映でも、`.githooks/pre-commit` と同じ品質ゲートを事前に実行してください。

**Hooksをスキップする場合（非推奨）:**
```bash
git commit --no-verify
```

#### レポート

コード品質チェックのレポートは以下に生成されます：

- Checkstyle: `backend/build/reports/checkstyle/`
- PMD: `backend/build/reports/pmd/`
- Spotless: コンソール出力
