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
- [docs/STATUS_AND_ROADMAP.md](docs/STATUS_AND_ROADMAP.md) - 開発状況と残タスク
- [backend/TEST_GUIDE.md](backend/TEST_GUIDE.md) - テスト分離規約
- [docs/README.md](docs/README.md) - ドキュメント記述規約（何を文書に書き、何を書かないか）

## 開発

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

ABServiceでは以下のLinting/フォーマットツールを使用しています：

- **Checkstyle**: Google Java Style Guideに基づくコードスタイルチェック＋独自ルール
- **Spotless**: 自動コードフォーマッタ（Eclipse JDT）
- **PMD**: 独自XPathルール（機能的スタイル強制）＋組込の不要変数検出
- **ArchUnit**: アーキテクチャ制約（レイヤー依存方向・配置・戻り値契約など）をテストで強制

> **注意**: SpotBugs は現在未導入です（4.10.2 で Java25 対応済み。再導入はロードマップのフェーズD で検討）。

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

コミット前・プッシュ前に自動でコード品質チェックを実行できます：

```bash
# プロジェクトルートで実行（初回のみ）
./scripts/setup-git-hooks.sh
```

**導入されるhooks:**
- `pre-commit`: 品質ゲート（Spotless / Checkstyle / PMD / ユニット+ArchUnit テスト）を実行
- `pre-push`: ビルドとテストを実行

**Hooksをスキップする場合（非推奨）:**
```bash
git commit --no-verify
git push --no-verify
```

#### レポート

コード品質チェックのレポートは以下に生成されます：

- Checkstyle: `backend/build/reports/checkstyle/`
- PMD: `backend/build/reports/pmd/`
- Spotless: コンソール出力

#### SpotBugsについて

SpotBugs は現在このプロジェクトでは未導入。再導入（PMD 組込ルールセットと併せたバグパターン検出）の検討は [docs/STATUS_AND_ROADMAP.md](docs/STATUS_AND_ROADMAP.md) の残タスク。
