# ABService移植作業手順書

## 概要

ABServiceプロジェクトで確立されたドメインモデル設計パターンと業務日付概念をABService backendに移植する作業手順書です。

**移植対象**: ドメイン基礎インターフェース、業務日付/日時、DomainService/Factory
**移植先**: ABService backend (Java)
**前提条件**: ABServiceはRESTEasy Reactive (Quarkus 3.x)を使用したフルリアクティブアーキテクチャ

---

## 作業フェーズ

### Phase 1: 事前準備と依存関係の追加
### Phase 2: ドメインモデル基礎インターフェースの実装
### Phase 3: 業務日付/日時の実装
### Phase 4: DomainService/Factoryの実装
### Phase 5: 動作確認とテスト
### Phase 6: ドキュメント整備

---

## Phase 1: 事前準備と依存関係の追加

### 1.1 必要な依存関係を確認・追加

**ファイル**: `backend/build.gradle`

```groovy
dependencies {
    // 既存の依存関係...

    // リアクティブ関連（既に含まれているはず）
    implementation 'io.quarkus:quarkus-rest'
    implementation 'io.quarkus:quarkus-rest-jackson'

    // Mutiny（Reactive Streamsライブラリ）
    implementation 'io.smallrye.reactive:mutiny'

    // UUID v7生成用（業務IDに使用）
    implementation 'com.fasterxml.uuid:java-uuid-generator:5.1.0'

    // Lombok（オプション：Javaのボイラープレートコード削減）
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
}
```

### 1.2 パッケージ構造の作成

以下のディレクトリ構造を作成：

```
backend/src/main/java/com/abservice/
├── domain/
│   ├── model/
│   │   ├── DomainObject.java
│   │   ├── entity/
│   │   │   └── DomainEntity.java
│   │   ├── aggregate/
│   │   │   └── Aggregate.java
│   │   ├── vo/
│   │   │   ├── ValueObject.java
│   │   │   ├── BusinessDate.java
│   │   │   └── BusinessDateTime.java
│   │   └── EntityId.java
│   ├── service/
│   │   ├── DomainService.java
│   │   └── BusinessDateTimeProvider.java
│   ├── factory/
│   │   └── Factory.java
│   └── exception/
│       └── DomainException.java
└── infrastructure/
    └── datetime/
        └── SystemBusinessDateTimeProvider.java
```

コマンド:
```bash
cd backend/src/main/java/com/abservice
mkdir -p domain/model/entity domain/model/aggregate domain/model/vo domain/service domain/factory domain/exception infrastructure/datetime
```

---

## Phase 2: ドメインモデル基礎インターフェースの実装

### 2.1 DomainObject インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/model/DomainObject.java`

```java
package com.abservice.domain.model;

/**
 * すべてのドメインオブジェクトの基底インターフェース
 *
 * <p>ドメイン駆動設計において、ドメインオブジェクト（エンティティ、値オブジェクト等）が
 * 共通して持つべき振る舞いを定義します。</p>
 *
 * @param <T> 実装クラスの型（CRTP: Curiously Recurring Template Pattern）
 */
public interface DomainObject<T extends DomainObject<T>> {
    /**
     * 等価性を検証する
     *
     * <p>ドメインオブジェクトとしての等価性を判定します。
     * 実装クラスでは、ビジネス上の同一性を表す属性に基づいて等価性を判定します。</p>
     *
     * @param other 比較対象のオブジェクト
     * @return 等価である場合はtrue、そうでない場合はfalse
     */
    boolean equivalentTo(T other);
}
```

### 2.2 ValueObject インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/model/vo/ValueObject.java`

```java
package com.abservice.domain.model.vo;

import com.abservice.domain.model.DomainObject;

/**
 * 値オブジェクトのインターフェース
 *
 * <p>ドメイン駆動設計における値オブジェクト（Value Object）を表します。
 * 値オブジェクトは以下の特性を持ちます：</p>
 * <ul>
 *   <li>不変性（Immutability）：一度生成されたら状態を変更できない</li>
 *   <li>等価性（Equality）：属性の値が同じであれば等価とみなされる</li>
 *   <li>副作用のない振る舞い（Side-Effect Free Behavior）：メソッド実行が状態を変更しない</li>
 * </ul>
 *
 * <h2>等価性の評価</h2>
 * <p>値オブジェクトの等価性は以下のように評価されます：</p>
 * <ul>
 *   <li>DomainObject型のフィールド：equivalentTo()メソッドで等価性を評価</li>
 *   <li>その他のフィールド：==演算子またはequals()で同値性を評価</li>
 *   <li>すべてのフィールドが上記の条件を満たす場合に等価とみなされる</li>
 * </ul>
 *
 * <h2>実装上の注意</h2>
 * <ul>
 *   <li>equivalentTo()メソッドは各実装クラスで明示的に実装する必要があります</li>
 *   <li>Java Recordsの使用を推奨します（Java 14+）</li>
 *   <li>不変性を保証するため、すべてのフィールドはfinalにしてください</li>
 * </ul>
 *
 * @param <T> 実装クラスの型（CRTP: Curiously Recurring Template Pattern）
 */
public interface ValueObject<T extends ValueObject<T>> extends DomainObject<T> {
}
```

### 2.3 EntityId インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/model/EntityId.java`

