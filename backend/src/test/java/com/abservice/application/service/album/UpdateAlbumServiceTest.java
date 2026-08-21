package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.service.album.UpdateAlbumInput.EventInput;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.AssetKey;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateAlbumService.validateAndApply（更新差分適用の集約）のテスト")
class UpdateAlbumServiceTest {

    private static Album existingAlbum() {
        return Album.create(
                AlbumTitle.of("元のタイトル"),
                BusinessDate.of(
                        2025,
                        1,
                        1),
                ArtistCredit.of("元のアーティスト"),
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("正常な入力は成功しCreate相当フィールドを置換する")
    void validInputSucceeds() {
        final var updated = UpdateAlbumService.validateAndApply(
                existingAlbum(),
                new UpdateAlbumInput(
                        null,
                        "新タイトル",
                        "2026-01-01",
                        "新アーティスト",
                        null,
                        "ABC-0001",
                        null,
                        "01a0233d-d25a-7c3b-924f-236ee154fecc.png",
                        null))
                .resolve();

        assertThat(updated.title().value()).isEqualTo("新タイトル");
        assertThat(updated.releaseDate().asLocalDate().toString()).isEqualTo("2026-01-01");
        assertThat(updated.artistCredit().displayName().value()).isEqualTo("新アーティスト");
        assertThat(updated.catalogNumber().value()).isEqualTo("ABC-0001");
        assertThat(updated.coverImageKey().value()).isEqualTo("01a0233d-d25a-7c3b-924f-236ee154fecc.png");
    }

    @Test
    @DisplayName("カバー画像を省略した更新は既存のカバー画像を外す（全項目置換）")
    void omittedCoverImageIsCleared() {
        final var existing = existingAlbum()
                .changeCoverImageKey(AssetKey.of("01a0233d-d25a-7c3b-924f-236ee154fecc.png"));

        final var updated = UpdateAlbumService.validateAndApply(
                existing,
                new UpdateAlbumInput(
                        null,
                        "新タイトル",
                        "2026-01-01",
                        "新アーティスト",
                        null,
                        null,
                        null,
                        null,
                        null))
                .resolve();

        assertThat(updated.coverImageKey()).isNull();
    }

    @Test
    @DisplayName("カバー画像のキーが配信URLの形なら検証エラーにする")
    void invalidCoverImageKeyFails() {
        final var result = UpdateAlbumService.validateAndApply(
                existingAlbum(),
                new UpdateAlbumInput(
                        null,
                        "新タイトル",
                        "2026-01-01",
                        "新アーティスト",
                        null,
                        null,
                        null,
                        "/assets/01a0233d-d25a-7c3b-924f-236ee154fecc.png",
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ASSET_KEY_INVALID_FORMAT");
    }

    @Test
    @DisplayName("idとトラックはUpdateの対象外のため既存の値を維持する")
    void idAndTracksAreUnaffected() {
        final var existing = existingAlbum()
                .addTrack(
                        Track.create(
                                1,
                                TrackTitle.of("既存トラック"),
                                ArtistCredit.of("既存アーティスト"),
                                null));

        final var updated = UpdateAlbumService.validateAndApply(
                existing,
                new UpdateAlbumInput(
                        null,
                        "新タイトル",
                        "2026-01-01",
                        "新アーティスト",
                        null,
                        null,
                        null,
                        null,
                        null))
                .resolve();

        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.getTracks()).hasSize(1);
    }

    @Test
    @DisplayName("タイトル・リリース日・アーティスト名が不正なら全てのエラーを集約する")
    void invalidRequiredFieldsAggregatesErrors() {
        final var result = UpdateAlbumService.validateAndApply(
                existingAlbum(),
                new UpdateAlbumInput(
                        null,
                        "   ",
                        "not-a-date",
                        "   ",
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains(
                        "ALBUM_TITLE_REQUIRED",
                        "ALBUM_RELEASE_DATE_INVALID",
                        "ARTIST_CREDIT_NAME_REQUIRED");
    }

    @Test
    @DisplayName("必須項目とISDNの両方が不正なら両方のエラーを集約する（zipによる独立検証の集約）")
    void invalidRequiredFieldAndIsdnAggregatesErrorsAcrossGroups() {
        final var result = UpdateAlbumService.validateAndApply(
                existingAlbum(),
                new UpdateAlbumInput(
                        null,
                        "   ",
                        "2026-01-01",
                        "新アーティスト",
                        null,
                        null,
                        "0000000000000",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ALBUM_TITLE_REQUIRED", "ISDN_INVALID_FORMAT");
    }

    @Test
    @DisplayName("ISDN・初出イベント情報を指定すると成功し置換される")
    void validIsdnAndEventSucceeds() {
        final var updated = UpdateAlbumService.validateAndApply(
                existingAlbum(),
                new UpdateAlbumInput(
                        null,
                        "新タイトル",
                        "2026-01-01",
                        "新アーティスト",
                        null,
                        null,
                        "2784702901978",
                        null,
                        new EventInput(
                                "コミックマーケット104",
                                "2026-01-01",
                                "東京ビッグサイト",
                                "東ホ-01a",
                                "新譜あります")))
                .resolve();

        assertThat(updated.isdn().value()).isEqualTo("2784702901978");
        final var event = updated.eventReleasedAt();
        assertThat(event).isNotNull();
        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
    }
}
