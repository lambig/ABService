package com.abservice.application.query;

import io.smallrye.mutiny.Uni;

/**
 * クエリサービスの基底インターフェース
 *
 * <p>
 * CQRS（Command Query Responsibility Segregation）パターンのQuery側を表現します。
 * </p>
 *
 * <h2>責務</h2>
 * <ol>
 * <li><strong>照会処理の実行</strong>: データの検索と集計</li>
 * <li><strong>ReadModelの取得</strong>: 読み取り専用モデルを返す</li>
 * <li><strong>複雑な検索</strong>: 集約横断や複数条件の検索</li>
 * <li><strong>パフォーマンス最適化</strong>: 読み取り専用の最適化クエリ</li>
 * </ol>
 *
 * <h2>Command vs Query</h2>
 * <table border="1">
 * <tr>
 * <th>観点</th>
 * <th>Command (CommandService)</th>
 * <th>Query (QueryService)</th>
 * </tr>
 * <tr>
 * <td>目的</td>
 * <td>状態を変更</td>
 * <td>データを照会</td>
 * </tr>
 * <tr>
 * <td>返値</td>
 * <td>実行結果DTO</td>
 * <td>照会結果DTO</td>
 * </tr>
 * <tr>
 * <td>副作用</td>
 * <td>あり（更新・削除等）</td>
 * <td>なし（読み取りのみ）</td>
 * </tr>
 * </table>
 *
 * <h2>参照実装</h2>
 *
 * <p>
 * {@code application.query.article} の Get/List 各サービスと Query/Result、Read Model。
 * </p>
 *
 * <h2>原則</h2>
 * <ol>
 * <li><strong>副作用なし</strong>: データを変更しない</li>
 * <li><strong>ReadModel使用</strong>: 読み取り専用モデルから取得（DataSource経由）</li>
 * <li><strong>単一責任</strong>: 一つのクエリは一つの検索</li>
 * <li><strong>リアクティブ</strong>: すべてUni&lt;T&gt;で非同期実行</li>
 * </ol>
 *
 * @param <Q>
 *            クエリの型
 * @param <R>
 *            結果の型
 * @see com.abservice.application.service.CommandService 更新側のサービス
 * @see <a href=
 *      "https://github.com/lambig/ABService/blob/main/docs/CODING_GUIDELINES.md#queryservice">実装詳細</a>
 */
public interface QueryService<Q extends QueryService.Query, R extends QueryService.Result> {
    /**
     * クエリを実行
     *
     * @param query
     *            検索条件
     * @return 検索結果
     */
    Uni<R> query(Q query);

    /**
     * クエリ（検索条件）のマーカーインターフェース
     */
    interface Query {
    }

    /**
     * 結果（照会結果）のマーカーインターフェース
     */
    interface Result {
    }
}