```java
package com.abservice.domain.model;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

/**
 * エンティティIDの基底インターフェース
 *
 * <p>型パラメータTでどのエンティティのIDかを明示的に表現する。
 * これにより、IDの取り違えをより明確に防止できる。</p>
 *
 * <p>ドメインIDはUUIDv7形式の文字列で、インフラレベルのID（DB主キー）とは分離される。
 * UUIDv7は時系列順でソート可能なため、Comparableを実装できる。</p>
 *
 * @param <T> このIDが属するエンティティの型
 */
public interface EntityId<T extends DomainObject<T>> extends Comparable<EntityId<T>> {
    /**
     * IDの実際の値（UUIDv7形式の文字列）
     */
    String value();

    /**
     * デフォルト実装：値による比較
     */
    @Override
    default int compareTo(EntityId<T> other) {
        return this.value().compareTo(other.value());
    }

    /**
     * UUID v7を生成する
     */
    static String generateUuidV7() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    /**
     * 文字列がUUID形式かどうかを検証する
     */
    static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

### 2.4 DomainEntity インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/model/entity/DomainEntity.java`

```java
package com.abservice.domain.model.entity;

import com.abservice.domain.model.DomainObject;

/**
 * ドメインエンティティのインターフェース
 *
 * <p>ドメイン駆動設計におけるエンティティ（Entity）を表します。</p>
 *
 * <h2>特性</h2>
 * <ul>
 *   <li><strong>同一性（Identity）</strong>: 一意な識別子（ID）によって区別される</li>
 *   <li><strong>可変性（Mutability）</strong>: ライフサイクルを通じて状態が変化しうる</li>
 *   <li><strong>連続性（Continuity）</strong>: 状態が変わっても同一のエンティティとして追跡される</li>
 * </ul>
 *
 * <h2>状態変更ルール：Witherパターン必須</h2>
 * <p>エンティティの状態変更は<strong>Witherメソッド</strong>で実装し、新しいインスタンスを返します。
 * Setterの使用は禁止です。</p>
 *
 * <pre>{@code
 * // ✅ 正しい実装（Java Records推奨）
 * public record Album(AlbumId id, String name, String catalogNumber) implements Aggregate<Album, AlbumId> {
 *     public Album updateCatalogNumber(String newCatalogNumber) {
 *         return new Album(this.id, this.name, newCatalogNumber);  // Witherメソッド
 *     }
 * }
 *
 * // ❌ 禁止
 * public class Album {
 *     private String catalogNumber;  // Setter禁止
 *     public void setCatalogNumber(String catalogNumber) { this.catalogNumber = catalogNumber; }
 * }
 * }</pre>
 *
 * <h2>等価性</h2>
 * <p>エンティティの等価性はIDのみで判定されます。
 * 他の属性が異なってもIDが同じなら同一エンティティです。</p>
 *
 * @param <T> 実装クラスの型（CRTP）
 * @param <ID> エンティティの識別子の型
 */
public interface DomainEntity<T extends DomainEntity<T, ID>, ID> extends DomainObject<T> {
    /**
     * エンティティの識別子を取得する
     *
     * @return エンティティの一意な識別子
     */
    ID id();

    /**
     * 等価性を検証する（デフォルト実装）
     *
     * <p>エンティティの等価性は識別子（ID）によってのみ判定されます。
     * 両方のエンティティのIDが等しい場合、等価とみなされます。</p>
     *
     * @param other 比較対象のオブジェクト
     * @return 等価である場合はtrue、そうでない場合はfalse
     */
    @Override
    default boolean equivalentTo(T other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (!this.getClass().equals(other.getClass())) {
            return false;
        }
        return this.id().equals(other.id());
    }
}
```

### 2.5 Aggregate インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/model/aggregate/Aggregate.java`

```java
package com.abservice.domain.model.aggregate;

import com.abservice.domain.model.entity.DomainEntity;

/**
 * 集約ルート（Aggregate Root）のインターフェース
 *
 * <p>ドメイン駆動設計における集約（Aggregate）の境界を定義します。</p>
 *
 * <h2>集約ルートの責務</h2>
 * <ol>
 *   <li><strong>整合性境界の定義</strong>: 一貫性を保つべきドメインオブジェクトの境界</li>
 *   <li><strong>トランザクション境界</strong>: 単一トランザクション内で永続化</li>
 *   <li><strong>永続化の単位</strong>: Repositoryは集約ルートにのみ提供</li>
 *   <li><strong>不変条件の保護</strong>: 集約全体の整合性を維持</li>
 * </ol>
 *
 * <h2>集約の設計原則</h2>
 *
 * <h3>集約ルートにすべきもの</h3>
 * <ul>
 *   <li>独立したライフサイクルを持つ</li>
 *   <li>独自のビジネスルール・整合性制約を持つ</li>
 *   <li>IDで他の集約から参照される</li>
 *   <li>直接検索・取得される必要がある</li>
 * </ul>
 *
 * <h3>集約は小さく保つ</h3>
 * <ul>
 *   <li>単一エンティティのみの集約も有効</li>
 *   <li>大きすぎる集約はパフォーマンス問題を引き起こす</li>
 *   <li>集約境界はビジネスルールとトランザクション要件で判断</li>
 * </ul>
 *
 * <h2>集約間の参照</h2>
 * <p>集約間はIDで参照します（オブジェクト参照は禁止）：</p>
 *
 * <pre>{@code
 * // ✅ 正しい: 型安全なID参照
 * public record Album(AlbumId id, DriverLicenseId driverLicenseId)
 *     implements Aggregate<Album, AlbumId> {
 * }
 *
 * // ❌ 間違い: オブジェクト参照
 * public record Album(AlbumId id, DriverLicense driverLicense)  // 集約境界違反
 *     implements Aggregate<Album, AlbumId> {
 * }
 * }</pre>
 *
 * <h2>Repositoryとの関係</h2>
 * <pre>{@code
 * // ✅ 集約ルートにRepositoryを提供
 * interface AlbumRepository extends Repository<Album, AlbumId>
 *
 * // ❌ 集約内エンティティにRepositoryは作らない
 * // interface PersonalDetailRepository  // 作成禁止
 * }</pre>
 *
 * @param <T> 実装クラスの型（CRTP）
 * @param <ID> エンティティの識別子の型
 */
public interface Aggregate<T extends Aggregate<T, ID>, ID> extends DomainEntity<T, ID> {
}
```

