package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReorderTracksService.validateOrderedTrackIds（IDリスト検証の集約）のテスト")
class ReorderTracksServiceTest {

    @Test
    @DisplayName("有効なIDのリストは成功し、順序を保ったTrack.Idのリストを生成する")
    void validIdsSucceedInOrder() {
        final var id1 = Track.Id.generate();
        final var id2 = Track.Id.generate();

        final var result = ReorderTracksService.validateOrderedTrackIds(
                List.of(
                        id1.value(),
                        id2.value()));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve()).containsExactly(id1, id2);
    }

    @Test
    @DisplayName("リスト自体が未指定ならエラー")
    void nullListFails() {
        final var result = ReorderTracksService.validateOrderedTrackIds(null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_ORDER_REQUIRED");
    }

    @Test
    @DisplayName("複数の不正なIDがあれば全てのエラーを集約する")
    void invalidIdsAggregateErrors() {
        final var result = ReorderTracksService.validateOrderedTrackIds(
                List.of(
                        "not-a-uuid",
                        "   "));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ID_INVALID_UUID", "ID_BLANK");
    }
}
