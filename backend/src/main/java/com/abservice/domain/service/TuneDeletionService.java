package com.abservice.domain.service;

import static java.util.function.Predicate.not;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import lombok.AllArgsConstructor;

/**
 * チューンの削除を担うドメインサービス
 *
 * <p>
 * チューンは{@link Album}から独立して存在するが、参照はアルバム集約の内側（トラック内のチューン構成）にある。
 * 削除してよいかは参照の有無に依存し、チューン集約単体では判定できない。本サービスは参照の有無を引き、
 * 操作オブジェクト（{@link TuneDeletion}）を組み立てて可否を確定する。
 * </p>
 *
 * <p>
 * 参照されているチューンは削除できない（参照を外す操作を先に要求する）。参照を残したまま削除を許して構成側を null
 * へ倒すと「曲が不明な録音」という中途半端な状態が生まれ、それが業務上の記述なのか削除の副作用なのかを 区別できなくなる。
 * </p>
 *
 * <p>
 * 削除自体はドメインの状態遷移ではなくリポジトリの操作のため、{@code CrossAggregateTransition} で守る対象は
 * 持たない。判定の結果として「削除してよいチューンID」を返し、削除はコマンドサービスが行う。
 * </p>
 *
 * <p>
 * 現時点でトラックへチューン構成を追加する経路は無いため、判定と削除の間に参照が生まれることはない。参照を追加する
 * ユースケースを設けるときは、参照の追加側がチューンに対する主張を伴う必要がある（アルバムに対する {@link AlbumAccessService}
 * と同じ形）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class TuneDeletionService implements DomainService {

    private final AlbumRepository albumRepository;

    /**
     * チューンを削除する試み
     *
     * @param tuneId
     *            削除対象のチューンID
     * @param referencedByTrack
     *            当該チューンを参照しているトラック内チューン構成があるか
     */
    public record TuneDeletion(Tune.Id tuneId, boolean referencedByTrack) {

        /** トラックから参照されているチューンは削除できない */
        private static final ErrorResult REFERENCED_BY_TRACK_ERROR = new ErrorResult(
                "tuneId",
                "Cannot delete a tune referenced by a track",
                "TUNE_REFERENCED_BY_TRACK");

        /**
         * 削除してよいかを評価します（例外を投げず、結果を {@link Result} で返す）。
         *
         * @return 削除してよければ自身の {@code Success}、規則を満たさなければ検証エラーの {@code Failure}
         */
        public Result<TuneDeletion> asValidated() {
            return policy().verify(this, Function.identity());
        }

        /**
         * 規則を満たすときだけ削除対象のIDを返します。
         *
         * @return 削除してよいチューンID
         */
        public Tune.Id deletableId() {
            return asValidated()
                    .map(TuneDeletion::tuneId)
                    .resolve(BusinessRuleViolationException::fromErrors);
        }

        private static Policy<TuneDeletion> policy() {
            return Policy.of(
                    not(TuneDeletion::referencedByTrack),
                    REFERENCED_BY_TRACK_ERROR);
        }
    }

    /**
     * 削除してよいチューンIDを返します。
     *
     * @param tuneId
     *            削除対象のチューンID
     * @return 削除してよいチューンID。参照されている場合は {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<Tune.Id> deletable(Tune.Id tuneId) {
        return albumRepository.existsTrackTuneReferencing(tuneId)
                .map(referenced -> new TuneDeletion(tuneId, referenced))
                .map(TuneDeletion::deletableId);
    }
}
