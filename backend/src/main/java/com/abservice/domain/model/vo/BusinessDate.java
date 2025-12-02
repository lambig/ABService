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
