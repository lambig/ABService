# リポジトリ実装における簡略化事項と今後の対応

## 概要

本ドキュメントでは、リポジトリ実装において一時的に簡略化された機能と、それらを完全に実装するための方針を記載します。

## 簡略化された機能

### 1. Article集約 - タグ機能

**ファイル**: `ArticleMapper.java`

#### 現状
- `Article.tags` フィールドは空のリスト `Collections.emptyList()` として返される
- `ArticleTagLinkEntity` との連携が未実装
- タグの保存・取得が行われない

#### 影響範囲
```java
// ArticleMapper.java (Line 50)
Collections.emptyList()); // タグは簡略化のため空リスト

// ArticleMapper.java (Line 78)
// タグリンクは簡略化のため省略
```

#### 完全実装のための要件

1. **ArticleTagEntity とのマッピング実装**
   - `ArticleTagEntity` の読み込み
   - `ArticleTag` ドメインモデルへの変換
   - `ArticleTagLinkEntity` を通じた多対多関連の処理

2. **必要な実装**
   ```java
   // toDomain メソッド内
   var tags = entity.getArticleTagLinks() != null
       ? entity.getArticleTagLinks().stream()
           .map(link -> new ArticleTag(
               new ArticleTag.Id(link.getArticleTag().getArticleTagId()),
               link.getArticleTag().getName()))
           .collect(Collectors.toList())
       : Collections.emptyList();
   
   // toEntity メソッド内
   if (article.tags() != null && !article.tags().isEmpty()) {
       var tagLinks = article.tags().stream()
           .map(tag -> {
               var link = new ArticleTagLinkEntity();
               var linkId = new ArticleTagLinkId();
               linkId.setArticleId(articleEntity.getArticleId());
               linkId.setArticleTagId(tag.id().value());
               link.setId(linkId);
               link.setArticle(articleEntity);
               // ArticleTagEntity の取得または作成が必要
               return link;
           })
           .collect(Collectors.toList());
       articleEntity.setArticleTagLinks(tagLinks);
   }
   ```

3. **追加で必要なデータアクセス**
   - `ArticleTagDataSource` の作成
   - `ArticleTagRepository` の実装（必要に応じて）
   - タグの事前ロードクエリの実装

---

### 2. AlbumArticle集約 - 頒布情報 (AlbumDistribution)

**ファイル**: `AlbumArticleMapper.java`

#### 現状
- `AlbumArticle.distribution` フィールドは常に `null` として返される
- `AlbumDistributionEntity` との連携が未実装

#### 影響範囲
```java
// AlbumArticleMapper.java (Line 41)
null, // 頒布情報は簡略化のためnull

// AlbumArticleMapper.java (Line 64)
// 頒布情報と入手経路は簡略化のため省略
```

#### 完全実装のための要件

1. **AlbumDistributionEntity とのマッピング実装**
   - `AlbumEntity` から `AlbumDistributionEntity` を取得
   - `AlbumDistribution` ドメインモデルへの変換

2. **必要な実装**
   ```java
   // toDomain メソッド内
   AlbumDistribution distribution = null;
   if (entity.getAlbum() != null && entity.getAlbum().getAlbumDistribution() != null) {
       var distEntity = entity.getAlbum().getAlbumDistribution();
       distribution = new AlbumDistribution(
           distEntity.getEventPrice() != null ? new Price(distEntity.getEventPrice()) : null,
           distEntity.getDownloadPrice() != null ? new Price(distEntity.getDownloadPrice()) : null,
           distEntity.getDemoUrl() != null ? new Url(distEntity.getDemoUrl()) : null
       );
   }
   
   // toEntity メソッド内（AlbumEntity側で管理されるため、ここでは処理不要の可能性あり）
   ```

3. **データ取得の考慮事項**
   - `AlbumArticleEntity` → `AlbumEntity` → `AlbumDistributionEntity` の関連
   - LAZY ロードの適切な処理
   - N+1 問題を避けるための JOIN FETCH クエリ

---

### 3. AlbumArticle集約 - 入手経路 (AlbumAcquisitionChannel)

