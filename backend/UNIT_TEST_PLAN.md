# ユニットテスト追加計画

> ⚠️ **進捗の正は [docs/STATUS_AND_ROADMAP.md](../docs/STATUS_AND_ROADMAP.md) §5.2 を参照。**
> Phase 1–5（ユニットテスト31クラス）は完了。Phase 6–10（App Service / Repository統合 / Mapper / DataSource / REST）は未着手です。
> 一部パス記述が陳腐化しているため注意（実態: `QueryService` は `application/query/`、REST Resource はルート直下 `com/abservice/*.java`、`SampleResource` は現 `GreetingResource`）。

## 📊 現状分析（2025年12月3日時点）

- **実装クラス総数**: 84クラス
- **テストクラス総数**: 31クラス
  - **既存**: 2クラス（`BusinessDate`, `BusinessDateTime`）
  - **Phase 1完了**: 4クラス（`Price`, `Isdn`, `ArtistCredit`, `EventReleasedAt`）
  - **Phase 2完了**: 9クラス（`AlbumTitle`, `CatalogNumber`, `TrackTitle`, `TuneTitle`, `EventName`, `LabelTag`, `Url`, `ArtistCreditName`, `Credit`）
  - **Phase 3完了**: 3クラス（`ChannelType`, `TuneKind`, `ArticleType`）
  - **Phase 4完了**: 9クラス（`Album`, `Article`, `Tune`, `AlbumArticle`, `ConfirmedEvent`, `DeclinedEvent`, `SelectedEvent`, `TentativeEvent`, `EventMatchingService`）
  - **Phase 5完了**: 4クラス（`Track`, `TrackTune`, `AlbumAcquisitionChannel`, `AlbumDistribution`）
- **テストメソッド総数**: 478メソッド
- **テストカバレッジ**: 約37% (31/84クラス)

## 🎯 テスト追加計画（10フェーズ）

### Phase 1: 重要なValue Objectsのテスト追加 🔴 最優先

**対象**: 複雑なバリデーションや計算ロジックを持つVO

| クラス名 | パス | 行数 | 優先度 | 理由 | ステータス |
|---------|------|------|--------|------|----------|
| ~~`Duration`~~ | ~~`domain/model/vo/album/Duration.java`~~ | ~~79~~ | ~~最高~~ | ~~時間計算ロジック、バリデーション~~ | ❌ 削除済み |
| `Price` | `domain/model/vo/common/Price.java` | 74 | 最高 | 金額計算ロジック | ✅ **完了** |
| ~~`Isrc`~~ → `Isdn` | `domain/model/vo/album/Isdn.java` | 117 | 高 | 複雑なフォーマットバリデーション（ISRC→ISDN変更） | ✅ **完了** |
| `EventReleasedAt` | `domain/model/vo/common/EventReleasedAt.java` | 140 | 高 | 複雑な構造、イベント情報 | ✅ **完了** |
| `ArtistCredit` | `domain/model/vo/common/ArtistCredit.java` | 70 | 高 | 複雑なロジック | ✅ **完了** |

**テスト種別**: ユニットテスト (`src/test/java/`)
**依存**: なし（純粋なビジネスロジック）

---

### Phase 2: 単純なValue Objectsのテスト追加 🟡

**対象**: タイトル系、名前系など単純なバリデーションを持つVO

| クラス名 | パス | 優先度 | ステータス |
|---------|------|--------|----------|
| `AlbumTitle` | `domain/model/vo/album/AlbumTitle.java` | 中 | ✅ **完了** |
| `CatalogNumber` | `domain/model/vo/album/CatalogNumber.java` | 中 | ✅ **完了** |
| `TrackTitle` | `domain/model/vo/album/TrackTitle.java` | 中 | ✅ **完了** |
| `TuneTitle` | `domain/model/vo/tune/TuneTitle.java` | 中 | ✅ **完了** |
| `EventName` | `domain/model/vo/event/EventName.java` | 中 | ✅ **完了** |
| `LabelTag` | `domain/model/vo/album/LabelTag.java` | 中 | ✅ **完了** |
| `Url` | `domain/model/vo/common/Url.java` | 中 | ✅ **完了** |
| `ArtistCreditName` | `domain/model/vo/common/ArtistCreditName.java` | 中 | ✅ **完了** |
| `Credit` | `domain/model/vo/common/Credit.java` | 中 | ✅ **完了** |

**テスト種別**: ユニットテスト (`src/test/java/`)
**テストパターン**: 類似（効率的に実装可能）

---

### Phase 3: Enum系Value Objectsのテスト追加 🟢

**対象**: Enum型のVO

