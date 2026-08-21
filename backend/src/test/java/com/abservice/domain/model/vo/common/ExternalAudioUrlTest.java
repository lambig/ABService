package com.abservice.domain.model.vo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalAudioUrl（外部音源URLのVO）のテスト")
class ExternalAudioUrlTest {

    private static final String VALID_URL = "https://soundcloud.com/example-artist/example-track";

    @Test
    @DisplayName("許可ホストのhttps URLを受け入れる")
    void acceptsAllowedHost() {
        assertThat(ExternalAudioUrl.of(VALID_URL).value().value()).isEqualTo(VALID_URL);
        assertThat(ExternalAudioUrl.fromInput("https://www.soundcloud.com/example/track"))
                .isInstanceOf(Result.Success.class);
        assertThat(ExternalAudioUrl.fromInput("https://on.soundcloud.com/abcdef"))
                .isInstanceOf(Result.Success.class);
    }

    @Test
    @DisplayName("許可されていないホストは拒否する")
    void rejectsDisallowedHost() {
        assertThat(codesOf(ExternalAudioUrl.fromInput("https://example.com/example/track")))
                .contains("EXTERNAL_AUDIO_URL_HOST_NOT_ALLOWED");
        assertThat(codesOf(ExternalAudioUrl.fromInput("https://soundcloud.com.example.com/track")))
                .contains("EXTERNAL_AUDIO_URL_HOST_NOT_ALLOWED");
    }

    @Test
    @DisplayName("httpsでないURLは拒否する")
    void rejectsNonHttps() {
        assertThat(codesOf(ExternalAudioUrl.fromInput("http://soundcloud.com/example/track")))
                .contains("EXTERNAL_AUDIO_URL_NOT_HTTPS");
    }

    @Test
    @DisplayName("ポート・認証情報を含むURLは拒否する")
    void rejectsPortAndCredentials() {
        assertThat(codesOf(ExternalAudioUrl.fromInput("https://soundcloud.com:8443/example/track")))
                .contains("EXTERNAL_AUDIO_URL_NOT_HTTPS");
        assertThat(codesOf(ExternalAudioUrl.fromInput("https://user:pass@soundcloud.com/example/track")))
                .contains("EXTERNAL_AUDIO_URL_NOT_HTTPS");
    }

    @Test
    @DisplayName("未指定・空白のURLはURL自体の検証で拒否する")
    void rejectsBlank() {
        assertThat(codesOf(ExternalAudioUrl.fromInput(null))).contains("URL_REQUIRED");
        assertThat(codesOf(ExternalAudioUrl.fromInput("   "))).contains("URL_REQUIRED");
    }

    @Test
    @DisplayName("不正なURLの内部生成は例外にする")
    void throwsOnInvalidInternalCreation() {
        assertThatThrownBy(() -> ExternalAudioUrl.of("https://example.com/track"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ値のURLは等価とみなす")
    void equivalentByValue() {
        assertThat(ExternalAudioUrl.of(VALID_URL).equivalentTo(ExternalAudioUrl.of(VALID_URL))).isTrue();
        assertThat(
                ExternalAudioUrl.of(VALID_URL)
                        .equivalentTo(ExternalAudioUrl.of("https://soundcloud.com/example/other")))
                .isFalse();
    }

    private static List<String> codesOf(Result<ExternalAudioUrl> result) {
        return ((Result.Failure<ExternalAudioUrl>) result).errors().stream().map(ErrorResult::code).toList();
    }
}
