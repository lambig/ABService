package com.abservice.infrastructure.persistence.mapper;

import java.util.Optional;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.infrastructure.persistence.entity.TuneTableRecord;

/**
 * Tune Mapper
 *
 * <p>
 * TuneドメインモデルとTuneTableRecordの相互変換を担当します。
 * </p>
 */
public final class TuneMapper {

    private TuneMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            TuneTableRecord
     * @return Tune
     */
    public static Tune toDomain(TuneTableRecord entity) {
        return Tune.reconstruct(
                new Tune.Id(entity.getDomainId()),
                new TuneTitle(entity.getTitle()),
                TuneKind.valueOf(entity.getTuneKind()),
                Optional.ofNullable(entity.getDefaultComposerCredit())
                        .map(Credit::new)
                        .orElse(null),
                Optional.ofNullable(entity.getDefaultArrangerCredit())
                        .map(Credit::new)
                        .orElse(null),
                entity.getOriginalWorkTitle(),
                entity.getOriginalWorkCredit(),
                entity.getTuneType(),
                entity.getDefaultKey(),
                entity.getDefaultTempo());
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param tune
     *            Tune
     * @return TuneTableRecord
     */
    public static TuneTableRecord toEntity(Tune tune) {
        final var tuneEntity = new TuneTableRecord();
        tuneEntity.setDomainId(tune.id().value());
        tuneEntity.setTitle(tune.title().value());
        tuneEntity.setTuneKind(tune.tuneKind().name());
        tuneEntity.setDefaultComposerCredit(
                Optional.ofNullable(tune.defaultComposerCredit())
                        .map(Credit::value)
                        .orElse(null));
        tuneEntity.setDefaultArrangerCredit(
                Optional.ofNullable(tune.defaultArrangerCredit())
                        .map(Credit::value)
                        .orElse(null));
        tuneEntity.setOriginalWorkTitle(tune.originalWorkTitle());
        tuneEntity.setOriginalWorkCredit(tune.originalWorkCredit());
        tuneEntity.setTuneType(tune.tuneType());
        tuneEntity.setDefaultKey(tune.defaultKey());
        tuneEntity.setDefaultTempo(tune.defaultTempo());
        return tuneEntity;
    }
}