| クラス名 | パス | 優先度 | ステータス |
|---------|------|--------|----------|
| `ChannelType` | `domain/model/vo/album/ChannelType.java` | 低 | ✅ **完了** |
| `TuneKind` | `domain/model/vo/tune/TuneKind.java` | 低 | ✅ **完了** |
| `ArticleType` | `domain/model/vo/article/ArticleType.java` | 低 | ✅ **完了** |

**テスト種別**: ユニットテスト (`src/test/java/`)
**テスト内容**: Enum値の網羅性確認

---

### Phase 4: Aggregateのビジネスロジックテスト 🔴 高優先度

**対象**: 集約ルートの複雑なビジネスロジック

| クラス名 | パス | 行数 | 優先度 | 理由 |
|---------|------|------|--------|------|
| `Album` | `domain/model/aggregate/album/Album.java` | 323 | 最高 | 最大規模、複雑なビジネスロジック |
| `Article` | `domain/model/aggregate/article/Article.java` | 281 | 最高 | 記事集約、複雑なロジック |
| `Tune` | `domain/model/aggregate/tune/Tune.java` | 237 | 高 | チューン集約 |
| `AlbumArticle` | `domain/model/aggregate/albumarticle/AlbumArticle.java` | 210 | 高 | アルバム記事集約 |

**テスト種別**: ユニットテスト (`src/test/java/`)
**依存**: モックを使用してリポジトリや外部依存を切り離す

---

### Phase 5: Entityのビジネスロジックテスト 🟢

**対象**: エンティティの振る舞い

| クラス名 | パス | 行数 | 優先度 | ステータス |
|---------|------|------|--------|----------|
| `Track` | `domain/model/aggregate/album/Track.java` | 291 | 高 | ✅ **完了** |
| `AlbumAcquisitionChannel` | `domain/model/aggregate/albumarticle/AlbumAcquisitionChannel.java` | 160 | 中 | ✅ **完了** |
| `TrackTune` | `domain/model/aggregate/album/TrackTune.java` | 119 | 中 | ✅ **完了** |
| `AlbumDistribution` | `domain/model/aggregate/albumarticle/AlbumDistribution.java` | 105 | 中 | ✅ **完了** |
| `ArticleTag` | `domain/model/entity/article/ArticleTag.java` | 60 | 低 | ✅ **Articleテスト内で実装済み** |

**テスト種別**: ユニットテスト (`src/test/java/`)
**テストメソッド追加数**: 92メソッド

---

### Phase 6: Application Servicesのテスト 🟢

**対象**: アプリケーションサービス

| クラス名 | パス | 行数 | 優先度 |
|---------|------|------|--------|
| `QueryService` | `application/query/QueryService.java` | 109 | 中 |
| `CommandService` | `application/service/CommandService.java` | 85 | 中 |

> 注: 上記2つは基底インターフェース。実際のテスト対象は今後実装する具象ユースケース（`*Service`）。

**テスト種別**: ユニットテスト (`src/test/java/`)
**依存**: リポジトリをモック化してロジックをテスト

---

### Phase 7: Repository実装の統合テスト 🔴 高優先度

**対象**: リポジトリ実装（永続化の中核）

| クラス名 | パス | 行数 | 優先度 |
|---------|------|------|--------|
| `AlbumRepositoryImpl` | `infrastructure/persistence/repository/AlbumRepositoryImpl.java` | 214 | 最高 |
| `ArticleRepositoryImpl` | `infrastructure/persistence/repository/ArticleRepositoryImpl.java` | 202 | 最高 |
| `TuneRepositoryImpl` | `infrastructure/persistence/repository/TuneRepositoryImpl.java` | 195 | 高 |
| `AlbumArticleRepositoryImpl` | `infrastructure/persistence/repository/AlbumArticleRepositoryImpl.java` | 191 | 高 |

**テスト種別**: 統合テスト (`src/integrationTest/java/`)
**依存**: データベース必要、`@QuarkusTest`使用
**前提**: `docker-compose up -d` + `./gradlew flywayMigrate`

---

### Phase 8: Mapperの統合テスト 🟡

**対象**: ドメインモデル ⇔ エンティティの変換ロジック

| クラス名 | パス | 行数 | 優先度 |
|---------|------|------|--------|
| `AlbumMapper` | `infrastructure/persistence/mapper/AlbumMapper.java` | 264 | 最高 |
| `ArticleMapper` | `infrastructure/persistence/mapper/ArticleMapper.java` | 78 | 中 |
| `TuneMapper` | `infrastructure/persistence/mapper/TuneMapper.java` | 70 | 中 |
| `AlbumArticleMapper` | `infrastructure/persistence/mapper/AlbumArticleMapper.java` | 65 | 中 |

