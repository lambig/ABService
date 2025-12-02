package com.abservice.domain.model.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * ビジネス日時値オブジェクト
 *
 * <p>
 * システム全体で使用するビジネス日時を表現する値オブジェクト。
 * 内部表現は{@link Instant}を使用し、ビジネスタイムゾーン（Asia/Tokyo）での
 * {@link LocalDateTime}/{@link LocalDate}への変換メソッドを提供する。
 * </p>
 *
 * <h2>使用方法</h2>
 * <ul>
 * <li>ApplicationService/DomainServiceでは{@link com.abservice.domain.service.BusinessDateTimeProvider}から取得</li>
 * <li>ドメインモデル（Aggregate/Entity）では引数として受け取る</li>
 * </ul>
 *
 * <h2>タイムゾーン</h2>
 * <p>
 * ビジネスタイムゾーンは{@code Asia/Tokyo}固定。 {@link #BUSINESS_ZONE_ID}定数として明示的に定義されている。
 * </p>
 *
 * <h2>インスタンス生成方法</h2>
 * <p>
 * 文字列からの直接生成は提供していません。これは以下の理由によります:
 * </p>
 * <ul>
 * <li>フォーマットの多様性（ISO 8601の複数バリエーション等）は外部インターフェース設計に属する</li>
 * <li>パースエラー時の処理はプレゼンテーション/アプリケーション層で決定すべき</li>
 * <li>ドメインは「すでに解釈済みの時間」として{@link Instant}や{@link OffsetDateTime}だけを扱う</li>
 * </ul>
 *
 * <p>
 * 文字列から生成する場合は、呼び出し側で以下のように変換してください:
 * </p>
 *
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
	 * <p>
	 * システム全体で使用するタイムゾーンを明示的に定義。 すべてのビジネス日時計算はこのタイムゾーンを基準とする。
	 * </p>
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
	 * <p>
	 * UTCベースの時刻から生成する場合に使用します。
	 * </p>
	 *
	 * <pre>{@code
	 * // 現在時刻から生成
	 * BusinessDateTime now = BusinessDateTime.of(Instant.now());
	 *
	 * // UTC文字列をパースして生成
	 * BusinessDateTime businessDateTime = BusinessDateTime.of(Instant.parse("2025-01-01T00:00:00Z"));
	 * }</pre>
	 *
	 * @param instant
	 *            UTC時刻
	 * @return ビジネス日時
	 */
	public static BusinessDateTime of(Instant instant) {
		return new BusinessDateTime(instant);
	}

	/**
	 * {@link OffsetDateTime}からビジネス日時を生成
	 *
	 * <p>
	 * タイムゾーン付き日時から生成する場合に使用します。 REST APIやDTO、設定ファイルからの入力変換に適しています。
	 * </p>
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
	 * @param offsetDateTime
	 *            タイムゾーンオフセット付き日時
	 * @return ビジネス日時
	 */
	public static BusinessDateTime of(OffsetDateTime offsetDateTime) {
		return new BusinessDateTime(offsetDateTime.toInstant());
	}

	/**
	 * {@link LocalDateTime}からビジネス日時を生成
	 *
	 * <p>
	 * 指定された{@link LocalDateTime}をビジネスタイムゾーン（Asia/Tokyo）として解釈し、
	 * {@link Instant}に変換します。
	 * </p>
	 *
	 * <p>
	 * <strong>注意</strong>: {@link LocalDateTime}はタイムゾーン情報を持たないため、
	 * 必ずビジネスタイムゾーン（Asia/Tokyo）として解釈されます。
	 * </p>
	 *
	 * <pre>{@code
	 * // 日本時間の2025年1月1日9時として生成
	 * BusinessDateTime businessDateTime = BusinessDateTime.of(LocalDateTime.of(2025, 1, 1, 9, 0, 0));
	 * }</pre>
	 *
	 * @param localDateTime
	 *            タイムゾーンなし日時（Asia/Tokyoとして解釈）
	 * @return ビジネス日時
	 */
	public static BusinessDateTime of(LocalDateTime localDateTime) {
		return new BusinessDateTime(localDateTime.atZone(BUSINESS_ZONE_ID).toInstant());
	}

	/**
	 * {@link LocalDate}からビジネス日時を生成（その日の00:00:00として）
	 *
	 * <p>
	 * 指定された{@link LocalDate}をビジネスタイムゾーン（Asia/Tokyo）の その日の開始時刻（00:00:00）として解釈します。
	 * </p>
	 *
	 * <p>
	 * <strong>注意</strong>: {@link LocalDate}はタイムゾーン情報を持たないため、
	 * 必ずビジネスタイムゾーン（Asia/Tokyo）の00:00:00として解釈されます。
	 * </p>
	 *
	 * <pre>{@code
	 * // 日本時間の2025年1月1日0時0分0秒として生成
	 * BusinessDateTime businessDateTime = BusinessDateTime.of(LocalDate.of(2025, 1, 1));
	 * }</pre>
	 *
	 * @param localDate
	 *            タイムゾーンなし日付（Asia/Tokyoの00:00:00として解釈）
	 * @return ビジネス日時
	 */
	public static BusinessDateTime of(LocalDate localDate) {
		return of(localDate.atStartOfDay());
	}
}
