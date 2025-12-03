package com.abservice.domain.model.aggregate.album;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;

@DisplayName("Trackエンティティのテスト")
class TrackTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("必須フィールドのみで生成できること")
        void createWithMinimalFieldsShouldSucceed() {
            // Arrange
            var trackNo = 1;
            var title = TrackTitle.of("Track 1");
            var artistCredit = ArtistCredit.of("Test Artist");
            var recordingDate = BusinessDate.of(2024, 1, 15);

            // Act
            var track = Track.create(trackNo, title, artistCredit, recordingDate);

            // Assert
            assertNotNull(track);
            assertNotNull(track.id());
            assertEquals(trackNo, track.trackNo());
            assertEquals(title, track.title());
            assertEquals(artistCredit, track.artistCredit());
            assertEquals(recordingDate, track.recordingDate());
            assertNull(track.recordingPlace());
            assertNull(track.isLive());
            assertTrue(track.getTunes().isEmpty());
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var trackNo = 2;
            var title = TrackTitle.of("Track 2");
            var artistCredit = ArtistCredit.of("Full Artist");
            var recordingDate = BusinessDate.of(2024, 5, 1);
            var recordingPlace = "Studio ABC";
            var isLive = false;

            // Act
            var track = Track.create(trackNo, title, artistCredit, recordingDate, recordingPlace, isLive);

            // Assert
            assertNotNull(track);
            assertEquals(trackNo, track.trackNo());
            assertEquals(title, track.title());
            assertEquals(artistCredit, track.artistCredit());
            assertEquals(recordingDate, track.recordingDate());
            assertEquals(recordingPlace, track.recordingPlace());
            assertEquals(isLive, track.isLive());
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Arrange
            var trackNo = 1;
            var artistCredit = ArtistCredit.of("Artist");
            var recordingDate = BusinessDate.of(2024, 1, 15);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Track.create(trackNo, null, artistCredit, recordingDate);
            });
            assertEquals("Track title cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("artistCreditがnullでも生成できること（アルバムから継承）")
        void createWithNullArtistCreditShouldSucceed() {
            // Arrange
            var trackNo = 1;
            var title = TrackTitle.of("Track 1");
            var recordingDate = BusinessDate.of(2024, 1, 15);

            // Act
            var track = Track.create(trackNo, title, null, recordingDate);

            // Assert
            assertNotNull(track);
            assertNull(track.artistCredit());
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Original"), ArtistCredit.of("Artist"),
                    BusinessDate.of(2024, 1, 1));
            var newTitle = TrackTitle.of("Updated");

            // Act
            var updated = track.changeTitle(newTitle);

            // Assert
            assertEquals(newTitle, updated.title());
            assertEquals(track.id(), updated.id());
            assertEquals(track.trackNo(), updated.trackNo());
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleToNullShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Original"), ArtistCredit.of("Artist"),
                    BusinessDate.of(2024, 1, 1));

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                track.changeTitle(null);
            });
            assertEquals("Track title cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("アーティストクレジット変更テスト")
    class ChangeArtistCreditTest {

        @Test
        @DisplayName("アーティストクレジットを変更できること")
        void changeArtistCreditShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Original Artist"),
                    BusinessDate.of(2024, 1, 1));
            var newCredit = ArtistCredit.of("New Artist");

            // Act
            var updated = track.changeArtistCredit(newCredit);

            // Assert
            assertEquals(newCredit, updated.artistCredit());
        }

        @Test
        @DisplayName("アーティストクレジットをnullに変更できること")
        void changeArtistCreditToNullShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Original Artist"),
                    BusinessDate.of(2024, 1, 1));

            // Act
            var updated = track.changeArtistCredit(null);

            // Assert
            assertNull(updated.artistCredit());
        }
    }

    @Nested
    @DisplayName("録音日変更テスト")
    class ChangeRecordingDateTest {

        @Test
        @DisplayName("録音日を変更できること")
        void changeRecordingDateShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var newDate = BusinessDate.of(2024, 12, 31);

            // Act
            var updated = track.changeRecordingDate(newDate);

            // Assert
            assertEquals(newDate, updated.recordingDate());
        }

        @Test
        @DisplayName("録音日をnullに変更できること")
        void changeRecordingDateToNullShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act
            var updated = track.changeRecordingDate(null);

            // Assert
            assertNull(updated.recordingDate());
        }
    }

    @Nested
    @DisplayName("録音場所変更テスト")
    class ChangeRecordingPlaceTest {

        @Test
        @DisplayName("録音場所を変更できること")
        void changeRecordingPlaceShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1),
                    "Studio A", false);
            var newPlace = "Studio B";

            // Act
            var updated = track.changeRecordingPlace(newPlace);

            // Assert
            assertEquals(newPlace, updated.recordingPlace());
        }

        @Test
        @DisplayName("録音場所をnullに変更できること")
        void changeRecordingPlaceToNullShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1),
                    "Studio A", false);

            // Act
            var updated = track.changeRecordingPlace(null);

            // Assert
            assertNull(updated.recordingPlace());
        }
    }

    @Nested
    @DisplayName("ライブフラグ変更テスト")
    class ChangeIsLiveTest {

        @Test
        @DisplayName("ライブフラグを変更できること")
        void changeIsLiveShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1),
                    "Studio", false);

            // Act
            var updated = track.changeIsLive(true);

            // Assert
            assertTrue(updated.isLive());
        }

        @Test
        @DisplayName("ライブフラグをnullに変更できること")
        void changeIsLiveToNullShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1),
                    "Studio", true);

            // Act
            var updated = track.changeIsLive(null);

            // Assert
            assertNull(updated.isLive());
        }
    }

    @Nested
    @DisplayName("チューン追加テスト")
    class AddTuneTest {

        @Test
        @DisplayName("チューンを追加できること")
        void addTuneShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tuneId = Tune.Id.generate();
            var trackTune = TrackTune.create(1, tuneId, null, null, null);

            // Act
            var updated = track.addTune(trackTune);

            // Assert
            assertEquals(1, updated.getTunes().size());
            assertEquals(trackTune, updated.getTunes().get(0));
        }

        @Test
        @DisplayName("複数のチューンを追加できること")
        void addMultipleTunesShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var tune2 = TrackTune.create(2, Tune.Id.generate(), null, null, null);

            // Act
            var updated = track.addTune(tune1).addTune(tune2);

            // Assert
            assertEquals(2, updated.getTunes().size());
            assertTrue(updated.getTunes().contains(tune1));
            assertTrue(updated.getTunes().contains(tune2));
        }

        @Test
        @DisplayName("nullのチューンを追加しようとすると例外が発生すること")
        void addNullTuneShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                track.addTune(null);
            });
            assertEquals("Tune cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("重複したseqのチューンを追加しようとすると例外が発生すること")
        void addDuplicateSeqTuneShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var tune2 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var trackWithTune = track.addTune(tune1);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                trackWithTune.addTune(tune2);
            });
            assertEquals("Tune seq 1 already exists in this track", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("チューン削除テスト")
    class RemoveTuneTest {

        @Test
        @DisplayName("チューンを削除できること")
        void removeTuneShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tune = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var trackWithTune = track.addTune(tune);

            // Act
            var updated = trackWithTune.removeTune(1);

            // Assert
            assertTrue(updated.getTunes().isEmpty());
        }

        @Test
        @DisplayName("複数のチューンから特定のチューンを削除できること")
        void removeSpecificTuneFromMultipleShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var tune2 = TrackTune.create(2, Tune.Id.generate(), null, null, null);
            var tune3 = TrackTune.create(3, Tune.Id.generate(), null, null, null);
            var trackWithTunes = track.addTune(tune1).addTune(tune2).addTune(tune3);

            // Act
            var updated = trackWithTunes.removeTune(2);

            // Assert
            assertEquals(2, updated.getTunes().size());
            assertTrue(updated.getTunes().contains(tune1));
            assertFalse(updated.getTunes().contains(tune2));
            assertTrue(updated.getTunes().contains(tune3));
        }

        @Test
        @DisplayName("nullのseqで削除しようとすると例外が発生すること")
        void removeWithNullSeqShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                track.removeTune(null);
            });
            assertEquals("Seq cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("存在しないseqで削除しようとすると例外が発生すること")
        void removeNonExistentSeqShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                track.removeTune(999);
            });
            assertEquals("Tune with seq 999 not found", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("チューン更新テスト")
    class UpdateTuneTest {

        @Test
        @DisplayName("チューンを更新できること")
        void updateTuneShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var originalTune = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var trackWithTune = track.addTune(originalTune);

            var composerCredit = Credit.of("New Composer");
            var updatedTune = originalTune.changeComposerCreditOverride(composerCredit);

            // Act
            var updated = trackWithTune.updateTune(updatedTune);

            // Assert
            assertEquals(1, updated.getTunes().size());
            assertEquals(composerCredit, updated.getTunes().get(0).composerCreditOverride());
        }

        @Test
        @DisplayName("複数のチューンの中から特定のチューンを更新できること")
        void updateSpecificTuneFromMultipleShouldSucceed() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var tune2 = TrackTune.create(2, Tune.Id.generate(), null, null, null);
            var trackWithTunes = track.addTune(tune1).addTune(tune2);

            var url = Url.of("https://example.com");
            var updatedTune2 = tune2.changeLinkUrl(url);

            // Act
            var updated = trackWithTunes.updateTune(updatedTune2);

            // Assert
            assertEquals(2, updated.getTunes().size());
            assertNull(updated.getTunes().get(0).linkUrl());
            assertEquals(url, updated.getTunes().get(1).linkUrl());
        }

        @Test
        @DisplayName("nullのチューンで更新しようとすると例外が発生すること")
        void updateWithNullTuneShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                track.updateTune(null);
            });
            assertEquals("Updated tune cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("存在しないseqのチューンで更新しようとすると例外が発生すること")
        void updateNonExistentTuneShouldThrowException() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var nonExistentTune = TrackTune.create(999, Tune.Id.generate(), null, null, null);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                track.updateTune(nonExistentTune);
            });
            assertEquals("Tune with seq 999 not found", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("チューンリスト取得テスト")
    class GetTunesTest {

        @Test
        @DisplayName("チューンリストが不変であること")
        void getTunesShouldReturnUnmodifiableList() {
            // Arrange
            var track = Track.create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            var tune = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var trackWithTune = track.addTune(tune);

            // Act
            var tunes = trackWithTune.getTunes();

            // Assert
            assertThrows(UnsupportedOperationException.class, () -> {
                tunes.add(TrackTune.create(2, Tune.Id.generate(), null, null, null));
            });
        }
    }

    @Nested
    @DisplayName("ID型テスト")
    class IdTest {

        @Test
        @DisplayName("UUIDv7形式のIDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            var id = Track.Id.generate();

            // Assert
            assertNotNull(id);
            assertNotNull(id.value());
            assertFalse(id.value().isBlank());
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofShouldCreateIdFromString() {
            // Arrange
            var id1 = Track.Id.generate();
            var value = id1.value();

            // Act
            var id2 = Track.Id.of(value);

            // Assert
            assertEquals(id1, id2);
            assertEquals(value, id2.value());
        }

        @Test
        @DisplayName("nullの文字列からIDを生成しようとすると例外が発生すること")
        void ofWithNullShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Track.Id.of(null);
            });
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Track.Id.of("");
            });
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Track.Id.of("not-a-uuid");
            });
        }
    }
}
