package com.abservice.domain.model.aggregate.album;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            var title = AlbumTitle.of("Test Album");
            var releaseDate = BusinessDate.of(2024, 1, 15);
            var artistCredit = ArtistCredit.of("Test Artist");

            // Act
            var album = Album.create(title, releaseDate, artistCredit, null, null, null);

            // Assert
            assertNotNull(album);
            assertNotNull(album.id());
            assertEquals(title, album.title());
            assertEquals(releaseDate, album.releaseDate());
            assertEquals(artistCredit, album.artistCredit());
            assertNull(album.eventReleasedAt());
            assertNull(album.catalogNumber());
            assertNull(album.isdn());
            assertTrue(album.getTracks().isEmpty());
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var title = AlbumTitle.of("Complete Album");
            var releaseDate = BusinessDate.of(2024, 5, 1);
            var artistCredit = ArtistCredit.of("Full Artist");
            var eventReleasedAt = EventReleasedAt.atEvent("Test Event", 2024, 5, 1);
            var catalogNumber = CatalogNumber.of("CAT-001");
            // 278-4-000000-00-7: チェックデジット計算 sum=43 -> (10-(43%10))%10 = 7
            var isdn = Isdn.of("2784000000007");

            // Act
            var album = Album.create(title, releaseDate, artistCredit, eventReleasedAt, catalogNumber, isdn);

            // Assert
            assertNotNull(album);
            assertEquals(eventReleasedAt, album.eventReleasedAt());
            assertEquals(catalogNumber, album.catalogNumber());
            assertEquals(isdn, album.isdn());
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Arrange
            var releaseDate = BusinessDate.of(2024, 1, 15);
            var artistCredit = ArtistCredit.of("Artist");

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Album.create(null, releaseDate, artistCredit, null, null, null);
            });
            assertEquals("Album title cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("アーティストクレジットがnullの場合は例外が発生すること")
        void createWithNullArtistCreditShouldThrowException() {
            // Arrange
            var title = AlbumTitle.of("Test Album");
            var releaseDate = BusinessDate.of(2024, 1, 15);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Album.create(title, releaseDate, null, null, null, null);
            });
            assertEquals("Artist credit cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleWithValidTitleShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            var newTitle = AlbumTitle.of("Updated Album Title");

            // Act
            var updated = album.changeTitle(newTitle);

            // Assert
            assertEquals(newTitle, updated.title());
            assertEquals(album.id(), updated.id()); // IDは変わらない
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            var album = createTestAlbum();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.changeTitle(null);
            });
            assertEquals("Album title cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("アーティストクレジット変更テスト")
    class ChangeArtistCreditTest {

        @Test
        @DisplayName("アーティストクレジットを変更できること")
        void changeArtistCreditWithValidCreditShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            var newCredit = ArtistCredit.of("New Artist");

            // Act
            var updated = album.changeArtistCredit(newCredit);

            // Assert
            assertEquals(newCredit, updated.artistCredit());
        }

        @Test
        @DisplayName("nullのアーティストクレジットに変更しようとすると例外が発生すること")
        void changeArtistCreditWithNullShouldThrowException() {
            // Arrange
            var album = createTestAlbum();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.changeArtistCredit(null);
            });
            assertEquals("Artist credit cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("トラック追加テスト")
    class AddTrackTest {

        @Test
        @DisplayName("トラックを追加できること")
        void addTrackWithValidTrackShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            var track = createTestTrack(1, "First Track");

            // Act
            var updated = album.addTrack(track);

            // Assert
            assertEquals(1, updated.getTrackCount());
            assertTrue(updated.getTracks().contains(track));
        }

        @Test
        @DisplayName("複数のトラックを追加できること")
        void addTrackWithMultipleTracksShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            var track2 = createTestTrack(2, "Track 2");
            var track3 = createTestTrack(3, "Track 3");

            // Act
            var updated = album.addTrack(track1).addTrack(track2).addTrack(track3);

            // Assert
            assertEquals(3, updated.getTrackCount());
            assertEquals(List.of(track1, track2, track3), updated.getTracks());
        }

        @Test
        @DisplayName("nullのトラックを追加しようとすると例外が発生すること")
        void addTrackWithNullShouldThrowException() {
            // Arrange
            var album = createTestAlbum();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.addTrack(null);
            });
            assertEquals("Track cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("重複するトラック番号を追加しようとすると例外が発生すること")
        void addTrackWithDuplicateTrackNumberShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            var track2 = createTestTrack(1, "Track 2"); // 同じトラック番号
            album = album.addTrack(track1);

            // Act & Assert
            var finalAlbum = album;
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbum.addTrack(track2);
            });
            assertEquals("Track number 1 already exists", exception.getMessage());
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
            var track = createTestTrack(1, "Track to Remove");
            album = album.addTrack(track);

            // Act
            var updated = album.removeTrack(track.id());

            // Assert
            assertEquals(0, updated.getTrackCount());
            assertFalse(updated.getTracks().contains(track));
        }

        @Test
        @DisplayName("複数のトラックから特定のトラックを削除できること")
        void removeTrackFromMultipleTracksShouldSucceed() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            var track2 = createTestTrack(2, "Track 2");
            var track3 = createTestTrack(3, "Track 3");
            album = album.addTrack(track1).addTrack(track2).addTrack(track3);

            // Act
            var updated = album.removeTrack(track2.id());

            // Assert
            assertEquals(2, updated.getTrackCount());
            assertTrue(updated.getTracks().contains(track1));
            assertFalse(updated.getTracks().contains(track2));
            assertTrue(updated.getTracks().contains(track3));
        }

        @Test
        @DisplayName("存在しないトラックを削除しようとすると例外が発生すること")
        void removeTrackWithNonExistentTrackShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var nonExistentId = Track.Id.generate();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.removeTrack(nonExistentId);
            });
            assertTrue(exception.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("nullのIDでトラックを削除しようとすると例外が発生すること")
        void removeTrackWithNullIdShouldThrowException() {
            // Arrange
            var album = createTestAlbum();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.removeTrack(null);
            });
            assertEquals("Track ID cannot be null", exception.getMessage());
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
            var originalTrack = createTestTrack(1, "Original Title");
            album = album.addTrack(originalTrack);

            var updatedTrack = Track.reconstruct(originalTrack.id(), 1, TrackTitle.of("Updated Title"),
                    originalTrack.artistCredit(), originalTrack.recordingDate(), originalTrack.recordingPlace(),
                    originalTrack.isLive(), originalTrack.tunes());

            // Act
            var updated = album.updateTrack(updatedTrack);

            // Assert
            var resultTrack = updated.getTrack(originalTrack.id());
            assertEquals("Updated Title", resultTrack.title().value());
        }

        @Test
        @DisplayName("nullのトラックで更新しようとすると例外が発生すること")
        void updateTrackWithNullShouldThrowException() {
            // Arrange
            var album = createTestAlbum();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.updateTrack(null);
            });
            assertEquals("Updated track cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("存在しないトラックを更新しようとすると例外が発生すること")
        void updateTrackWithNonExistentTrackShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            album = album.addTrack(track1);

            var nonExistentTrack = createTestTrack(2, "Non Existent");

            // Act & Assert
            var finalAlbum = album;
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbum.updateTrack(nonExistentTrack);
            });
            assertTrue(exception.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("他のトラックと重複するトラック番号に更新しようとすると例外が発生すること")
        void updateTrackWithDuplicateTrackNumberShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            var track2 = createTestTrack(2, "Track 2");
            album = album.addTrack(track1).addTrack(track2);

            // track2のトラック番号を1に変更しようとする（track1と重複）
            var updatedTrack = Track.reconstruct(track2.id(), 1, // 重複するトラック番号
                    track2.title(), track2.artistCredit(), track2.recordingDate(), track2.recordingPlace(),
                    track2.isLive(), track2.tunes());

            // Act & Assert
            var finalAlbum = album;
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbum.updateTrack(updatedTrack);
            });
            assertEquals("Track number 1 already exists", exception.getMessage());
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
            var track1 = createTestTrack(1, "Track 1");
            var track2 = createTestTrack(2, "Track 2");
            var track3 = createTestTrack(3, "Track 3");
            album = album.addTrack(track1).addTrack(track2).addTrack(track3);

            // 順序を逆にする
            var newOrder = List.of(track3.id(), track2.id(), track1.id());

            // Act
            var updated = album.reorderTracks(newOrder);

            // Assert
            var sortedTracks = updated.getTracksSortedByTrackNo();
            assertEquals(3, sortedTracks.size());
            assertEquals(track3.id(), sortedTracks.get(0).id());
            assertEquals(1, sortedTracks.get(0).trackNo());
            assertEquals(track2.id(), sortedTracks.get(1).id());
            assertEquals(2, sortedTracks.get(1).trackNo());
            assertEquals(track1.id(), sortedTracks.get(2).id());
            assertEquals(3, sortedTracks.get(2).trackNo());
        }

        @Test
        @DisplayName("トラック数が一致しない場合は例外が発生すること")
        void reorderTracksWithMismatchedCountShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            album = album.addTrack(track1);

            var invalidOrder = List.of(track1.id(), Track.Id.generate()); // 2つ指定

            // Act & Assert
            var finalAlbum = album;
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbum.reorderTracks(invalidOrder);
            });
            assertEquals("Ordered track IDs must match the number of tracks", exception.getMessage());
        }

        @Test
        @DisplayName("存在しないトラックIDを含む場合は例外が発生すること")
        void reorderTracksWithNonExistentIdShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var track1 = createTestTrack(1, "Track 1");
            album = album.addTrack(track1);

            var invalidOrder = List.of(Track.Id.generate()); // 存在しないID

            // Act & Assert
            var finalAlbum = album;
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbum.reorderTracks(invalidOrder);
            });
            assertTrue(exception.getMessage().contains("not found"));
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
            var track = createTestTrack(1, "Target Track");
            album = album.addTrack(track);

            // Act
            var result = album.getTrack(track.id());

            // Assert
            assertNotNull(result);
            assertEquals(track.id(), result.id());
            assertEquals(track.title(), result.title());
        }

        @Test
        @DisplayName("存在しないトラックを取得しようとすると例外が発生すること")
        void getTrackWithNonExistentIdShouldThrowException() {
            // Arrange
            var album = createTestAlbum();
            var nonExistentId = Track.Id.generate();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                album.getTrack(nonExistentId);
            });
            assertTrue(exception.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("トラックをトラック番号順にソートして取得できること")
        void getTracksSortedByTrackNoShouldReturnSortedList() {
            // Arrange
            var album = createTestAlbum();
            var track3 = createTestTrack(3, "Track 3");
            var track1 = createTestTrack(1, "Track 1");
            var track2 = createTestTrack(2, "Track 2");
            // わざと順不同で追加
            album = album.addTrack(track3).addTrack(track1).addTrack(track2);

            // Act
            var sorted = album.getTracksSortedByTrackNo();

            // Assert
            assertEquals(3, sorted.size());
            assertEquals(1, sorted.get(0).trackNo());
            assertEquals(2, sorted.get(1).trackNo());
            assertEquals(3, sorted.get(2).trackNo());
        }
    }

    @Nested
    @DisplayName("Album.Idのテスト")
    class IdTest {

        @Test
        @DisplayName("IDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            var id = Album.Id.generate();

            // Assert
            assertNotNull(id);
            assertNotNull(id.value());
            assertFalse(id.value().isBlank());
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            var validUuid = Album.Id.generate().value();

            // Act
            var id = Album.Id.of(validUuid);

            // Assert
            assertEquals(validUuid, id.value());
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Album.Id.of("");
            });
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Album.Id.of("invalid-uuid");
            });
        }

        @Test
        @DisplayName("同じ値のIDは等しいこと")
        void equalsWithSameValueShouldBeEqual() {
            // Arrange
            var value = Album.Id.generate().value();
            var id1 = Album.Id.of(value);
            var id2 = Album.Id.of(value);

            // Act & Assert
            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }
    }

    // テストヘルパーメソッド

    private Album createTestAlbum() {
        return Album.create(AlbumTitle.of("Test Album"), BusinessDate.of(2024, 1, 1), ArtistCredit.of("Test Artist"),
                null, null, null);
    }

    private Track createTestTrack(int trackNo, String title) {
        return Track.create(trackNo, TrackTitle.of(title), ArtistCredit.of("Test Artist"), null);
    }
}
