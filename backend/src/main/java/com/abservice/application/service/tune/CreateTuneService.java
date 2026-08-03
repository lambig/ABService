package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
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
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * チューン作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateTuneInput}）から新規 {@link Tune} を生成して永続化するユースケースです。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して {@code Tune} を組み立てるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に集約し、HTTP への変換は presentation 層の ExceptionMapper
 * が担います。
 * </p>
 */
@ApplicationScoped
public class CreateTuneService implements CommandService<CreateTuneInput, CreateTuneOutput> {

    private final TuneRepository tuneRepository;

    /**
     * @param tuneRepository
     *            チューンリポジトリ
     */
    public CreateTuneService(TuneRepository tuneRepository) {
        this.tuneRepository = tuneRepository;
    }

    @WithTransaction
    @Override
    public Uni<CreateTuneOutput> execute(CreateTuneInput input) {
        return Uni.createFrom()
                .item(
                        () -> validate(input)
                                .resolve(ValidationException::new))
                .flatMap(tuneRepository::save)
                .map(CreateTuneService::toOutput);
    }

    static Result<Tune> validate(CreateTuneInput input) {
        return Result.zip(
                TuneTitle.fromInput(input.title()),
                TuneKind.fromInput(input.tuneKind()),
                resolveCredit(input.defaultComposerCredit()),
                TitleKindComposer::new)
                .flatMap(
                        composer -> resolveCredit(input.defaultArrangerCredit())
                                .map(
                                        arrangerCredit -> Tune.create(
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

    private static CreateTuneOutput toOutput(Tune tune) {
        return new CreateTuneOutput(
                tune.id().value(),
                tune.title().value(),
                tune.tuneKind().name());
    }
}
