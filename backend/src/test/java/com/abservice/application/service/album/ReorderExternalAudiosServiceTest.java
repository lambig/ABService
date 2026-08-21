package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReorderExternalAudiosService.validateOrderedExternalAudioIds（IDリスト検証の集約）のテスト")
class ReorderExternalAudiosServiceTest {

    @Test
    @DisplayName("有効なIDのリストは成功し、順序を保ったExternalAudio.Idのリストを生成する")
    void validIdsSucceedInOrder() {
        final var id1 = ExternalAudio.Id.generate();
        final var id2 = ExternalAudio.Id.generate();

        final var result = ReorderExternalAudiosService.validateOrderedExternalAudioIds(
                List.of(
                        id1.value(),
                        id2.value()));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve()).containsExactly(id1, id2);
    }

    @Test
    @DisplayName("リスト自体が未指定ならエラー")
    void nullListFails() {
        final var result = ReorderExternalAudiosService.validateOrderedExternalAudioIds(null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("EXTERNAL_AUDIO_ORDER_REQUIRED");
    }

    @Test
    @DisplayName("複数の不正なIDがあれば全てのエラーを集約する")
    void invalidIdsAggregateErrors() {
        final var result = ReorderExternalAudiosService.validateOrderedExternalAudioIds(
                List.of(
                        "not-a-uuid",
                        "   "));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ID_INVALID_UUID", "ID_BLANK");
    }
}
