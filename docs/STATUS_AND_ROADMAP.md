# 開発状況と残タスク

> **このドキュメントの位置づけ**
> バックエンドの内部品質に関する残タスクを持つ。**記載事項がゼロに収束することを維持する**（完了した項目は削除し、何をいつ実施したかの正は git コミット履歴）。
> 本番リリースに向けた整備タスク（機能・インフラ・フロントエンド）は GitHub issue **#132** が正。
> 実装済みの構造・スキーマ・APIは実コードが正のため、ここでは再記述しない。設計判断は [DECISIONS.md](DECISIONS.md)、構成・境界は [ARCHITECTURE.md](ARCHITECTURE.md)、規約と強制ルールは [CODING_GUIDELINES.md](CODING_GUIDELINES.md)。

---

## 現状

Article / Tune / Album / AlbumArticle の4集約で domain → application → REST → 統合テストの Create/Get/Update/Delete/List が通っている。Album のトラック、アセット（画像）アップロード、APIキー認証・認可、公開/非公開制御も実装済み。フロントエンド（`frontend-admin` / `frontend-public`）はテンプレート状態で、作り直しは #122 / #123。

子コレクションへの追加系ユースケースのうち、REST を公開していないものが残る（`AlbumArticle` の `acquisitionChannels`、記事タグ）。#120 で扱う。

---

## 残タスク

### 品質ゲート

1. カバレッジ計測（JaCoCo導入の検討）
2. **SpotBugs / PMD 組込ルールセットの再導入検討**: SpotBugs 4.10.2（Gradle plugin 6.5.8）は Java25 対応済み。バグパターン系を SpotBugs、collection/security 系を PMD 組込ルールセットで補う。本プロジェクト固有規約（[CODING_GUIDELINES.md](CODING_GUIDELINES.md) §1）とは別系統で、導入時に顕在化する違反の段階是正・除外スコープ設計が要る。品質ゲート＝ポリシー変更のため都度承認のうえ実施

### ドキュメント

3. `docs/DECISIONS.md` に無い設計判断が実装中に出た場合、その都度追記する（文書を増やさない）

---

## 参照

- 設計判断（なぜ）: [DECISIONS.md](DECISIONS.md)。JSpecify 移行の設計・除外方針は #44 が正
- 構成・境界・経路: [ARCHITECTURE.md](ARCHITECTURE.md)。AWS構成とデプロイは `infra/README.md`
- 規約と強制ルールの索引: [CODING_GUIDELINES.md](CODING_GUIDELINES.md)。テスト分離規約は `backend/TEST_GUIDE.md`
- リリースに向けた整備タスク: GitHub issue #132