### 2.6 DomainException 基底クラス

**ファイル**: `backend/src/main/java/com/abservice/domain/exception/DomainException.java`

```java
package com.abservice.domain.exception;

/**
 * ドメイン層の例外基底クラス
 *
 * <p>ビジネスルール違反やドメイン制約違反を表現する例外です。</p>
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## Phase 3: 業務日付/日時の実装

### 3.1 BusinessDate クラス

**ファイル**: `backend/src/main/java/com/abservice/domain/model/vo/BusinessDate.java`

```java
package com.abservice.domain.model.vo;

import java.time.LocalDate;

/**
 * ビジネス日付値オブジェクト
 *
 * <p>システム全体で使用するビジネス日付を表現する値オブジェクト。
 * 内部表現は{@link LocalDate}を使用し、ビジネスタイムゾーン（Asia/Tokyo）での日付を表す。</p>
 *
 * <h2>使用方法</h2>
 * <ul>
 *   <li>ApplicationService/DomainServiceでは{@link com.abservice.domain.service.BusinessDateTimeProvider}のtoday()から取得</li>
 *   <li>ドメインモデル（Aggregate/Entity）では引数として受け取る</li>
 *   <li>日時が必要な場合は{@link BusinessDateTime}を使用</li>
 * </ul>
 *
 * <h2>タイムゾーン</h2>
 * <p>ビジネスタイムゾーンは{@code Asia/Tokyo}固定。
 * {@link BusinessDateTime#BUSINESS_ZONE_ID}として明示的に定義されている。</p>
 *
 * <h2>インスタンス生成方法</h2>
 * <p>文字列からの直接生成は提供していません。これは以下の理由によります:</p>
 * <ul>
 *   <li>フォーマットの多様性（ISO 8601の複数バリエーション等）は外部インターフェース設計に属する</li>
 *   <li>パースエラー時の処理はプレゼンテーション/アプリケーション層で決定すべき</li>
 *   <li>ドメインは「すでに解釈済みの日付」として{@link LocalDate}だけを扱う</li>
 * </ul>
 *
 * <p>文字列から生成する場合は、呼び出し側で以下のように変換してください:</p>
 * <pre>{@code
 * // ISO 8601形式の文字列から生成
 * String dateString = "2025-01-01";
 * BusinessDate businessDate = BusinessDate.of(LocalDate.parse(dateString));
 * }</pre>
 */
public record BusinessDate(LocalDate value) implements ValueObject<BusinessDate>, Comparable<BusinessDate> {

    /**
     * {@link LocalDate}として取得
     *
     * @return 内部の{@link LocalDate}値
     */
    public LocalDate asLocalDate() {
        return value;
    }

    /**
     * {@link BusinessDateTime}に変換（その日の00:00:00として）
     *
     * <p>ビジネスタイムゾーン（Asia/Tokyo）のその日の開始時刻（00:00:00）として
     * {@link BusinessDateTime}に変換します。</p>
     *
     * <pre>{@code
     * BusinessDate businessDate = BusinessDate.of(LocalDate.of(2025, 1, 1));
     * BusinessDateTime businessDateTime = businessDate.asBusinessDateTime();
     * // -> 2025-01-01T00:00:00 Asia/Tokyo
     * }</pre>
     *
     * @return その日の00:00:00を表す{@link BusinessDateTime}
     */
    public BusinessDateTime asBusinessDateTime() {
        return BusinessDateTime.of(value);
    }

    @Override
    public boolean equivalentTo(BusinessDate other) {
        if (other == null) {
            return false;
        }
        return this.value.equals(other.value);
    }

    @Override
    public int compareTo(BusinessDate other) {
        return value.compareTo(other.value);
    }

    /**
     * {@link LocalDate}からビジネス日付を生成
     *
     * <pre>{@code
     * // 直接生成
     * BusinessDate businessDate = BusinessDate.of(LocalDate.of(2025, 1, 1));
     *
     * // 文字列をパースして生成
     * BusinessDate businessDate = BusinessDate.of(LocalDate.parse("2025-01-01"));
     * }</pre>
     *
     * @param localDate 日付
     * @return ビジネス日付
     */
    public static BusinessDate of(LocalDate localDate) {
        return new BusinessDate(localDate);
    }