**テスト種別**: 統合テスト (`src/integrationTest/java/`)
**依存**: データベース必要（関連エンティティの読み込みのため）

---

### Phase 9: DataSourceの統合テスト 🟢

**対象**: Panache操作を使用したデータソース

| クラス名 | パス | 行数 | 優先度 |
|---------|------|------|--------|
| `AlbumDataSource` | `infrastructure/persistence/datasource/AlbumDataSource.java` | 145 | 中 |
| `ArticleDataSource` | `infrastructure/persistence/datasource/ArticleDataSource.java` | 112 | 中 |
| `AlbumArticleDataSource` | `infrastructure/persistence/datasource/AlbumArticleDataSource.java` | 109 | 中 |
| `TuneDataSource` | `infrastructure/persistence/datasource/TuneDataSource.java` | 86 | 中 |

**テスト種別**: 統合テスト (`src/integrationTest/java/`)
**依存**: データベース必要

---

### Phase 10: REST APIの統合テスト 🔵

**対象**: RESTエンドポイント

| クラス名 | パス | 優先度 |
|---------|------|--------|
| `CircleMemberResource` | `com/abservice/CircleMemberResource.java` | 低 |
| `GreetingResource` | `com/abservice/GreetingResource.java` | 低 |
| `HealthResource` | `com/abservice/HealthResource.java` | 低 |

> 注: 上記はサンプルResource。本命は今後実装する集約向けの Command/Query Resource（`presentation/rest/` 想定）。

**テスト種別**: 統合テスト (`src/integrationTest/java/`)
**依存**: REST Assured使用、エンドツーエンドテスト

---

## 🚀 推奨実装順序

1. **Phase 1** → すぐに価値が出る（複雑なVO）
2. **Phase 4** → コアビジネスロジックの保護（集約）
3. **Phase 7-8** → データ変換の信頼性確保（Repository・Mapper）
4. **Phase 2-3** → カバレッジ向上（単純なVO）
5. **Phase 5-6** → ロジック層の充実
6. **Phase 9-10** → 統合テストの充実

## 📋 実装ガイドライン

### ユニットテスト (`src/test/java/`)

**条件**:
- データベース不要
- 外部システム接続不要
- `@QuarkusTest` **使用しない**

**対象**:
- Value Object (VO)
- Domain Entity
- Domain Service（ロジック部分）
- Aggregate（ビジネスロジック）
- Application Service（モック使用）

**実行コマンド**:
```bash
./gradlew test
./gradlew test --continuous  # 継続的実行（開発時）
```

### 統合テスト (`src/integrationTest/java/`)

**条件**:
- データベース必要
- `@QuarkusTest` 必須
- `@TestTransaction`（必要に応じて）

**対象**:
- Repository実装
- Mapper
- DataSource
- REST APIエンドポイント

**前提条件**:
```bash
# Dockerコンテナ起動
docker-compose up -d

# マイグレーション実行
cd backend
./gradlew flywayMigrate
```

**実行コマンド**:
```bash
./gradlew integrationTest
```

### 全テスト実行（CI用）

```bash
./gradlew check
```

---

## 📊 期待される成果

| フェーズ | テストクラス数（推定） | カバレッジ向上 |
|---------|---------------------|--------------|
| Phase 1 | 5 | +6% |
| Phase 2 | 9 | +12% |
| Phase 3 | 3 | +4% |
| Phase 4 | 4 | +5% |
| Phase 5 | 5 | +7% |
| Phase 6 | 2 | +3% |
| Phase 7 | 4 | +5% |
| Phase 8 | 4 | +5% |
| Phase 9 | 4 | +5% |
| Phase 10 | 3 | +4% |
| **合計** | **43** | **~56%** |

---

## 🔍 参考情報

- **既存テスト**: `BusinessDate`, `BusinessDateTime` (2クラス)
- **Phase 1 進捗**: 4/4 完了 (100%) ✅ **完了**
- **Phase 2 進捗**: 9/9 完了 (100%) ✅ **完了**
- **Phase 3 進捗**: 3/3 完了 (100%) ✅ **完了**
- **Phase 4 進捗**: 4/4 完了 (100%) ✅ **完了**（EventやEventMatchingServiceも追加実装）
- **Phase 5 進捗**: 5/5 完了 (100%) ✅ **完了**
- **最大規模クラス**: `Album` (323行)
- **最大規模マッパー**: `AlbumMapper` (264行)
- **集約ルート数**: 4つ（Album, Article, Tune, AlbumArticle）
- **Value Object数**: 20クラス（基底除く、Duration削除後）
- **リポジトリ実装数**: 4クラス

詳細は `TEST_GUIDE.md` を参照。
