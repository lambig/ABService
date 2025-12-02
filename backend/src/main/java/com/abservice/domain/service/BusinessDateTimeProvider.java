package com.abservice.domain.service;

import com.abservice.domain.model.vo.BusinessDate;
import com.abservice.domain.model.vo.BusinessDateTime;
import io.smallrye.mutiny.Uni;

/**
 * ビジネス日時プロバイダー
 *
 * <p>
 * システム全体で使用するビジネス日時を提供するドメインサービス。 テスト容易性を確保するため、現在時刻の取得を抽象化する。
 * </p>
 *
 * <h2>使用ルール</h2>
 * <ul>
 * <li><strong>ApplicationService/DomainServiceにのみinjectする</strong></li>
 * <li>ドメインモデル（Aggregate/Entity）では、引数として{@link BusinessDateTime}または{@link BusinessDate}インスタンスを受け取る</li>
 * <li>Providerを直接ドメインモデルに渡さない</li>
 * </ul>
 *
 * <h2>非同期設計</h2>
 * <p>
 * DomainServiceは一貫性のため、すべてのメソッドが{@link Uni}を返します。
 * 純粋な計算であっても{@code Uni.createFrom().item(...)}でラップします。
 * </p>
 *
 * <h2>実装</h2>
 * <ul>
 * <li>{@link com.abservice.infrastructure.datetime.SystemBusinessDateTimeProvider}:
 * 本番用、リアルタイムの現在時刻を返す</li>
 * <li>ConfigurableBusinessDateTimeProvider:
 * dev/test用、設定ファイルで指定した時刻を返す（未実装）</li>
 * <li>FixedBusinessDateTimeProvider: UT用、コンストラクタで指定した固定時刻を返す（未実装）</li>
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
	 * <p>
	 * デフォルト実装では{@link #now()}から日付部分を抽出して返します。 各実装クラスでオーバーライドする必要はありません。
	 * </p>
	 *
	 * @return 現在のビジネス日付を含むUni
	 */
	default Uni<BusinessDate> today() {
		return now().map(BusinessDate::of);
	}
}
