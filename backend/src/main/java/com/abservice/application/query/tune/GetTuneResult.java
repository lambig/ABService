package com.abservice.application.query.tune;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.tune.model.TuneView;

/**
 * チューン詳細照会の結果
 *
 * <p>
 * 「未存在」を例外ではなく正常な結果の一種として型で表現します（sealed）。presentation 層は各バリアントで switch
 * し、{@link Found} を 200、{@link NotFound} を 404 に対応づけます。
 * </p>
 */
public sealed interface GetTuneResult extends QueryService.Result
        permits GetTuneResult.Found, GetTuneResult.NotFound {

    /**
     * チューンが見つかった結果（→ 200）
     *
     * @param tune
     *            チューンの Read Model
     */
    record Found(TuneView tune) implements GetTuneResult {
    }

    /**
     * チューンが見つからなかった結果（→ 404）
     */
    record NotFound() implements GetTuneResult {
    }
}
