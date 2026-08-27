package com.abservice.application.query.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.datasource.AlbumExternalAudioRow;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetAlbumService.toResult（結果分岐）のテスト")
class GetAlbumServiceTest {

    private static final String ASSET_BASE_PATH = "/assets";

    @Test
    @DisplayName("エンティティありはFoundを返す")
    void entityYieldsFound() {
        final var entity = new AlbumTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setTitle("タイトル");
        entity.setReleaseDate(
                LocalDate.of(
                        2026,
                        1,
                        1));
        entity.setArtistDisplayName("アーティスト名");

        final var result = GetAlbumService.toResult(
                entity,
                ASSET_BASE_PATH,
                List.of(
                        new AlbumExternalAudioRow(
                                1L,
                                "0192f8a0-0000-7000-8000-0000000000a1",
                                1,
                                "https://soundcloud.com/example/first")),
                List.of());

        assertThat(result).isInstanceOf(GetAlbumResult.Found.class);
        assertThat(((GetAlbumResult.Found) result).album().title()).isEqualTo("タイトル");
        assertThat(((GetAlbumResult.Found) result).album().externalAudios()).hasSize(1);
    }

    @Test
    @DisplayName("nullはNotFoundを返す")
    void nullYieldsNotFound() {
        assertThat(
                GetAlbumService.toResult(
                        null,
                        ASSET_BASE_PATH,
                        List.of(),
                        List.of()))
                .isInstanceOf(GetAlbumResult.NotFound.class);
    }
}