**ファイル**: `AlbumArticleMapper.java`

#### 現状
- `AlbumArticle.acquisitionChannels` フィールドは空のリスト `Collections.emptyList()` として返される
- `AlbumAcquisitionChannelEntity` との連携が未実装

#### 影響範囲
```java
// AlbumArticleMapper.java (Line 42)
Collections.emptyList()); // 入手経路は簡略化のため空リスト

// AlbumArticleMapper.java (Line 64)
// 頒布情報と入手経路は簡略化のため省略
```

#### 完全実装のための要件

1. **AlbumAcquisitionChannelEntity とのマッピング実装**
   - `AlbumEntity` から `AlbumAcquisitionChannelEntity` のリストを取得
   - `AlbumAcquisitionChannel` ドメインモデルへの変換

2. **必要な実装**
   ```java
   // toDomain メソッド内
   var acquisitionChannels = Collections.<AlbumAcquisitionChannel>emptyList();
   if (entity.getAlbum() != null && entity.getAlbum().getAcquisitionChannels() != null) {
       acquisitionChannels = entity.getAlbum().getAcquisitionChannels().stream()
           .map(channelEntity -> new AlbumAcquisitionChannel(
               new AlbumAcquisitionChannel.Id(channelEntity.getChannelId()),
               ChannelType.valueOf(channelEntity.getChannelType()),
               channelEntity.getName(),
               channelEntity.getUrl() != null ? new Url(channelEntity.getUrl()) : null,
               channelEntity.getNote()
           ))
           .collect(Collectors.toList());
   }
   
   // toEntity メソッド内（AlbumEntity側で管理されるため、ここでは処理不要の可能性あり）
   ```

3. **データ取得の考慮事項**
   - `AlbumArticleEntity` → `AlbumEntity` → `AlbumAcquisitionChannelEntity` の関連
   - LAZY ロードの適切な処理
   - コレクションの JOIN FETCH クエリ

---

## 実装優先順位

### Phase 1: 基本機能（完了）
- ✅ 全リポジトリの基本CRUD操作
- ✅ カスタム検索メソッド
- ✅ 基本的なドメインモデル↔エンティティ変換

### Phase 2: 関連エンティティの完全対応（次のステップ）

#### 優先度: 高
1. **AlbumArticle - 頒布情報**
   - ビジネス上重要な情報
   - 実装は比較的単純（1対1関係）
   
2. **AlbumArticle - 入手経路**
   - ユーザー向け重要情報
   - 実装は中程度の複雑さ（1対多関係）

#### 優先度: 中
3. **Article - タグ機能**
   - 記事の分類・検索に有用
   - 多対多関連の実装が必要
   - ArticleTagRepository の追加検討が必要

### Phase 3: パフォーマンス最適化
- JOIN FETCH クエリの最適化
- N+1 問題の解消
- キャッシング戦略の検討

---

## 実装時の注意事項

### 1. トランザクション境界
- `AlbumArticle` は `Album` とは別の集約
- 頒布情報と入手経路は `Album` 側のエンティティを参照
- 整合性の保証方法を慎重に設計

### 2. 遅延ロード vs 即時ロード
- デフォルトはLAZYロード
- 必要に応じて明示的なFETCH戦略を使用
- DTOパターンの検討

### 3. テスト戦略
- 各簡略化箇所を解除する際は、包括的なテストを追加
- Mapperの単体テスト
- Repository統合テスト
- エンドツーエンドテスト

---

## 参考資料

- [REPOSITORY_IMPLEMENTATION.md](./REPOSITORY_IMPLEMENTATION.md) - リポジトリ設計の全体方針
- [DOMAIN_MODEL_DESIGN.md](./DOMAIN_MODEL_DESIGN.md) - ドメインモデルの詳細設計
- [DATABASE_DESIGN.md](./DATABASE_DESIGN.md) - データベーススキーマ設計

---

## 変更履歴

| 日付 | 内容 |
|------|------|
| 2025-12-02 | 初版作成。簡略化箇所の特定と実装方針の策定 |
