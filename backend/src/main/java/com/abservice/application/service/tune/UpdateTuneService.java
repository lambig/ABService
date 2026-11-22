package com.abservice.application.service.tune;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.repository.tune.TuneRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * チューン更新コマンドサービス
 *
 * <p>
 * 外部入力（{@link UpdateTuneInput}）から既存 {@link Tune} をCreate相当の全フィールドでPUT風に全項目置換する
 * ユースケースです。Tuneは除外すべきライフサイクル系フィールドを持たないため、既存の値を引き継ぐ必要が無く、
 * {@code Tune.reconstruct} で直接再構築します（{@code Tune.create}
 * と同じ検証範囲になり、Create/Update 間の挙動が一致します）。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して更新後の {@code Tune} を組み立てるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に、対象チューンの不在は {@link EntityNotFoundException}
 * に集約し、HTTP への変換は presentation 層の ExceptionMapper が担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class UpdateTuneService implements CommandService<UpdateTuneInput, UpdateTuneOutput> {

    private final TuneRepository tuneRepository;

    @WithTransaction
    @Override
    public Uni<UpdateTuneOutput> execute(UpdateTuneInput input) {
        return input.asRequested()
                .flatMap(this::findExisting)
                .call(input::asValidated)
                .map(
                        existing -> validateAndApply(existing, input)
                                .resolve(ValidationException::new))
                .flatMap(tuneRepository::save)
                .map(UpdateTuneService::toOutput);
    }

    private Uni<Tune> findExisting(Tune.Id id) {
        return tuneRepository.findById(id)
                .onItem()
                .ifNull()
                .failWith(() -> EntityNotFoundException.of("Tune", id.value()));
    }

    static Result<Tune> validateAndApply(Tune existing, UpdateTuneInput input) {
        return input.bodyValues()
                .map(
                        values -> Tune.reconstruct(
                                existing.id(),
                                values.title(),
                                values.tuneKind(),
                                values.composer()
                                        .orElse(null),
                                values.arranger()
                                        .orElse(null),
                                input.originalWorkTitle(),
                                input.originalWorkCredit(),
                                input.tuneType(),
                                input.defaultKey(),
                                input.defaultTempo()));
    }

    private static UpdateTuneOutput toOutput(Tune tune) {
        return new UpdateTuneOutput(
                tune.id()
                        .value(),
                tune.title()
                        .value(),
                tune.tuneKind()
                        .name());
    }
}
