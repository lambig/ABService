# ABService

WebService/Site implementation for my own use

## 技術スタック

- **Java**: Amazon Corretto 25
- **Quarkus**: 3.28.4
- **Gradle**: 9.1.0
- **Lombok**: 1.18.42 (Java 25対応版)
- **Node.js**: 18+
- **Docker**: Latest

## 動作確認済み

- ✅ ビルド: 成功
- ✅ テスト: 成功
- ✅ サーバー起動: 成功
- ✅ Webアクセス: 成功

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

# ビルド時に自動フォーマットが実行されます
./gradlew build
```

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

SpotBugs は現在このプロジェクトでは未導入です。
- 最新版 4.10.2（2026-06）は Java 25 に対応済み（ASM 9.8 / BCEL 6.11）。Gradle plugin は 6.5.8。
- 再導入（PMD 組込ルールセットと併せた errorprone/バグパターン検出）はロードマップのフェーズD で検討する。
