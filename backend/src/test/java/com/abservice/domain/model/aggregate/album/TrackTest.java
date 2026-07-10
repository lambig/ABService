package com.abservice.domain.model.aggregate.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            final var trackNo = 1;
            final var title = TrackTitle.of("Track 1");
            final var artistCredit = ArtistCredit.of("Test Artist");
            final var recordingDate = BusinessDate.of(2024, 1, 15);

            // Act
            final var track = Track.create(trackNo, title, artistCredit, recordingDate);

            // Assert
            assertThat(track).isNotNull();
            assertThat(track.id()).isNotNull();
            assertThat(track.trackNo()).isEqualTo(trackNo);
            assertThat(track.title()).isEqualTo(title);
            assertThat(track.artistCredit()).isEqualTo(artistCredit);
            assertThat(track.recordingDate()).isEqualTo(recordingDate);
            assertThat(track.recordingPlace()).isNull();
            assertThat(track.isLive()).isNull();
            assertThat(track.getTunes().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            final var trackNo = 2;
            final var title = TrackTitle.of("Track 2");
            final var artistCredit = ArtistCredit.of("Full Artist");
            final var recordingDate = BusinessDate.of(2024, 5, 1);
            final var recordingPlace = "Studio ABC";
            final var isLive = false;

            // Act
            final var track = Track.create(trackNo, title, artistCredit, recordingDate, recordingPlace, isLive);

            // Assert
            assertThat(track).isNotNull();
            assertThat(track.trackNo()).isEqualTo(trackNo);
            assertThat(track.title()).isEqualTo(title);
            assertThat(track.artistCredit()).isEqualTo(artistCredit);
            assertThat(track.recordingDate()).isEqualTo(recordingDate);
            assertThat(track.recordingPlace()).isEqualTo(recordingPlace);
            assertThat(track.isLive()).isEqualTo(isLive);
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Arrange
            final var trackNo = 1;
            final var artistCredit = ArtistCredit.of("Artist");
            final var recordingDate = BusinessDate.of(2024, 1, 15);

            // Act & Assert
            assertThatThrownBy(() -> {
                Track.create(trackNo, null, artistCredit, recordingDate);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Track title cannot be null");
        }

        @Test
        @DisplayName("artistCreditがnullでも生成できること（アルバムから継承）")
        void createWithNullArtistCreditShouldSucceed() {
            // Arrange
            final var trackNo = 1;
            final var title = TrackTitle.of("Track 1");
            final var recordingDate = BusinessDate.of(2024, 1, 15);

            // Act
            final var track = Track.create(trackNo, title, null, recordingDate);

            // Assert
            assertThat(track).isNotNull();
            assertThat(track.artistCredit()).isNull();
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Original"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var newTitle = TrackTitle.of("Updated");

            // Act
            final var updated = track.changeTitle(newTitle);

            // Assert
            assertThat(updated.title()).isEqualTo(newTitle);
            assertThat(updated.id()).isEqualTo(track.id());
            assertThat(updated.trackNo()).isEqualTo(track.trackNo());
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleToNullShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Original"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            assertThatThrownBy(() -> {
                track.changeTitle(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Track title cannot be null");
        }
    }

    @Nested
    @DisplayName("アーティストクレジット変更テスト")
    class ChangeArtistCreditTest {

        @Test
        @DisplayName("アーティストクレジットを変更できること")
        void changeArtistCreditShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Original Artist"), BusinessDate.of(2024, 1, 1));
            final var newCredit = ArtistCredit.of("New Artist");

            // Act
            final var updated = track.changeArtistCredit(newCredit);

            // Assert
            assertThat(updated.artistCredit()).isEqualTo(newCredit);
        }

        @Test
        @DisplayName("アーティストクレジットをnullに変更できること")
        void changeArtistCreditToNullShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Original Artist"), BusinessDate.of(2024, 1, 1));

            // Act
            final var updated = track.changeArtistCredit(null);

            // Assert
            assertThat(updated.artistCredit()).isNull();
        }
    }

    @Nested
    @DisplayName("録音日変更テスト")
    class ChangeRecordingDateTest {

        @Test
        @DisplayName("録音日を変更できること")
        void changeRecordingDateShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var newDate = BusinessDate.of(2024, 12, 31);

            // Act
            final var updated = track.changeRecordingDate(newDate);

            // Assert
            assertThat(updated.recordingDate()).isEqualTo(newDate);
        }

        @Test
        @DisplayName("録音日をnullに変更できること")
        void changeRecordingDateToNullShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act
            final var updated = track.changeRecordingDate(null);

            // Assert
            assertThat(updated.recordingDate()).isNull();
        }
    }

    @Nested
    @DisplayName("録音場所変更テスト")
    class ChangeRecordingPlaceTest {

        @Test
        @DisplayName("録音場所を変更できること")
        void changeRecordingPlaceShouldSucceed() {
            // Arrange
            final var track = Track.create(
                    1,
                    TrackTitle.of("Track"),
                    ArtistCredit.of("Artist"),
                    BusinessDate.of(2024, 1, 1),
                    "Studio A",
                    false);
            final var newPlace = "Studio B";

            // Act
            final var updated = track.changeRecordingPlace(newPlace);

            // Assert
            assertThat(updated.recordingPlace()).isEqualTo(newPlace);
        }

        @Test
        @DisplayName("録音場所をnullに変更できること")
        void changeRecordingPlaceToNullShouldSucceed() {
            // Arrange
            final var track = Track.create(
                    1,
                    TrackTitle.of("Track"),
                    ArtistCredit.of("Artist"),
                    BusinessDate.of(2024, 1, 1),
                    "Studio A",
                    false);

            // Act
            final var updated = track.changeRecordingPlace(null);

            // Assert
            assertThat(updated.recordingPlace()).isNull();
        }
    }

    @Nested
    @DisplayName("ライブフラグ変更テスト")
    class ChangeIsLiveTest {

        @Test
        @DisplayName("ライブフラグを変更できること")
        void changeIsLiveShouldSucceed() {
            // Arrange
            final var track = Track.create(
                    1,
                    TrackTitle.of("Track"),
                    ArtistCredit.of("Artist"),
                    BusinessDate.of(2024, 1, 1),
                    "Studio",
                    false);

            // Act
            final var updated = track.changeIsLive(true);

            // Assert
            assertThat(updated.isLive()).isTrue();
        }

        @Test
        @DisplayName("ライブフラグをnullに変更できること")
        void changeIsLiveToNullShouldSucceed() {
            // Arrange
            final var track = Track.create(
                    1,
                    TrackTitle.of("Track"),
                    ArtistCredit.of("Artist"),
                    BusinessDate.of(2024, 1, 1),
                    "Studio",
                    true);

            // Act
            final var updated = track.changeIsLive(null);

            // Assert
            assertThat(updated.isLive()).isNull();
        }
    }

    @Nested
    @DisplayName("チューン追加テスト")
    class AddTuneTest {

        @Test
        @DisplayName("チューンを追加できること")
        void addTuneShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tuneId = Tune.Id.generate();
            final var trackTune = TrackTune.create(1, tuneId, null, null, null);

            // Act
            final var updated = track.addTune(trackTune);

            // Assert
            assertThat(updated.getTunes().size()).isEqualTo(1);
            assertThat(updated.getTunes().get(0)).isEqualTo(trackTune);
        }

        @Test
        @DisplayName("複数のチューンを追加できること")
        void addMultipleTunesShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var tune2 = TrackTune.create(2, Tune.Id.generate(), null, null, null);

            // Act
            final var updated = track.addTune(tune1).addTune(tune2);

            // Assert
            assertThat(updated.getTunes().size()).isEqualTo(2);
            assertThat(updated.getTunes().contains(tune1)).isTrue();
            assertThat(updated.getTunes().contains(tune2)).isTrue();
        }

        @Test
        @DisplayName("nullのチューンを追加しようとすると例外が発生すること")
        void addNullTuneShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            assertThatThrownBy(() -> {
                track.addTune(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune cannot be null");
        }

        @Test
        @DisplayName("重複したseqのチューンを追加しようとすると例外が発生すること")
        void addDuplicateSeqTuneShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var tune2 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var trackWithTune = track.addTune(tune1);

            // Act & Assert
            assertThatThrownBy(() -> {
                trackWithTune.addTune(tune2);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune seq 1 already exists in this track");
        }
    }

    @Nested
    @DisplayName("チューン削除テスト")
    class RemoveTuneTest {

        @Test
        @DisplayName("チューンを削除できること")
        void removeTuneShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tune = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var trackWithTune = track.addTune(tune);

            // Act
            final var updated = trackWithTune.removeTune(1);

            // Assert
            assertThat(updated.getTunes().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("複数のチューンから特定のチューンを削除できること")
        void removeSpecificTuneFromMultipleShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var tune2 = TrackTune.create(2, Tune.Id.generate(), null, null, null);
            final var tune3 = TrackTune.create(3, Tune.Id.generate(), null, null, null);
            final var trackWithTunes = track.addTune(tune1).addTune(tune2).addTune(tune3);

            // Act
            final var updated = trackWithTunes.removeTune(2);

            // Assert
            assertThat(updated.getTunes().size()).isEqualTo(2);
            assertThat(updated.getTunes().contains(tune1)).isTrue();
            assertThat(updated.getTunes().contains(tune2)).isFalse();
            assertThat(updated.getTunes().contains(tune3)).isTrue();
        }

        @Test
        @DisplayName("nullのseqで削除しようとすると例外が発生すること")
        void removeWithNullSeqShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            assertThatThrownBy(() -> {
                track.removeTune(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Seq cannot be null");
        }

        @Test
        @DisplayName("存在しないseqで削除しようとすると例外が発生すること")
        void removeNonExistentSeqShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            assertThatThrownBy(() -> {
                track.removeTune(999);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune with seq 999 not found");
        }
    }

    @Nested
    @DisplayName("チューン更新テスト")
    class UpdateTuneTest {

        @Test
        @DisplayName("チューンを更新できること")
        void updateTuneShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var originalTune = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var trackWithTune = track.addTune(originalTune);

            final var composerCredit = Credit.of("New Composer");
            final var updatedTune = originalTune.changeComposerCreditOverride(composerCredit);

            // Act
            final var updated = trackWithTune.updateTune(updatedTune);

            // Assert
            assertThat(updated.getTunes().size()).isEqualTo(1);
            assertThat(updated.getTunes().get(0).composerCreditOverride()).isEqualTo(composerCredit);
        }

        @Test
        @DisplayName("複数のチューンの中から特定のチューンを更新できること")
        void updateSpecificTuneFromMultipleShouldSucceed() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tune1 = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var tune2 = TrackTune.create(2, Tune.Id.generate(), null, null, null);
            final var trackWithTunes = track.addTune(tune1).addTune(tune2);

            final var url = Url.of("https://example.com");
            final var updatedTune2 = tune2.changeLinkUrl(url);

            // Act
            final var updated = trackWithTunes.updateTune(updatedTune2);

            // Assert
            assertThat(updated.getTunes().size()).isEqualTo(2);
            assertThat(updated.getTunes().get(0).linkUrl()).isNull();
            assertThat(updated.getTunes().get(1).linkUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("nullのチューンで更新しようとすると例外が発生すること")
        void updateWithNullTuneShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));

            // Act & Assert
            assertThatThrownBy(() -> {
                track.updateTune(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Updated tune cannot be null");
        }

        @Test
        @DisplayName("存在しないseqのチューンで更新しようとすると例外が発生すること")
        void updateNonExistentTuneShouldThrowException() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var nonExistentTune = TrackTune.create(999, Tune.Id.generate(), null, null, null);

            // Act & Assert
            assertThatThrownBy(() -> {
                track.updateTune(nonExistentTune);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune with seq 999 not found");
        }
    }

    @Nested
    @DisplayName("チューンリスト取得テスト")
    class GetTunesTest {

        @Test
        @DisplayName("チューンリストが不変であること")
        void getTunesShouldReturnUnmodifiableList() {
            // Arrange
            final var track = Track
                    .create(1, TrackTitle.of("Track"), ArtistCredit.of("Artist"), BusinessDate.of(2024, 1, 1));
            final var tune = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var trackWithTune = track.addTune(tune);

            // Act
            final var tunes = trackWithTune.getTunes();

            // Assert
            assertThatThrownBy(() -> {
                tunes.add(TrackTune.create(2, Tune.Id.generate(), null, null, null));
            }).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("ID型テスト")
    class IdTest {

        @Test
        @DisplayName("UUIDv7形式のIDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            final var id = Track.Id.generate();

            // Assert
            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
            assertThat(id.value().isBlank()).isFalse();
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofShouldCreateIdFromString() {
            // Arrange
            final var id1 = Track.Id.generate();
            final var value = id1.value();

            // Act
            final var id2 = Track.Id.of(value);

            // Assert
            assertThat(id2).isEqualTo(id1);
            assertThat(id2.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("nullの文字列からIDを生成しようとすると例外が発生すること")
        void ofWithNullShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Track.Id.of(null);
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Track.Id.of("");
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Track.Id.of("not-a-uuid");
            }).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
