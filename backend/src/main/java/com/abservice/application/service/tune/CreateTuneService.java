package com.abservice.application.service.tune;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.repository.tune.TuneRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * チューン作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateTuneInput}）から新規 {@link Tune} を生成して永続化するユースケースです。
 * </p>
 *
 * <p>
 * 値の検証は入力自身が担い、本サービスは検証後の値から集約を作成して保存します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract(Failure.VALIDATION)
public class CreateTuneService implements CommandService<CreateTuneInput, CreateTuneOutput> {

    private final TuneRepository tuneRepository;

    @WithTransaction
    @Override
    public Uni<CreateTuneOutput> execute(CreateTuneInput input) {
        return input.asValidated(CreateTuneService::validate)
                .flatMap(tuneRepository::save)
                .map(CreateTuneService::toOutput);
    }

    static Result<Tune> validate(CreateTuneInput input) {
        return input.parsed()
                .map(
                        values -> Tune.create(
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

    private static CreateTuneOutput toOutput(Tune tune) {
        return new CreateTuneOutput(
                tune.id()
                        .value(),
                tune.title()
                        .value(),
                tune.tuneKind()
                        .name());
    }
}
