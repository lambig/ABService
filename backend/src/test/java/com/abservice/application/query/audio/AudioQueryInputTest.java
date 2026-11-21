package com.abservice.application.query.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jspecify.annotations.Nullable;

@DisplayName("必須UUIDを持つaudio Queryの入力契約")
class AudioQueryInputTest {
    @Test
    @DisplayName("3つのQueryは正準形UUIDの大文字を同じUUIDへ正規化する")
    void normalizesCanonicalIds() {
        final var input = "ABCDEF01-2345-6789-ABCD-EF0123456789";
        final var expected = UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789");
        assertThat(new GetAlbumCrossfadeService.Query(input).asValidated()).isEqualTo(expected);
        assertThat(new GetAudioRegistrationService.Query(input).asValidated()).isEqualTo(expected);
        assertThat(new ResolveListeningAssetUrlService.Query(input, Instant.EPOCH).asValidated()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "invalid", "1-1-1-1-1", " abcdef01-2345-6789-abcd-ef0123456789",
            "abcdef01-2345-6789-abcd-ef0123456789 "})
    @DisplayName("クロスフェード照会は未指定・非正準IDをalbumIdエラーで拒否する")
    void rejectsAlbumId(@Nullable String id) {
        assertThatThrownBy(() -> new GetAlbumCrossfadeService.Query(id).asValidated())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).containsExactly(
                                new ErrorResult(
                                        "albumId",
                                        "有効な値を指定してください",
                                        "INVALID_AUDIO_ARGUMENT")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "invalid", "1-1-1-1-1", " abcdef01-2345-6789-abcd-ef0123456789",
            "abcdef01-2345-6789-abcd-ef0123456789 "})
    @DisplayName("登録照会は未指定・非正準IDをaudioIdエラーで拒否する")
    void rejectsAudioId(@Nullable String id) {
        assertThatThrownBy(() -> new GetAudioRegistrationService.Query(id).asValidated())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).containsExactly(
                                new ErrorResult(
                                        "audioId",
                                        "有効な値を指定してください",
                                        "INVALID_AUDIO_ARGUMENT")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "invalid", "1-1-1-1-1", " abcdef01-2345-6789-abcd-ef0123456789",
            "abcdef01-2345-6789-abcd-ef0123456789 "})
    @DisplayName("取得URL照会は未指定・非正準IDをassetIdエラーで拒否する")
    void rejectsAssetId(@Nullable String id) {
        assertThatThrownBy(() -> new ResolveListeningAssetUrlService.Query(id, Instant.EPOCH).asValidated())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).containsExactly(
                                new ErrorResult(
                                        "assetId",
                                        "有効な値を指定してください",
                                        "INVALID_AUDIO_ARGUMENT")));
    }
}
