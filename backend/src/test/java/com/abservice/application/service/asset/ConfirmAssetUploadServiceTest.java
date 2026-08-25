package com.abservice.application.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfirmAssetUploadService（アップロード確定と実体検査）のテスト")
class ConfirmAssetUploadServiceTest {

    private static final String PNG_KEY = "0192f8a0-0000-7000-8000-000000000000.png";
    private static final long MAX_BYTES = 1024L;
    private static final String BASE_PATH = "/assets";

    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n', 0, 0, 0, 0};

    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0,
            0};

    @Test
    @DisplayName("形式とサイズが妥当なら公開配信URLを返す")
    void confirmsValidAsset() {
        final var storage = FakeAssetStorage.holding(PNG_HEAD, 512L);

        final var output = service(storage).execute(new ConfirmAssetUploadInput(PNG_KEY))
                .await().indefinitely();

        assertThat(output.assetKey()).isEqualTo(PNG_KEY);
        assertThat(output.url()).isEqualTo(BASE_PATH + "/" + PNG_KEY);
        assertThat(output.contentType()).isEqualTo("image/png");
        assertThat(output.sizeBytes()).isEqualTo(512L);
        assertThat(storage.publishedKeys()).as("検査に通った実体は配信対象として確定する").containsExactly(PNG_KEY);
        assertThat(storage.discardedKeys()).isEmpty();
    }

    @Test
    @DisplayName("実体が無いキーは未存在エラーにする")
    void rejectsMissingAsset() {
        assertThatThrownBy(
                () -> service(FakeAssetStorage.empty()).execute(new ConfirmAssetUploadInput(PNG_KEY))
                        .await().indefinitely())
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(PNG_KEY);
    }

    @Test
    @DisplayName("上限を超えるサイズは検証エラーにし、実体を破棄する")
    void rejectsAndDeletesTooLargeAsset() {
        final var storage = FakeAssetStorage.holding(PNG_HEAD, MAX_BYTES + 1);

        assertThatThrownBy(
                () -> service(storage).execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ASSET_TOO_LARGE");

        assertThat(storage.discardedKeys()).containsExactly(PNG_KEY);
    }

    @Test
    @DisplayName("画像として認識できない実体は検証エラーにし、実体を破棄する")
    void rejectsAndDeletesNonImageAsset() {
        final var storage = FakeAssetStorage.holding(
                "not an image".getBytes(StandardCharsets.UTF_8),
                12L);

        assertThatThrownBy(
                () -> service(storage).execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ASSET_CONTENT_MISMATCH");

        assertThat(storage.discardedKeys()).containsExactly(PNG_KEY);
    }

    @Test
    @DisplayName("発行したキーの拡張子と実体の形式が異なる場合は検証エラーにする")
    void rejectsFormatMismatchAgainstKeyExtension() {
        final var storage = FakeAssetStorage.holding(JPEG_HEAD, 512L);

        assertThatThrownBy(
                () -> service(storage).execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ASSET_CONTENT_MISMATCH");

        assertThat(storage.discardedKeys()).containsExactly(PNG_KEY);
    }

    private static ConfirmAssetUploadService service(FakeAssetStorage storage) {
        return new ConfirmAssetUploadService(
                storage,
                MAX_BYTES,
                BASE_PATH);
    }
}