    /**
     * {@link BusinessDateTime}からビジネス日付を生成
     *
     * <p>{@link BusinessDateTime}の日付部分を抽出して{@link BusinessDate}を生成します。
     * ビジネスタイムゾーン（Asia/Tokyo）での日付が使用されます。</p>
     *
     * <pre>{@code
     * BusinessDateTime businessDateTime = BusinessDateTime.of(Instant.now());
     * BusinessDate businessDate = BusinessDate.of(businessDateTime);
     * }</pre>
     *
     * @param businessDateTime ビジネス日時
     * @return ビジネス日付
     */
    public static BusinessDate of(BusinessDateTime businessDateTime) {
        return new BusinessDate(businessDateTime.asLocalDate());
    }
}
```

### 3.2 BusinessDateTime クラス

**ファイル**: `backend/src/main/java/com/abservice/domain/model/vo/BusinessDateTime.java`

```java
package com.abservice.domain.model.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * ビジネス日時値オブジェクト
 *
 * <p>システム全体で使用するビジネス日時を表現する値オブジェクト。
 * 内部表現は{@link Instant}を使用し、ビジネスタイムゾーン（Asia/Tokyo）での
 * {@link LocalDateTime}/{@link LocalDate}への変換メソッドを提供する。</p>
 *
 * <h2>使用方法</h2>
 * <ul>
 *   <li>ApplicationService/DomainServiceでは{@link com.abservice.domain.service.BusinessDateTimeProvider}から取得</li>
 *   <li>ドメインモデル（Aggregate/Entity）では引数として受け取る</li>
 * </ul>
 *
 * <h2>タイムゾーン</h2>
 * <p>ビジネスタイムゾーンは{@code Asia/Tokyo}固定。
 * {@link #BUSINESS_ZONE_ID}定数として明示的に定義されている。</p>
 *
 * <h2>インスタンス生成方法</h2>
 * <p>文字列からの直接生成は提供していません。これは以下の理由によります:</p>
 * <ul>
 *   <li>フォーマットの多様性（ISO 8601の複数バリエーション等）は外部インターフェース設計に属する</li>
 *   <li>パースエラー時の処理はプレゼンテーション/アプリケーション層で決定すべき</li>
 *   <li>ドメインは「すでに解釈済みの時間」として{@link Instant}や{@link OffsetDateTime}だけを扱う</li>
 * </ul>
 *
 * <p>文字列から生成する場合は、呼び出し側で以下のように変換してください:</p>
 * <pre>{@code
 * // ISO 8601形式の文字列から生成
 * String dateTimeString = "2025-01-01T09:00:00+09:00";
 * BusinessDateTime businessDateTime = BusinessDateTime.of(OffsetDateTime.parse(dateTimeString));
 *
 * // UTC形式の文字列から生成
 * String utcString = "2025-01-01T00:00:00Z";
 * BusinessDateTime businessDateTime = BusinessDateTime.of(Instant.parse(utcString));
 * }</pre>
 */
public record BusinessDateTime(Instant value) implements ValueObject<BusinessDateTime>, Comparable<BusinessDateTime> {

    /**
     * ビジネスタイムゾーン: 日本標準時（Asia/Tokyo）
     *
     * <p>システム全体で使用するタイムゾーンを明示的に定義。
     * すべてのビジネス日時計算はこのタイムゾーンを基準とする。</p>
     */
    public static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Tokyo");

    /**
     * ビジネスタイムゾーンでの{@link LocalDateTime}を取得
     *
     * @return ビジネスタイムゾーン（Asia/Tokyo）での日時
     */
    public LocalDateTime asLocalDateTime() {
        return LocalDateTime.ofInstant(value, BUSINESS_ZONE_ID);
    }

    /**
     * ビジネスタイムゾーンでの{@link LocalDate}を取得
     *
     * @return ビジネスタイムゾーン（Asia/Tokyo）での日付
     */
    public LocalDate asLocalDate() {
        return asLocalDateTime().toLocalDate();
    }

    @Override
    public boolean equivalentTo(BusinessDateTime other) {
        if (other == null) {
            return false;
        }
        return this.value.equals(other.value);
    }

    @Override
    public int compareTo(BusinessDateTime other) {
        return value.compareTo(other.value);
    }

    /**
     * {@link Instant}からビジネス日時を生成
     *
     * <p>UTCベースの時刻から生成する場合に使用します。</p>
     *
     * <pre>{@code
     * // 現在時刻から生成
     * BusinessDateTime now = BusinessDateTime.of(Instant.now());
     *
     * // UTC文字列をパースして生成
     * BusinessDateTime businessDateTime = BusinessDateTime.of(Instant.parse("2025-01-01T00:00:00Z"));
     * }</pre>
     *
     * @param instant UTC時刻
     * @return ビジネス日時
     */
    public static BusinessDateTime of(Instant instant) {
        return new BusinessDateTime(instant);
    }

    /**
     * {@link OffsetDateTime}からビジネス日時を生成
     *
     * <p>タイムゾーン付き日時から生成する場合に使用します。
     * REST APIやDTO、設定ファイルからの入力変換に適しています。</p>
     *
     * <pre>{@code
     * // ISO 8601形式の文字列をパースして生成
     * String dateTimeString = "2025-01-01T09:00:00+09:00";
     * BusinessDateTime businessDateTime = BusinessDateTime.of(OffsetDateTime.parse(dateTimeString));
     *
     * // 現在時刻をオフセット付きで生成
     * BusinessDateTime businessDateTime = BusinessDateTime.of(OffsetDateTime.now());
     * }</pre>
     *
     * @param offsetDateTime タイムゾーンオフセット付き日時
     * @return ビジネス日時
     */
    public static BusinessDateTime of(OffsetDateTime offsetDateTime) {
        return new BusinessDateTime(offsetDateTime.toInstant());
    }

    /**
     * {@link LocalDateTime}からビジネス日時を生成
     *
     * <p>指定された{@link LocalDateTime}をビジネスタイムゾーン（Asia/Tokyo）として解釈し、
     * {@link Instant}に変換します。</p>
     *
     * <p><strong>注意</strong>: {@link LocalDateTime}はタイムゾーン情報を持たないため、
     * 必ずビジネスタイムゾーン（Asia/Tokyo）として解釈されます。</p>
     *
     * <pre>{@code
     * // 日本時間の2025年1月1日9時として生成
     * BusinessDateTime businessDateTime = BusinessDateTime.of(LocalDateTime.of(2025, 1, 1, 9, 0, 0));
     * }</pre>
     *
     * @param localDateTime タイムゾーンなし日時（Asia/Tokyoとして解釈）
     * @return ビジネス日時
     */
    public static BusinessDateTime of(LocalDateTime localDateTime) {
        return new BusinessDateTime(localDateTime.atZone(BUSINESS_ZONE_ID).toInstant());
    }

    /**
     * {@link LocalDate}からビジネス日時を生成（その日の00:00:00として）
     *
     * <p>指定された{@link LocalDate}をビジネスタイムゾーン（Asia/Tokyo）の
     * その日の開始時刻（00:00:00）として解釈します。</p>
     *
     * <p><strong>注意</strong>: {@link LocalDate}はタイムゾーン情報を持たないため、
     * 必ずビジネスタイムゾーン（Asia/Tokyo）の00:00:00として解釈されます。</p>
     *
     * <pre>{@code
     * // 日本時間の2025年1月1日0時0分0秒として生成
     * BusinessDateTime businessDateTime = BusinessDateTime.of(LocalDate.of(2025, 1, 1));
     * }</pre>
     *
     * @param localDate タイムゾーンなし日付（Asia/Tokyoの00:00:00として解釈）
     * @return ビジネス日時
     */
    public static BusinessDateTime of(LocalDate localDate) {
        return of(localDate.atStartOfDay());
    }
}
```

### 3.3 BusinessDateTimeProvider インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/service/BusinessDateTimeProvider.java`

```java
package com.abservice.domain.service;

import com.abservice.domain.model.vo.BusinessDate;
import com.abservice.domain.model.vo.BusinessDateTime;
import io.smallrye.mutiny.Uni;

/**
 * ビジネス日時プロバイダー
 *
 * <p>システム全体で使用するビジネス日時を提供するドメインサービス。
 * テスト容易性を確保するため、現在時刻の取得を抽象化する。</p>
 *
 * <h2>使用ルール</h2>
 * <ul>
 *   <li><strong>ApplicationService/DomainServiceにのみinjectする</strong></li>
 *   <li>ドメインモデル（Aggregate/Entity）では、引数として{@link BusinessDateTime}または{@link BusinessDate}インスタンスを受け取る</li>
 *   <li>Providerを直接ドメインモデルに渡さない</li>
 * </ul>
 *
 * <h2>非同期設計</h2>
 * <p>DomainServiceは一貫性のため、すべてのメソッドが{@link Uni}を返します。
 * 純粋な計算であっても{@code Uni.createFrom().item(...)}でラップします。</p>
 *
 * <h2>実装</h2>
 * <ul>
 *   <li>{@link com.abservice.infrastructure.datetime.SystemBusinessDateTimeProvider}: 本番用、リアルタイムの現在時刻を返す</li>
 *   <li>ConfigurableBusinessDateTimeProvider: dev/test用、設定ファイルで指定した時刻を返す（未実装）</li>
 *   <li>FixedBusinessDateTimeProvider: UT用、コンストラクタで指定した固定時刻を返す（未実装）</li>
 * </ul>
 */
public interface BusinessDateTimeProvider extends DomainService {
    /**
     * 現在のビジネス日時を取得
     *
     * @return 現在のビジネス日時を含むUni
     */
    Uni<BusinessDateTime> now();

    /**
     * 現在のビジネス日付を取得
     *
     * <p>デフォルト実装では{@link #now()}から日付部分を抽出して返します。
     * 各実装クラスでオーバーライドする必要はありません。</p>
     *
     * @return 現在のビジネス日付を含むUni
     */
    default Uni<BusinessDate> today() {
        return now().map(BusinessDate::of);
    }
}
```

### 3.4 DomainService インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/service/DomainService.java`

```java
package com.abservice.domain.service;

/**
 * ドメインサービスの基底インターフェース
 *
 * <h2>目的</h2>
 * <p>複数の集約にまたがるビジネスロジックや、単一の集約/値オブジェクトに属さないロジックを実装します。</p>
 *
 * <h2>適用パターン</h2>
 * <ol>
 *   <li><strong>複数集約の協調</strong>: 複数の集約を組み合わせたビジネスルール</li>
 *   <li><strong>一意性チェック</strong>: メールアドレスの重複確認など</li>
 *   <li><strong>複雑な計算</strong>: 特定の集約に属さない計算ロジック</li>
 *   <li><strong>外部システム連携</strong>: インフラ層へのインターフェース</li>
 * </ol>
 *
 * <h2>使用例</h2>
 * <pre>{@code
 * // 一意性チェック
 * @ApplicationScoped
 * public class CatalogNumberUniquenessService implements DomainService {
 *     private final AlbumRepository albumRepository;
 *
 *     public Uni<Boolean> isCatalogNumberUnique(String catalogNumber, AlbumId excludeId) {
 *         return albumRepository.findByCatalogNumber(catalogNumber)
 *             .onItem().transform(album ->
 *                 album == null || album.id().equals(excludeId)
 *             );
 *     }
 * }
 *
 * // 複数集約の協調
 * @ApplicationScoped
 * public class ArticleAlbumLinkService implements DomainService {
 *     public Uni<Void> assignCarToAlbum(Album album, Car car) {
 *         album.assignCar(car.id());  // CarIdを渡す（型安全）
 *         car.assignOwner(album.id());  // AlbumIdを渡す（型安全）
 *         return Uni.createFrom().voidItem();
 *     }
 * }
 * }</pre>
 *
 * <h2>原則</h2>
 * <ol>
 *   <li><strong>ステートレス</strong>: 内部状態を持たない</li>
 *   <li><strong>明示的な命名</strong>: ビジネスロジックが分かる名前</li>
 *   <li><strong>必要最小限</strong>: 集約で実装できる場合は集約に</li>
 *   <li><strong>リアクティブ</strong>: すべてUni&lt;T&gt;で非同期実行</li>
 * </ol>
 */
public interface DomainService {
}
```

### 3.5 SystemBusinessDateTimeProvider 実装

**ファイル**: `backend/src/main/java/com/abservice/infrastructure/datetime/SystemBusinessDateTimeProvider.java`

```java
package com.abservice.infrastructure.datetime;

import com.abservice.domain.model.vo.BusinessDateTime;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * システム時刻ベースのビジネス日時プロバイダー
 *
 * <p>本番環境で使用するデフォルトの実装。
 * システムの現在時刻（リアルタイム）を返す。</p>
 */
@ApplicationScoped
public class SystemBusinessDateTimeProvider implements BusinessDateTimeProvider {
    @Override
    public Uni<BusinessDateTime> now() {
        return Uni.createFrom().item(() -> BusinessDateTime.of(Instant.now()));
    }
}
```

---

## Phase 4: DomainService/Factoryの実装

### 4.1 Factory インターフェース

**ファイル**: `backend/src/main/java/com/abservice/domain/factory/Factory.java`

```java
package com.abservice.domain.factory;

import com.abservice.domain.model.aggregate.Aggregate;
import io.smallrye.mutiny.Uni;

/**
 * ファクトリの基底インターフェース
 *
 * <h2>目的</h2>
 * <p>複雑な集約の生成ロジックをカプセル化します。</p>
 *
 * <h2>適用パターン</h2>
 * <ol>
 *   <li><strong>複雑な初期化</strong>: 複数のエンティティと値オブジェクトを組み合わせた集約生成</li>
 *   <li><strong>外部依存の生成</strong>: リポジトリや他の集約を参照した生成</li>
 *   <li><strong>不変条件の保証</strong>: 生成時に複雑なバリデーションが必要</li>
 *   <li><strong>再構成</strong>: 永続化されたデータから集約を再構成</li>
 * </ol>
 *
 * <h2>使用例</h2>
 * <pre>{@code
 * @ApplicationScoped
 * public class AlbumFactory implements Factory<Album, AlbumFactory.CreateParams> {
 *
 *     private final CatalogNumberUniquenessService catalogNumberUniquenessService;
 *
 *     public record CreateParams(
 *         String name,
 *         String catalogNumber,
 *         String phoneNumber
 *     ) implements Factory.Params {}
 *
 *     @Override
 *     public Uni<Album> create(CreateParams params) {
 *         AlbumTitle catalogNumber = AlbumTitle.of(params.catalogNumber());
 *
 *         return catalogNumberUniquenessService.isCatalogNumberUnique(catalogNumber, null)
 *             .onItem().transform(isUnique -> {
 *                 if (!isUnique) {
 *                     throw new CatalogNumberAlreadyExistsException(catalogNumber);
 *                 }
 *
 *                 return Album.create(
 *                     name: params.name(),
 *                     catalogNumber: catalogNumber,
 *                     phoneNumber: PhoneNumber.of(params.phoneNumber())
 *                 );
 *             });
 *     }
 * }
 * }</pre>
 *
 * <h2>原則</h2>
 * <ol>
 *   <li><strong>生成に集中</strong>: 集約の生成ロジックのみ</li>
 *   <li><strong>不変条件の保証</strong>: 生成時に必ずバリデーション</li>
 *   <li><strong>依存の注入</strong>: DomainServiceやRepositoryをコンストラクタ注入</li>
 *   <li><strong>リアクティブ</strong>: すべてUni&lt;T&gt;で非同期実行</li>
 * </ol>
 *
 * @param <T> 生成する集約の型
 * @param <P> 生成パラメータの型
 */
public interface Factory<T extends Aggregate<T, ?>, P extends Factory.Params> {
    /**
     * 集約を生成する
     *
     * @param params 生成パラメータ
     * @return 生成された集約を含むUni
     */
    Uni<T> create(P params);

    /**
     * ファクトリのパラメータマーカーインターフェース
     */
    interface Params {
    }
}
```

---

## Phase 5: 動作確認とテスト

### 5.1 BusinessDateTimeのテスト

**ファイル**: `backend/src/test/java/com/abservice/domain/model/vo/BusinessDateTimeTest.java`

```java
package com.abservice.domain.model.vo;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateTimeTest {

    @Test
    void testCreateFromInstant() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        BusinessDateTime businessDateTime = BusinessDateTime.of(instant);

        assertThat(businessDateTime.value()).isEqualTo(instant);
    }

    @Test
    void testAsLocalDateTime() {
        // UTC 2025-01-01 00:00:00 = JST 2025-01-01 09:00:00
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        BusinessDateTime businessDateTime = BusinessDateTime.of(instant);

        LocalDateTime localDateTime = businessDateTime.asLocalDateTime();
        assertThat(localDateTime.getYear()).isEqualTo(2025);
        assertThat(localDateTime.getMonthValue()).isEqualTo(1);
        assertThat(localDateTime.getDayOfMonth()).isEqualTo(1);
        assertThat(localDateTime.getHour()).isEqualTo(9); // JST = UTC+9
    }

    @Test
    void testEquivalentTo() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        BusinessDateTime dt1 = BusinessDateTime.of(instant);
        BusinessDateTime dt2 = BusinessDateTime.of(instant);

        assertThat(dt1.equivalentTo(dt2)).isTrue();
    }

    @Test
    void testComparable() {
        BusinessDateTime dt1 = BusinessDateTime.of(Instant.parse("2025-01-01T00:00:00Z"));
        BusinessDateTime dt2 = BusinessDateTime.of(Instant.parse("2025-01-02T00:00:00Z"));

        assertThat(dt1.compareTo(dt2)).isLessThan(0);
        assertThat(dt2.compareTo(dt1)).isGreaterThan(0);
    }
}
```

### 5.2 BusinessDateのテスト

**ファイル**: `backend/src/test/java/com/abservice/domain/model/vo/BusinessDateTest.java`

```java
package com.abservice.domain.model.vo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateTest {

    @Test
    void testCreateFromLocalDate() {
        LocalDate localDate = LocalDate.of(2025, 1, 1);
        BusinessDate businessDate = BusinessDate.of(localDate);

        assertThat(businessDate.value()).isEqualTo(localDate);
    }

    @Test
    void testAsBusinessDateTime() {
        LocalDate localDate = LocalDate.of(2025, 1, 1);
        BusinessDate businessDate = BusinessDate.of(localDate);

        BusinessDateTime businessDateTime = businessDate.asBusinessDateTime();
        assertThat(businessDateTime.asLocalDate()).isEqualTo(localDate);
    }

    @Test
    void testEquivalentTo() {
        LocalDate localDate = LocalDate.of(2025, 1, 1);
        BusinessDate bd1 = BusinessDate.of(localDate);
        BusinessDate bd2 = BusinessDate.of(localDate);

        assertThat(bd1.equivalentTo(bd2)).isTrue();
    }

    @Test
    void testComparable() {
        BusinessDate bd1 = BusinessDate.of(LocalDate.of(2025, 1, 1));
        BusinessDate bd2 = BusinessDate.of(LocalDate.of(2025, 1, 2));

        assertThat(bd1.compareTo(bd2)).isLessThan(0);
        assertThat(bd2.compareTo(bd1)).isGreaterThan(0);
    }
}
```

### 5.3 SystemBusinessDateTimeProviderのテスト

**ファイル**: `backend/src/test/java/com/abservice/infrastructure/datetime/SystemBusinessDateTimeProviderTest.java`

```java
package com.abservice.infrastructure.datetime;

import com.abservice.domain.model.vo.BusinessDate;
import com.abservice.domain.model.vo.BusinessDateTime;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SystemBusinessDateTimeProviderTest {

    @Inject
    SystemBusinessDateTimeProvider provider;

    @Test
    void testNow() {
        Instant before = Instant.now();
        BusinessDateTime businessDateTime = provider.now().await().indefinitely();
        Instant after = Instant.now();

        assertThat(businessDateTime.value()).isBetween(before, after);
    }

    @Test
    void testToday() {
        BusinessDate businessDate = provider.today().await().indefinitely();
        BusinessDate expected = BusinessDate.of(
            Instant.now().atZone(BusinessDateTime.BUSINESS_ZONE_ID).toLocalDate()
        );

        assertThat(businessDate.value()).isEqualTo(expected.value());
    }
}
```

### 5.4 ビルドとテスト実行

```bash
cd backend
./gradlew clean build
./gradlew test
```

---

## Phase 6: ドキュメント整備

### 6.1 ARCHITECTURE.mdの更新

**ファイル**: `docs/ARCHITECTURE.md`

以下のセクションを追加：

```markdown
## ドメインモデル設計

### ドメイン駆動設計（DDD）の採用

ABServiceのバックエンドは、ドメイン駆動設計（DDD）の原則に基づいて設計されています。

#### ドメインオブジェクト階層

```
DomainObject<T>
├── ValueObject<T>              // 値で識別されるオブジェクト
└── DomainEntity<T, ID>         // IDで識別されるオブジェクト
    └── Aggregate<T, ID>        // 永続化境界を持つエンティティ
```

#### 値オブジェクト（Value Object）

- **不変性**: 一度生成されたら状態を変更できない
- **等価性**: すべての属性が等しければ等価
- **副作用なし**: メソッド実行が状態を変更しない
- **実装**: Java Recordsを推奨

#### エンティティ（Entity）

- **同一性**: 一意な識別子（ID）によって区別
- **Witherパターン**: 状態変更時は新しいインスタンスを返す
- **Setterの禁止**: 不変性を保つ

#### 集約（Aggregate）

- **整合性境界**: トランザクション境界
- **永続化単位**: Repositoryは集約ルートにのみ提供
- **ID参照**: 集約間はIDで参照（オブジェクト参照禁止）

### 業務日付/日時

#### BusinessDate / BusinessDateTime

- **タイムゾーン**: Asia/Tokyo固定
- **内部表現**: LocalDate / Instant
- **テスト容易性**: BusinessDateTimeProviderで抽象化

#### BusinessDateTimeProvider

- **役割**: 現在時刻の取得を抽象化
- **使用箇所**: ApplicationService/DomainServiceのみ
- **実装**:
  - `SystemBusinessDateTimeProvider`: 本番用（リアルタイム）
  - テスト用実装（固定時刻）も追加可能

### DomainServiceとFactory

#### DomainService

- **適用パターン**:
  - 複数集約の協調
  - 一意性チェック
  - 複雑な計算
- **原則**: ステートレス、リアクティブ（Uni<T>返却）

#### Factory

- **役割**: 複雑な集約の生成ロジックをカプセル化
- **適用**: 外部依存を伴う生成、複雑なバリデーション
```

### 6.2 README.mdの更新

**ファイル**: `README.md`

パッケージ構造のセクションを追加：

```markdown
## パッケージ構造

```
com.abservice/
├── domain/                      # ドメイン層
│   ├── model/                   # ドメインモデル
│   │   ├── DomainObject.java
│   │   ├── EntityId.java
│   │   ├── entity/              # エンティティ
│   │   ├── aggregate/           # 集約
│   │   └── vo/                  # 値オブジェクト
│   ├── service/                 # ドメインサービス
│   ├── factory/                 # ファクトリ
│   ├── repository/              # リポジトリインターフェース
│   └── exception/               # ドメイン例外
├── application/                 # アプリケーション層
│   ├── service/                 # アプリケーションサービス
│   └── dto/                     # データ転送オブジェクト
├── infrastructure/              # インフラ層
│   ├── persistence/             # 永続化実装
│   └── datetime/                # 日時プロバイダー実装
└── presentation/                # プレゼンテーション層
    └── rest/                    # RESTエンドポイント
```
```

---

## 完了チェックリスト

### Phase 1: 事前準備
- [ ] build.gradleに依存関係追加
- [ ] パッケージ構造作成

### Phase 2: 基礎インターフェース
- [ ] DomainObject実装
- [ ] ValueObject実装
- [ ] EntityId実装
- [ ] DomainEntity実装
- [ ] Aggregate実装
- [ ] DomainException実装

### Phase 3: 業務日付
- [ ] BusinessDate実装
- [ ] BusinessDateTime実装
- [ ] BusinessDateTimeProvider実装
- [ ] DomainService実装
- [ ] SystemBusinessDateTimeProvider実装

### Phase 4: サービス/ファクトリ
- [ ] Factory実装

### Phase 5: テスト
- [ ] BusinessDateTimeのテスト
- [ ] BusinessDateのテスト
- [ ] SystemBusinessDateTimeProviderのテスト
- [ ] ビルド成功確認

### Phase 6: ドキュメント
- [ ] ARCHITECTURE.md更新
- [ ] README.md更新

---

## トラブルシューティング

### コンパイルエラー: java-uuid-generator not found

```bash
./gradlew clean build --refresh-dependencies
```

### テスト失敗: BusinessDateTimeProviderがInjectできない

`SystemBusinessDateTimeProvider`に`@ApplicationScoped`アノテーションが付いているか確認。

### タイムゾーンの問題

すべての業務日時計算は`BusinessDateTime.BUSINESS_ZONE_ID`（Asia/Tokyo）を使用。
システムタイムゾーンに依存しない実装を徹底。

---

## 次のステップ

1. **具体的なドメインモデルの実装**: CircleMemberなどのAggregateを実装
2. **Repositoryパターンの実装**: Reactive Panacheを使用
3. **ApplicationServiceの実装**: CommandService/QueryServiceパターン
4. **テスト用BusinessDateTimeProviderの追加**: 固定時刻を返す実装

---

## 参考リンク

- [ABService DOMAIN_MODEL_CHARTER.md](https://github.com/lambig/ABService/blob/main/docs/DOMAIN_MODEL_CHARTER.md)
- [ABService CODING_GUIDELINES.md](https://github.com/lambig/ABService/blob/main/docs/CODING_GUIDELINES.md)
- [Quarkus Reactive Architecture](https://quarkus.io/guides/quarkus-reactive-architecture)
- [SmallRye Mutiny](https://smallrye.io/smallrye-mutiny/)
