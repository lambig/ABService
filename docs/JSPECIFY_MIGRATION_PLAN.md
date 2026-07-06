# JSpecify Nullability Annotations Migration Plan

> ⚠️ **進捗の正は [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md) §5.1 を参照。**
> このドキュメントは方針・改修パターンのリファレンスです。以下「進捗状況」節の記載は当時のものです。
> 2026-07-06 の実コード検証では、jspecify を import している domain クラスは **11件**（`Album`, `Track`, `Article`, `Tune`, `ArticleTag`, `CatalogNumber`, `AlbumTitle`, `TuneTitle`, `Credit`, `ArtistCredit`, `MarkupContent`）で、当時からほぼ進んでいません。`AlbumArticle` 集約・集約内エンティティ・大半のVO・infrastructure/application層は未対応です。

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

4. **Tune.java** - 曲マスター集約
   - 全フィールドにアノテーション追加
   - 全publicメソッドにアノテーション追加
   - Optional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED
   - 実施日: 2025年12月3日

5. **Article.java** - 記事集約
   - 全フィールドにアノテーション追加（内部クラスIDには`Album.@Nullable Id`形式を使用）
   - 全publicメソッドにアノテーション追加
   - Optional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED
   - 実施日: 2025年12月3日

6. **ArticleTag.java** - 記事タグEntity
   - 全フィールドにアノテーション追加
   - 全publicメソッドにアノテーション追加
   - Optional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED
   - 実施日: 2025年12月3日

7. **AlbumTitle.java** - Value Object
   - recordパラメータにアノテーション追加
   - コンパクトコンストラクタでOptional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED
   - 実施日: 2025年12月3日

8. **TuneTitle.java** - Value Object
   - recordパラメータにアノテーション追加
   - コンパクトコンストラクタでOptional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED
   - 実施日: 2025年12月3日

9. **Credit.java** - Value Object
   - recordパラメータにアノテーション追加
   - コンパクトコンストラクタでOptional.ofNullableによる実行時チェック実装
   - テスト: ✅ PASSED
   - 実施日: 2025年12月3日

10. **ArtistCredit.java** - Value Object
    - 全フィールドにアノテーション追加
    - コンストラクタとファクトリメソッドにアノテーション追加
    - Optional.ofNullableによる実行時チェック実装
    - テスト: ✅ PASSED
    - 実施日: 2025年12月3日

### 📊 進捗サマリー
- **完了**: 10クラス（Aggregate: 3, Entity: 2, Value Object: 5）
- **残り**: 約15-20クラス
- **次回優先度**: 残りのAggregateとEntity、主要Value Object

### 🔄 次回セッションで実施予定

#### Phase 1: 残りのAggregateクラス（優先度: 高）
- [ ] **AlbumArticle.java** - アルバム記事集約（存在する場合）
  - 対象メソッド: `create()`, `reconstruct()`, `updateIntro()`, `addAcquisitionChannel()`, etc.
  - 推定作業時間: 30分

#### Phase 2: 集約内Entityクラス（優先度: 中）
- [ ] **TrackTune.java** - Track内のチューン情報
- [ ] **AlbumAcquisitionChannel.java** - アルバム入手経路
- [ ] **AlbumDistribution.java** - アルバム頒布情報

#### Phase 3: 残りのValue Objectクラス（優先度: 中）
- [ ] **TrackTitle.java**
- [ ] **BusinessDate.java**
- [ ] **Duration.java**
- [ ] **EventReleasedAt.java**
- [ ] **MarkupContent.java**
- [ ] **Isdn.java**
- [ ] **TuneKind.java**
- [ ] **ArticleType.java**
- [ ] **ArtistCreditName.java**

#### Phase 4: Infrastructure層とApplication層（優先度: 低）
- [ ] Mapper classes
- [ ] Repository implementations
- [ ] Application Service classes
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
