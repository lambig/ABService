package com.abservice.domain.model.aggregate.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;

@DisplayName("Album集約のテスト")
class AlbumTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("正常な値で生成できること")
        void createWithValidValuesShouldSucceed() {
            // Arrange
            final var title = AlbumTitle.of("Test Album");
            final var releaseDate = BusinessDate.of(
                    2024,
                    1,
                    15);
            final var artistCredit = ArtistCredit.of("Test Artist");

            // Act
            final var album = Album.create(
                    title,
                    releaseDate,
                    artistCredit,
                    null,
                    null,
                    null);

            // Assert
            assertThat(album).isNotNull();
            assertThat(album.id()).isNotNull();
            assertThat(album.title()).isEqualTo(title);
            assertThat(album.releaseDate()).isEqualTo(releaseDate);
            assertThat(album.artistCredit()).isEqualTo(artistCredit);
            assertThat(album.eventReleasedAt()).isNull();
            assertThat(album.catalogNumber()).isNull();
            assertThat(album.isdn()).isNull();
            assertThat(album.getTracks().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            final var title = AlbumTitle.of("Complete Album");
            final var releaseDate = BusinessDate.of(
                    2024,
                    5,
                    1);
            final var artistCredit = ArtistCredit.of("Full Artist");
            final var eventReleasedAt = EventReleasedAt.atEvent(
                    "Test Event",
                    2024,
                    5,
                    1);
            final var catalogNumber = CatalogNumber.of("CAT-001");
            // 278-4-000000-00-7: チェックデジット計算 sum=43 -> (10-(43%10))%10 = 7
            final var isdn = Isdn.of("2784000000007");

            // Act
            final var album = Album.create(
                    title,
                    releaseDate,
                    artistCredit,
                    eventReleasedAt,
                    catalogNumber,
                    isdn);

            // Assert
            assertThat(album).isNotNull();
            assertThat(album.eventReleasedAt()).isEqualTo(eventReleasedAt);
            assertThat(album.catalogNumber()).isEqualTo(catalogNumber);
            assertThat(album.isdn()).isEqualTo(isdn);
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Arrange
            final var releaseDate = BusinessDate.of(
                    2024,
                    1,
                    15);
            final var artistCredit = ArtistCredit.of("Artist");

            // Act & Assert
            assertThatThrownBy(
                    () -> Album.create(
                            null,
                            releaseDate,
                            artistCredit,
                            null,
                            null,
                            null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Album title cannot be null");
        }

        @Test
        @DisplayName("アーティストクレジットがnullの場合は例外が発生すること")
        void createWithNullArtistCreditShouldThrowException() {
            // Arrange
            final var title = AlbumTitle.of("Test Album");
            final var releaseDate = BusinessDate.of(
                    2024,
                    1,
                    15);

            // Act & Assert
            assertThatThrownBy(
                    () -> Album.create(
                            title,
                            releaseDate,
                            null,
                            null,
                            null,
                            null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Artist credit cannot be null");
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleWithValidTitleShouldSucceed() {
            // Arrange
            final var album = createTestAlbum();
            final var newTitle = AlbumTitle.of("Updated Album Title");

            // Act
            final var updated = album.changeTitle(newTitle);

            // Assert
            assertThat(updated.title()).isEqualTo(newTitle);
            assertThat(updated.id()).isEqualTo(album.id()); // IDは変わらない
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();

            // Act & Assert
            assertThatThrownBy(() -> album.changeTitle(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Album title cannot be null");
        }
    }

    @Nested
    @DisplayName("アーティストクレジット変更テスト")
    class ChangeArtistCreditTest {

        @Test
        @DisplayName("アーティストクレジットを変更できること")
        void changeArtistCreditWithValidCreditShouldSucceed() {
            // Arrange
            final var album = createTestAlbum();
            final var newCredit = ArtistCredit.of("New Artist");

            // Act
            final var updated = album.changeArtistCredit(newCredit);

            // Assert
            assertThat(updated.artistCredit()).isEqualTo(newCredit);
        }

        @Test
        @DisplayName("nullのアーティストクレジットに変更しようとすると例外が発生すること")
        void changeArtistCreditWithNullShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();

            // Act & Assert
            assertThatThrownBy(() -> album.changeArtistCredit(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Artist credit cannot be null");
        }
    }

    @Nested
    @DisplayName("トラック追加テスト")
    class AddTrackTest {

        @Test
        @DisplayName("トラックを追加できること")
        void addTrackWithValidTrackShouldSucceed() {
            // Arrange
            final var album = createTestAlbum();
            final var track = createTestTrack(1, "First Track");

            // Act
            final var updated = album.addTrack(track);

            // Assert
            assertThat(updated.getTrackCount()).isEqualTo(1);
            assertThat(updated.getTracks().contains(track)).isTrue();
        }

        @Test
        @DisplayName("複数のトラックを追加できること")
        void addTrackWithMultipleTracksShouldSucceed() {
            // Arrange
            final var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            final var track2 = createTestTrack(2, "Track 2");
            final var track3 = createTestTrack(3, "Track 3");

            // Act
            final var updated = album.addTrack(track1).addTrack(track2).addTrack(track3);

            // Assert
            assertThat(updated.getTrackCount()).isEqualTo(3);
            assertThat(updated.getTracks()).isEqualTo(
                    List.of(
                            track1,
                            track2,
                            track3));
        }

        @Test
        @DisplayName("nullのトラックを追加しようとすると例外が発生すること")
        void addTrackWithNullShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();

            // Act & Assert
            assertThatThrownBy(() -> album.addTrack(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Track cannot be null");
        }

        @Test
        @DisplayName("重複するトラック番号を追加しようとすると例外が発生すること")
        void addTrackWithDuplicateTrackNumberShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            final var track2 = createTestTrack(1, "Track 2"); // 同じトラック番号
            album = album.addTrack(track1);

            // Act & Assert
            final var finalAlbum = album;
            assertThatThrownBy(() -> finalAlbum.addTrack(track2)).isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("Track number 1 already exists");
        }
    }

    @Nested
    @DisplayName("トラック削除テスト")
    class RemoveTrackTest {

        @Test
        @DisplayName("トラックを削除できること")
        void removeTrackWithExistingTrackShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            final var track = createTestTrack(1, "Track to Remove");
            album = album.addTrack(track);

            // Act
            final var updated = album.removeTrack(track.id());

            // Assert
            assertThat(updated.getTrackCount()).isEqualTo(0);
            assertThat(updated.getTracks().contains(track)).isFalse();
        }

        @Test
        @DisplayName("複数のトラックから特定のトラックを削除できること")
        void removeTrackFromMultipleTracksShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            final var track2 = createTestTrack(2, "Track 2");
            final var track3 = createTestTrack(3, "Track 3");
            album = album.addTrack(track1).addTrack(track2).addTrack(track3);

            // Act
            final var updated = album.removeTrack(track2.id());

            // Assert
            assertThat(updated.getTrackCount()).isEqualTo(2);
            assertThat(updated.getTracks().contains(track1)).isTrue();
            assertThat(updated.getTracks().contains(track2)).isFalse();
            assertThat(updated.getTracks().contains(track3)).isTrue();
        }

        @Test
        @DisplayName("存在しないトラックを削除しようとすると例外が発生すること")
        void removeTrackWithNonExistentTrackShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();
            final var nonExistentId = Track.Id.generate();

            // Act & Assert
            assertThatThrownBy(() -> album.removeTrack(nonExistentId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("nullのIDでトラックを削除しようとすると例外が発生すること")
        void removeTrackWithNullIdShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();

            // Act & Assert
            assertThatThrownBy(() -> album.removeTrack(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Track ID cannot be null");
        }
    }

    @Nested
    @DisplayName("トラック更新テスト")
    class UpdateTrackTest {

        @Test
        @DisplayName("トラックを更新できること")
        void updateTrackWithValidTrackShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            final var originalTrack = createTestTrack(1, "Original Title");
            album = album.addTrack(originalTrack);

            final var updatedTrack = Track.reconstruct(
                    originalTrack.id(),
                    1,
                    TrackTitle.of("Updated Title"),
                    originalTrack.artistCredit(),
                    originalTrack.recordingDate(),
                    originalTrack.recordingPlace(),
                    originalTrack.isLive(),
                    originalTrack.tunes());

            // Act
            final var updated = album.updateTrack(updatedTrack);

            // Assert
            final var resultTrack = updated.getTrack(originalTrack.id());
            assertThat(resultTrack.title().value()).isEqualTo("Updated Title");
        }

        @Test
        @DisplayName("nullのトラックで更新しようとすると例外が発生すること")
        void updateTrackWithNullShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();

            // Act & Assert
            assertThatThrownBy(() -> album.updateTrack(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Updated track cannot be null");
        }

        @Test
        @DisplayName("存在しないトラックを更新しようとすると例外が発生すること")
        void updateTrackWithNonExistentTrackShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            album = album.addTrack(track1);

            final var nonExistentTrack = createTestTrack(2, "Non Existent");

            // Act & Assert
            final var finalAlbum = album;
            assertThatThrownBy(() -> finalAlbum.updateTrack(nonExistentTrack))
                    .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("not found");
        }

        @Test
        @DisplayName("他のトラックと重複するトラック番号に更新しようとすると例外が発生すること")
        void updateTrackWithDuplicateTrackNumberShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            final var track2 = createTestTrack(2, "Track 2");
            album = album.addTrack(track1).addTrack(track2);

            // track2のトラック番号を1に変更しようとする（track1と重複）
            final var updatedTrack = Track.reconstruct(
                    track2.id(),
                    1, // 重複するトラック番号
                    track2.title(),
                    track2.artistCredit(),
                    track2.recordingDate(),
                    track2.recordingPlace(),
                    track2.isLive(),
                    track2.tunes());

            // Act & Assert
            final var finalAlbum = album;
            assertThatThrownBy(() -> finalAlbum.updateTrack(updatedTrack))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("Track number 1 already exists");
        }
    }

    @Nested
    @DisplayName("トラック並び替えテスト")
    class ReorderTracksTest {

        @Test
        @DisplayName("トラック順序を変更できること")
        void reorderTracksWithValidOrderShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            final var track2 = createTestTrack(2, "Track 2");
            final var track3 = createTestTrack(3, "Track 3");
            album = album.addTrack(track1).addTrack(track2).addTrack(track3);

            // 順序を逆にする
            final var newOrder = List.of(
                    track3.id(),
                    track2.id(),
                    track1.id());

            // Act
            final var updated = album.reorderTracks(newOrder);

            // Assert
            final var sortedTracks = updated.getTracksSortedByTrackNo();
            assertThat(sortedTracks.size()).isEqualTo(3);
            assertThat(sortedTracks.get(0).id()).isEqualTo(track3.id());
            assertThat(sortedTracks.get(0).trackNo()).isEqualTo(1);
            assertThat(sortedTracks.get(1).id()).isEqualTo(track2.id());
            assertThat(sortedTracks.get(1).trackNo()).isEqualTo(2);
            assertThat(sortedTracks.get(2).id()).isEqualTo(track1.id());
            assertThat(sortedTracks.get(2).trackNo()).isEqualTo(3);
        }

        @Test
        @DisplayName("トラック数が一致しない場合は例外が発生すること")
        void reorderTracksWithMismatchedCountShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            album = album.addTrack(track1);

            final var invalidOrder = List.of(track1.id(), Track.Id.generate()); // 2つ指定

            // Act & Assert
            final var finalAlbum = album;
            assertThatThrownBy(() -> finalAlbum.reorderTracks(invalidOrder))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ordered track IDs must match the number of tracks");
        }

        @Test
        @DisplayName("存在しないトラックIDを含む場合は例外が発生すること")
        void reorderTracksWithNonExistentIdShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            final var track1 = createTestTrack(1, "Track 1");
            album = album.addTrack(track1);

            final var invalidOrder = List.of(Track.Id.generate()); // 存在しないID

            // Act & Assert
            final var finalAlbum = album;
            assertThatThrownBy(() -> finalAlbum.reorderTracks(invalidOrder))
                    .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("トラック取得テスト")
    class GetTrackTest {

        @Test
        @DisplayName("特定のトラックを取得できること")
        void getTrackWithExistingIdShouldReturnTrack() {
            // Arrange
            var album = createTestAlbum();
            final var track = createTestTrack(1, "Target Track");
            album = album.addTrack(track);

            // Act
            final var result = album.getTrack(track.id());

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(track.id());
            assertThat(result.title()).isEqualTo(track.title());
        }

        @Test
        @DisplayName("存在しないトラックを取得しようとすると例外が発生すること")
        void getTrackWithNonExistentIdShouldThrowException() {
            // Arrange
            final var album = createTestAlbum();
            final var nonExistentId = Track.Id.generate();

            // Act & Assert
            assertThatThrownBy(() -> album.getTrack(nonExistentId)).isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("トラックをトラック番号順にソートして取得できること")
        void getTracksSortedByTrackNoShouldReturnSortedList() {
            // Arrange
            var album = createTestAlbum();
            final var track3 = createTestTrack(3, "Track 3");
            final var track1 = createTestTrack(1, "Track 1");
            final var track2 = createTestTrack(2, "Track 2");
            // わざと順不同で追加
            album = album.addTrack(track3).addTrack(track1).addTrack(track2);

            // Act
            final var sorted = album.getTracksSortedByTrackNo();

            // Assert
            assertThat(sorted.size()).isEqualTo(3);
            assertThat(sorted.get(0).trackNo()).isEqualTo(1);
            assertThat(sorted.get(1).trackNo()).isEqualTo(2);
            assertThat(sorted.get(2).trackNo()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Album.Idのテスト")
    class IdTest {

        @Test
        @DisplayName("IDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            final var id = Album.Id.generate();

            // Assert
            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
            assertThat(id.value().isBlank()).isFalse();
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            final var validUuid = Album.Id.generate().value();

            // Act
            final var id = Album.Id.of(validUuid);

            // Assert
            assertThat(id.value()).isEqualTo(validUuid);
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> Album.Id.of("")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> Album.Id.of("invalid-uuid")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("同じ値のIDは等しいこと")
        void equalsWithSameValueShouldBeEqual() {
            // Arrange
            final var value = Album.Id.generate().value();
            final var id1 = Album.Id.of(value);
            final var id2 = Album.Id.of(value);

            // Act & Assert
            assertThat(id2).isEqualTo(id1);
            assertThat(id2.hashCode()).isEqualTo(id1.hashCode());
        }
    }

    // テストヘルパーメソッド

    private Album createTestAlbum() {
        return Album.create(
                AlbumTitle.of("Test Album"),
                BusinessDate.of(
                        2024,
                        1,
                        1),
                ArtistCredit.of("Test Artist"),
                null,
                null,
                null);
    }

    private Track createTestTrack(int trackNo, String title) {
        return Track.create(
                trackNo,
                TrackTitle.of(title),
                ArtistCredit.of("Test Artist"),
                null);
    }
}
