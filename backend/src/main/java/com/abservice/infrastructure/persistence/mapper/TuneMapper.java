package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.infrastructure.persistence.entity.TuneEntity;

/**
 * Tune Mapper
 *
 * <p>
 * TuneドメインモデルとTuneEntityの相互変換を担当します。
 * </p>
 */
public class TuneMapper {

    private TuneMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            TuneEntity
     * @return Tune
     */
    public static Tune toDomain(TuneEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Tune(new Tune.Id(entity.getDomainId()), new TuneTitle(entity.getTitle()),
                TuneKind.valueOf(entity.getTuneKind()),
                entity.getDefaultComposerCredit() != null ? new Credit(entity.getDefaultComposerCredit()) : null,
                entity.getDefaultArrangerCredit() != null ? new Credit(entity.getDefaultArrangerCredit()) : null,
                entity.getOriginalWorkTitle(), entity.getOriginalWorkCredit(), entity.getTuneType(),
                entity.getDefaultKey(), entity.getDefaultTempo());
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param tune
     *            Tune
     * @return TuneEntity
     */
    public static TuneEntity toEntity(Tune tune) {
        if (tune == null) {
            return null;
        }

        var tuneEntity = new TuneEntity();
        tuneEntity.setDomainId(tune.id().value());
        tuneEntity.setTitle(tune.title().value());
        tuneEntity.setTuneKind(tune.tuneKind().name());
        tuneEntity.setDefaultComposerCredit(
                tune.defaultComposerCredit() != null ? tune.defaultComposerCredit().value() : null);
        tuneEntity.setDefaultArrangerCredit(
                tune.defaultArrangerCredit() != null ? tune.defaultArrangerCredit().value() : null);
        tuneEntity.setOriginalWorkTitle(tune.originalWorkTitle());
        tuneEntity.setOriginalWorkCredit(tune.originalWorkCredit());
        tuneEntity.setTuneType(tune.tuneType());
        tuneEntity.setDefaultKey(tune.defaultKey());
        tuneEntity.setDefaultTempo(tune.defaultTempo());

        return tuneEntity;
    }
}
