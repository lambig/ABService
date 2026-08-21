package com.abservice.application.service;

import io.smallrye.mutiny.Uni;

/**
 * コマンドサービスの基底インターフェース
 *
 * <p>
 * CQRS（Command Query Responsibility Segregation）パターンのCommand側を表現します。
 * </p>
 *
 * <h2>責務</h2>
 * <ol>
 * <li><strong>ユースケースの実行</strong>: ビジネスプロセスの流れを制御</li>
 * <li><strong>トランザクション管理</strong>: トランザクション境界を定義（{@code @WithTransaction}）</li>
 * <li><strong>ドメイン層の調整</strong>: リポジトリ、ドメインサービス、集約を協調</li>
 * <li><strong>入出力の変換</strong>: DTOとドメインオブジェクトの変換</li>
 * </ol>
 *
 * <h2>参照実装</h2>
 *
 * <p>
 * {@code application.service.article} の Create/Update/Delete 各サービスと
 * Input/Output。
 * </p>
 *
 * <h2>原則</h2>
 * <ol>
 * <li><strong>薄く保つ</strong>: ビジネスロジックはドメイン層に</li>
 * <li><strong>単一責任</strong>: 一つのサービスは一つのユースケース</li>
 * <li><strong>DTO変換</strong>: ドメインオブジェクトを直接公開しない</li>
 * <li><strong>リアクティブ</strong>: すべてUni&lt;T&gt;で非同期実行</li>
 * </ol>
 *
 * @param <I>
 *            入力の型
 * @param <O>
 *            出力の型
 * @see QueryService 照会側のサービス
 * @see <a href=
 *      "https://github.com/lambig/ABService/blob/main/docs/CODING_GUIDELINES.md#commandservice">実装詳細</a>
 */
public interface CommandService<I extends CommandService.Input, O extends CommandService.Output> {
    /**
     * ユースケースを実行
     *
     * @param input
     *            入力データ
     * @return 出力データ
     */
    Uni<O> execute(I input);

    /**
     * 入力DTOのマーカーインターフェース
     */
    interface Input {
    }

    /**
     * 出力DTOのマーカーインターフェース
     */
    interface Output {
    }
}
