package com.abservice.application.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssueAssetUploadUrlService（アップロードURL発行）のテスト")
class IssueAssetUploadUrlServiceTest {

    private static final long MAX_BYTES = 52428800L;

    @Test
    @DisplayName("受け入れ可能な形式は拡張子付きのキーと署名付きURLを返す")
    void issuesUrlForAcceptedContentType() {
        final var storage = FakeAssetStorage.empty();

        final var output = new IssueAssetUploadUrlService(storage, MAX_BYTES)
                .execute(new IssueAssetUploadUrlInput("image/png"))
                .await().indefinitely();

        assertThat(output.assetKey()).endsWith(".png");
        assertThat(output.uploadUrl()).contains(output.assetKey());
        assertThat(output.expiresAt()).isEqualTo(FakeAssetStorage.expiresAt());
        assertThat(output.maxBytes()).isEqualTo(MAX_BYTES);
        assertThat(storage.presignedKeys()).containsExactly(output.assetKey());
    }

    @Test
    @DisplayName("発行するキーは毎回異なる")
    void issuesDistinctKeys() {
        final var service = new IssueAssetUploadUrlService(FakeAssetStorage.empty(), MAX_BYTES);

        final var first = service.execute(new IssueAssetUploadUrlInput("image/jpeg")).await().indefinitely();
        final var second = service.execute(new IssueAssetUploadUrlInput("image/jpeg")).await().indefinitely();

        assertThat(first.assetKey()).isNotEqualTo(second.assetKey());
    }

    @Test
    @DisplayName("受け入れ対象外の形式は検証エラーにし、URLを発行しない")
    void rejectsUnsupportedContentType() {
        final var storage = FakeAssetStorage.empty();

        assertThatThrownBy(
                () -> new IssueAssetUploadUrlService(storage, MAX_BYTES)
                        .execute(new IssueAssetUploadUrlInput("image/gif"))
                        .await().indefinitely())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("image/gif");

        assertThat(storage.presignedKeys()).isEmpty();
    }
}
