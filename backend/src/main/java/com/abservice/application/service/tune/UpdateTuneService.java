package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.domain.repository.tune.TuneRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

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
public class UpdateTuneService implements CommandService<UpdateTuneInput, UpdateTuneOutput> {

    private final TuneRepository tuneRepository;

    @WithTransaction
    @Override
    public Uni<UpdateTuneOutput> execute(UpdateTuneInput input) {
        return Uni.createFrom()
                .item(
                        () -> Tune.Id.fromInput(input.tuneId())
                                .resolve(ValidationException::new))
                .flatMap(this::findExisting)
                .map(
                        existing -> validateAndApply(existing, input)
                                .resolve(ValidationException::new))
                .flatMap(tuneRepository::save)
                .map(UpdateTuneService::toOutput);
    }

    private Uni<Tune> findExisting(Tune.Id id) {
        return tuneRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Tune", id.value()));
    }

    static Result<Tune> validateAndApply(Tune existing, UpdateTuneInput input) {
        return Result.zip(
                TuneTitle.fromInput(input.title()),
                TuneKind.fromInput(input.tuneKind()),
                resolveCredit(input.defaultComposerCredit()),
                TitleKindComposer::new)
                .flatMap(
                        composer -> resolveCredit(input.defaultArrangerCredit())
                                .map(
                                        arrangerCredit -> Tune.reconstruct(
                                                existing.id(),
                                                composer.title(),
                                                composer.tuneKind(),
                                                composer.defaultComposerCredit().orElse(null),
                                                arrangerCredit.orElse(null),
                                                input.originalWorkTitle(),
                                                input.originalWorkCredit(),
                                                input.tuneType(),
                                                input.defaultKey(),
                                                input.defaultTempo())));
    }

    private record TitleKindComposer(TuneTitle title, TuneKind tuneKind, Optional<Credit> defaultComposerCredit) {
    }

    private static Result<Optional<Credit>> resolveCredit(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> Credit.fromInput(v)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<Credit>>success(Optional.empty()));
    }

    private static UpdateTuneOutput toOutput(Tune tune) {
        return new UpdateTuneOutput(
                tune.id().value(),
                tune.title().value(),
                tune.tuneKind().name());
    }
}
