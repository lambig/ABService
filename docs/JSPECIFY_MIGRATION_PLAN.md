# JSpecify Nullability Annotations Migration Plan

## 概要

コードベース全体にJSpecifyのnullabilityアノテーション（`@NonNull`, `@Nullable`）を追加し、null安全性を向上させる改修計画。

## 方針

### 基本方針
1. **JSpecifyアノテーションの追加**: すべての必須パラメータに`@NonNull`、nullable許可箇所に`@Nullable`を明示
2. **実行時チェックの保持**: `Optional.ofNullable()`を使った関数型的なnullチェックを実施
3. **段階的な適用**: テストを実行しながら順次適用

### 技術的背景
- **NullAwayは現時点で使用しない**: LombokとNullAwayの互換性問題があるため
- **将来的なKotlin化を見据えた設計**: 現在はJava + JSpecifyで進める
- **Lombokとの共存**: アノテーションをフィールド宣言の前に配置（`@NonNull private final Type field;`）

## 改修パターン

### 1. インポートの追加
```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
```

### 2. フィールドへのアノテーション
```java
// 必須フィールド
@NonNull
private final Type field;

// nullable許可フィールド
@Nullable
private final Type nullableField;
```

### 3. メソッドシグネチャ
```java
// 戻り値とパラメータ
public @NonNull ReturnType method(@NonNull ParamType param, @Nullable ParamType nullable)

// 内部クラス型の場合
public @NonNull Album removeTrack(Track.@NonNull Id trackId)

// ジェネリクスの場合
public @NonNull List<Track.@NonNull Id> getIds()
```

### 4. 実行時nullチェック（Optional.ofNullableを使用）
```java
// 従来のif文によるチェック（非推奨）
if (param == null) {
    throw new IllegalArgumentException("Param cannot be null");
}

// 推奨: Optional.ofNullableを使用
var validated = Optional.ofNullable(param)
    .orElseThrow(() -> new IllegalArgumentException("Param cannot be null"));

// メソッドチェーンで直接使用
return withField(Optional.ofNullable(newValue)
    .orElseThrow(() -> new IllegalArgumentException("Value cannot be null")));

// 条件付きチェック
var validatedIds = Optional.ofNullable(orderedTrackIds)
    .filter(ids -> ids.size() == tracks.size())
    .orElseThrow(() -> new IllegalArgumentException("Size mismatch"));
```

## 進捗状況

### ✅ 完了
1. **Album.java** - Album集約ルート
   - 全フィールドにアノテーション追加
   - 全publicメソッドにアノテーション追加
   - Optional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED

2. **Track.java** - Track entity (Album集約内)
   - 全フィールドにアノテーション追加
   - 全publicメソッドにアノテーション追加
   - Optional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED

3. **CatalogNumber.java** - Value Object
   - recordパラメータにアノテーション追加
   - コンパクトコンストラクタでnullチェック追加
   - テスト: ✅ PASSED

### 🔄 次回セッションで実施予定

#### Phase 1: 主要Aggregateクラス（優先度: 高）
- [ ] **Tune.java** - 曲マスター集約
  - 対象メソッド: `create()`, `reconstruct()`, `changeTitle()`, `changeTuneKind()`, etc.
  - 推定作業時間: 30分

- [ ] **Article.java** - 記事集約
  - 対象メソッド: `create()`, `reconstruct()`, `changeTitle()`, `changeArticleType()`, `addTag()`, `removeTag()`, etc.
  - 推定作業時間: 30分

- [ ] **AlbumArticle.java** - アルバム記事集約
  - 対象メソッド: `create()`, `reconstruct()`, `updateIntro()`, `addAcquisitionChannel()`, etc.
  - 推定作業時間: 30分

#### Phase 2: 集約内Entityクラス（優先度: 中）
- [ ] **TrackTune.java** - Track内のチューン情報
- [ ] **AlbumAcquisitionChannel.java** - アルバム入手経路
- [ ] **AlbumDistribution.java** - アルバム頒布情報
- [ ] **ArticleTag.java** - 記事タグ

#### Phase 3: Value Objectクラス（優先度: 中）
- [ ] **AlbumTitle.java**
- [ ] **TrackTitle.java**
- [ ] **TuneTitle.java**
- [ ] **ArtistCredit.java**
- [ ] **BusinessDate.java**
- [ ] **Duration.java**
- [ ] **EventReleasedAt.java**
- [ ] **MarkupContent.java**
- [ ] **Isdn.java**
- [ ] **Isrc.java**
- [ ] **LabelTag.java**
- [ ] その他VOクラス

#### Phase 4: Repositoryインターフェースと実装（優先度: 低）
- [ ] **AlbumRepository.java** / **AlbumRepositoryImpl.java**
- [ ] **TuneRepository.java** / **TuneRepositoryImpl.java**
- [ ] **ArticleRepository.java** / **ArticleRepositoryImpl.java**
- [ ] **AlbumArticleRepository.java** / **AlbumArticleRepositoryImpl.java**

#### Phase 5: Mapperクラス（優先度: 低）
- [ ] **AlbumMapper.java**
- [ ] **TuneMapper.java**
- [ ] **ArticleMapper.java**
- [ ] **AlbumArticleMapper.java**

## 注意事項

### Lombokとの互換性
- `@Accessors(fluent = true)`を使用している場合、getter生成時にアノテーションの扱いに注意
- アノテーションはフィールド宣言の前に配置（`private final @NonNull Type field;`ではなく`@NonNull private final Type field;`）

### recordクラスの扱い
```java
// コンパクトコンストラクタでnullチェックを追加
public record Id(@NonNull String value) implements EntityId<Album> {
    public Id {
        // nullチェックを明示的に実装（JSpecifyだけでは実行時保護されない）
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ID cannot be blank");
        }
    }
}
```

### テスト戦略
- 各Phase完了後に`./gradlew test`を実行
- nullパラメータを渡すテストケースが正しく例外を投げることを確認
- 既存のテストが全てPASSすることを確認

## 期待される効果

1. **コンパイル時の型安全性向上**: IDEと静的解析ツールがnull問題を早期検出
2. **ドキュメント性向上**: APIの契約（nullable/non-null）が明確に
3. **コードの可読性向上**: Optional.ofNullableによる関数型的なnullチェック
4. **将来のKotlin移行の準備**: nullability情報が明示されているため移行が容易

## リファレンス

- [JSpecify Documentation](https://jspecify.dev/)
- [Lombok Compatibility](https://projectlombok.org/)
- プロジェクト内参照実装: `Album.java`, `Track.java`, `CatalogNumber.java`

---

最終更新: 2025年12月3日
作成者: GitHub Copilot
