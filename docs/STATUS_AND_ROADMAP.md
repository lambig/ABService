# 開発状況

> **このドキュメントの位置づけ**
> 実装がどこまで通っているかの現状だけを持つ。**残タスクは持たない**（フロー情報は GitHub issue が正。残作業と進捗の正は milestone で、本番リリースに向けた整備は「リリース v1.0」、それ以外は「v1.0以降」。v1.0 の機能スコープの定義は #132）。
> 実装済みの構造・スキーマ・APIは実コードが正のため、ここでは再記述しない。設計判断は [DECISIONS.md](DECISIONS.md)、構成・境界は [ARCHITECTURE.md](ARCHITECTURE.md)、規約と強制ルールは [CODING_GUIDELINES.md](CODING_GUIDELINES.md)。

---

## 現状

Article / Tune / Album の3集約で domain → application → REST → 統合テストの Create/Get/Update/Delete/List が通っている。Album のトラック、アセット（画像）アップロード、APIキー認証・認可、公開/非公開制御も実装済み。記事は種別ごとのサブタイプに分かれ、アルバムへの参照を持てるのは `AlbumArticle`（`ALBUM` 種別）のみ。フロントエンド（`frontend-admin` / `frontend-public`）はテンプレート状態で、作り直しは #122 / #123。

頒布情報・入手経路は実装を持たない。帰属が（作品 × 発表）であり、発表を第一級の概念にする設計とセットで作る（#201）。

---

## 参照

- 設計判断（なぜ）: [DECISIONS.md](DECISIONS.md)。JSpecify 移行の設計・除外方針は #44 が正
- 構成・境界・経路: [ARCHITECTURE.md](ARCHITECTURE.md)。AWS構成とデプロイは `infra/README.md`
- 規約と強制ルールの索引: [CODING_GUIDELINES.md](CODING_GUIDELINES.md)。テスト分離規約は `backend/TEST_GUIDE.md`
- リリースに向けた整備タスク: milestone「リリース v1.0」。v1.0 の機能スコープの定義は #132
