package com.abservice.application.service.tune;

import com.abservice.lib.Result;
import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.tune.Tune;
import org.jspecify.annotations.Nullable;

/**
 * チューン削除コマンドの入力DTO
 *
 * @param tuneId
 *            削除対象のチューンID
 */
public record DeleteTuneInput(@Nullable String tuneId) implements CommandService.Input<DeleteTuneInput> {

    @Override
    public Policy<DeleteTuneInput> policy() {
        return new InputPolicy<>(DeleteTuneInput::parsed);
    }

    Result<Tune.Id> parsed() {
        return Tune.Id.fromInput(tuneId())
                .mapErrorFields(field -> "tuneId");
    }
}
