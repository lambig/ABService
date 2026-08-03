package com.abservice.application.query.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetAlbumService.toResult（結果分岐）のテスト")
class GetAlbumServiceTest {

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

        final var result = GetAlbumService.toResult(entity);

        assertThat(result).isInstanceOf(GetAlbumResult.Found.class);
        assertThat(((GetAlbumResult.Found) result).album().title()).isEqualTo("タイトル");
    }

    @Test
    @DisplayName("nullはNotFoundを返す")
    void nullYieldsNotFound() {
        assertThat(GetAlbumService.toResult(null)).isInstanceOf(GetAlbumResult.NotFound.class);
    }
}
