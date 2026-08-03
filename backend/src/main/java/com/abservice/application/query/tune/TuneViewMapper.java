package com.abservice.application.query.tune;

import com.abservice.application.query.tune.model.TuneView;
import com.abservice.infrastructure.persistence.entity.TuneTableRecord;

/**
 * チューンエンティティから Read Model（{@link TuneView}）への変換
 *
 * <p>
 * CQRS の Read 側マッパー。{@code infrastructure.persistence.datasource} が返す
 * {@link TuneTableRecord} を照会結果 DTO へ平坦化します。ドメインモデルを経由しません。
 * </p>
 */
final class TuneViewMapper {

    private TuneViewMapper() {
    }

    /**
     * エンティティを Read Model へ変換します。
     *
     * @param entity
     *            チューンエンティティ
     * @return チューンの Read Model
     */
    static TuneView toView(TuneTableRecord entity) {
        return new TuneView(
                entity.getDomainId(),
                entity.getTitle(),
                entity.getTuneKind(),
                entity.getDefaultComposerCredit(),
                entity.getDefaultArrangerCredit(),
                entity.getOriginalWorkTitle(),
                entity.getOriginalWorkCredit(),
                entity.getTuneType(),
                entity.getDefaultKey(),
                entity.getDefaultTempo());
    }
}
