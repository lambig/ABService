package com.abservice.application.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.AssetConfirmConflictException;
import com.abservice.domain.exception.BusinessRuleViolationException;
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

    @Test
    @DisplayName("確定済みのキーをもう一度確定しようとした場合は競合にし、実体を確定し直さない")
    void rejectsConfirmingAlreadyPublishedKey() {
        final var storage = FakeAssetStorage.holding(PNG_HEAD, 512L);
        final var service = service(storage);

        service.execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely();

        assertThatThrownBy(
                () -> service.execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining(PNG_KEY);

        assertThat(storage.publishedKeys()).as("確定は一度きりで、同じキーが確定し直されない").containsExactly(PNG_KEY);
    }

    @Test
    @DisplayName("検査から確定までの間に実体が置き換わった場合は競合にする")
    void rejectsWhenContentChangedBetweenInspectionAndPublish() {
        final var storage = FakeAssetStorage.replacedAfterRead(PNG_HEAD, 512L);

        assertThatThrownBy(
                () -> service(storage).execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasCauseInstanceOf(AssetConfirmConflictException.class);

        assertThat(storage.publishedKeys()).as("検査した実体でなければ確定しない").isEmpty();
    }

    @Test
    @DisplayName("確定済みの判定をすり抜けても、確定そのものの条件で競合になる")
    void rejectsSecondConfirmThatSlipsPastThePublishedCheck() {
        final var storage = FakeAssetStorage.hidingPublishedState(PNG_HEAD, 512L);
        final var service = service(storage);

        service.execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely();

        assertThatThrownBy(
                () -> service.execute(new ConfirmAssetUploadInput(PNG_KEY)).await().indefinitely())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasCauseInstanceOf(AssetConfirmConflictException.class);

        assertThat(storage.publishedKeys()).as("配信対象へ入るのは先に成立した1つだけ").containsExactly(PNG_KEY);
    }

    private static ConfirmAssetUploadService service(FakeAssetStorage storage) {
        return new ConfirmAssetUploadService(
                storage,
                MAX_BYTES,
                BASE_PATH);
    }
}
