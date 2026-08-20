package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.repository.tune.TuneRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * チューン削除コマンドサービス
 *
 * <p>
 * べき等な削除ユースケースです。対象チューンの存在有無は確認せず、常に成功として扱います
 * （DELETEの一般的なべき等性に倣う）。ただしチューンIDの形式検証は行い、不正な形式は {@link ValidationException}
 * として扱います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class DeleteTuneService implements CommandService<DeleteTuneInput, DeleteTuneOutput> {

    private final TuneRepository tuneRepository;

    @WithTransaction
    @Override
    public Uni<DeleteTuneOutput> execute(DeleteTuneInput input) {
        return Uni.createFrom()
                .item(
                        () -> Tune.Id.fromInput(input.tuneId())
                                .resolve(ValidationException::new))
                .flatMap(tuneRepository::deleteById)
                .replaceWith(new DeleteTuneOutput());
    }
}
