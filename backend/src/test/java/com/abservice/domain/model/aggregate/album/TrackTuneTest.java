package com.abservice.domain.model.aggregate.album;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;

@DisplayName("TrackTuneエンティティのテスト")
class TrackTuneTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("必須フィールドのみで生成できること")
        void createWithMinimalFieldsShouldSucceed() {
            // Arrange
            var seq = 1;
            var tuneId = Tune.Id.generate();

            // Act
            var trackTune = TrackTune.create(seq, tuneId, null, null, null);

            // Assert
            assertNotNull(trackTune);
            assertEquals(seq, trackTune.seq());
            assertEquals(tuneId, trackTune.tuneId());
            assertNull(trackTune.composerCreditOverride());
            assertNull(trackTune.arrangerCreditOverride());
            assertNull(trackTune.linkUrl());
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var seq = 1;
            var tuneId = Tune.Id.generate();
            var composerCredit = Credit.of("Composer");
            var arrangerCredit = Credit.of("Arranger");
            var url = Url.of("https://example.com");

            // Act
            var trackTune = TrackTune.create(seq, tuneId, composerCredit, arrangerCredit, url);

            // Assert
            assertNotNull(trackTune);
            assertEquals(seq, trackTune.seq());
            assertEquals(tuneId, trackTune.tuneId());
            assertEquals(composerCredit, trackTune.composerCreditOverride());
            assertEquals(arrangerCredit, trackTune.arrangerCreditOverride());
            assertEquals(url, trackTune.linkUrl());
        }

        @Test
        @DisplayName("tuneIdがnullでも生成できること（MC、環境音などの場合）")
        void createWithNullTuneIdShouldSucceed() {
            // Arrange
            var seq = 1;

            // Act
            var trackTune = TrackTune.create(seq, null, null, null, null);

            // Assert
            assertNotNull(trackTune);
            assertNull(trackTune.tuneId());
        }

        @Test
        @DisplayName("seqがnullの場合は例外が発生すること")
        void createWithNullSeqShouldThrowException() {
            // Arrange
            var tuneId = Tune.Id.generate();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                TrackTune.create(null, tuneId, null, null, null);
            });
            assertEquals("Seq cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("再構成テスト")
    class ReconstructTest {

        @Test
        @DisplayName("永続化層から再構成できること")
        void reconstructShouldSucceed() {
            // Arrange
            var seq = 1;
            var tuneId = Tune.Id.generate();
            var composerCredit = Credit.of("Composer");
            var arrangerCredit = Credit.of("Arranger");
            var url = Url.of("https://example.com");

            // Act
            var trackTune = TrackTune.reconstruct(seq, tuneId, composerCredit, arrangerCredit, url);

            // Assert
            assertNotNull(trackTune);
            assertEquals(seq, trackTune.seq());
            assertEquals(tuneId, trackTune.tuneId());
            assertEquals(composerCredit, trackTune.composerCreditOverride());
            assertEquals(arrangerCredit, trackTune.arrangerCreditOverride());
            assertEquals(url, trackTune.linkUrl());
        }
    }

    @Nested
    @DisplayName("チューンID変更テスト")
    class ChangeTuneIdTest {

        @Test
        @DisplayName("チューンIDを変更できること")
        void changeTuneIdShouldSucceed() {
            // Arrange
            var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var newTuneId = Tune.Id.generate();

            // Act
            var updated = original.changeTuneId(newTuneId);

            // Assert
            assertEquals(newTuneId, updated.tuneId());
            assertEquals(original.seq(), updated.seq());
        }

        @Test
        @DisplayName("チューンIDをnullに変更できること")
        void changeTuneIdToNullShouldSucceed() {
            // Arrange
            var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);

            // Act
            var updated = original.changeTuneId(null);

            // Assert
            assertNull(updated.tuneId());
        }
    }

    @Nested
    @DisplayName("作曲者クレジット上書き変更テスト")
    class ChangeComposerCreditOverrideTest {

        @Test
        @DisplayName("作曲者クレジット上書きを変更できること")
        void changeComposerCreditOverrideShouldSucceed() {
            // Arrange
            var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var composerCredit = Credit.of("New Composer");

            // Act
            var updated = original.changeComposerCreditOverride(composerCredit);

            // Assert
            assertEquals(composerCredit, updated.composerCreditOverride());
        }

        @Test
        @DisplayName("作曲者クレジット上書きをnullに変更できること")
        void changeComposerCreditOverrideToNullShouldSucceed() {
            // Arrange
            var composerCredit = Credit.of("Composer");
            var original = TrackTune.create(1, Tune.Id.generate(), composerCredit, null, null);

            // Act
            var updated = original.changeComposerCreditOverride(null);

            // Assert
            assertNull(updated.composerCreditOverride());
        }

        @Test
        @DisplayName("作曲者クレジット上書きを変更しても他のフィールドは変わらないこと")
        void changeComposerCreditOverrideShouldNotAffectOtherFields() {
            // Arrange
            var tuneId = Tune.Id.generate();
            var arrangerCredit = Credit.of("Arranger");
            var url = Url.of("https://example.com");
            var original = TrackTune.create(1, tuneId, null, arrangerCredit, url);
            var composerCredit = Credit.of("Composer");

            // Act
            var updated = original.changeComposerCreditOverride(composerCredit);

            // Assert
            assertEquals(original.seq(), updated.seq());
            assertEquals(original.tuneId(), updated.tuneId());
            assertEquals(original.arrangerCreditOverride(), updated.arrangerCreditOverride());
            assertEquals(original.linkUrl(), updated.linkUrl());
        }
    }

    @Nested
    @DisplayName("アレンジャークレジット上書き変更テスト")
    class ChangeArrangerCreditOverrideTest {

        @Test
        @DisplayName("アレンジャークレジット上書きを変更できること")
        void changeArrangerCreditOverrideShouldSucceed() {
            // Arrange
            var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var arrangerCredit = Credit.of("New Arranger");

            // Act
            var updated = original.changeArrangerCreditOverride(arrangerCredit);

            // Assert
            assertEquals(arrangerCredit, updated.arrangerCreditOverride());
        }

        @Test
        @DisplayName("アレンジャークレジット上書きをnullに変更できること")
        void changeArrangerCreditOverrideToNullShouldSucceed() {
            // Arrange
            var arrangerCredit = Credit.of("Arranger");
            var original = TrackTune.create(1, Tune.Id.generate(), null, arrangerCredit, null);

            // Act
            var updated = original.changeArrangerCreditOverride(null);

            // Assert
            assertNull(updated.arrangerCreditOverride());
        }

        @Test
        @DisplayName("アレンジャークレジット上書きを変更しても他のフィールドは変わらないこと")
        void changeArrangerCreditOverrideShouldNotAffectOtherFields() {
            // Arrange
            var tuneId = Tune.Id.generate();
            var composerCredit = Credit.of("Composer");
            var url = Url.of("https://example.com");
            var original = TrackTune.create(1, tuneId, composerCredit, null, url);
            var arrangerCredit = Credit.of("Arranger");

            // Act
            var updated = original.changeArrangerCreditOverride(arrangerCredit);

            // Assert
            assertEquals(original.seq(), updated.seq());
            assertEquals(original.tuneId(), updated.tuneId());
            assertEquals(original.composerCreditOverride(), updated.composerCreditOverride());
            assertEquals(original.linkUrl(), updated.linkUrl());
        }
    }

    @Nested
    @DisplayName("リンクURL変更テスト")
    class ChangeLinkUrlTest {

        @Test
        @DisplayName("リンクURLを変更できること")
        void changeLinkUrlShouldSucceed() {
            // Arrange
            var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            var url = Url.of("https://example.com");

            // Act
            var updated = original.changeLinkUrl(url);

            // Assert
            assertEquals(url, updated.linkUrl());
        }

        @Test
        @DisplayName("リンクURLをnullに変更できること")
        void changeLinkUrlToNullShouldSucceed() {
            // Arrange
            var url = Url.of("https://example.com");
            var original = TrackTune.create(1, Tune.Id.generate(), null, null, url);

            // Act
            var updated = original.changeLinkUrl(null);

            // Assert
            assertNull(updated.linkUrl());
        }

        @Test
        @DisplayName("リンクURLを変更しても他のフィールドは変わらないこと")
        void changeLinkUrlShouldNotAffectOtherFields() {
            // Arrange
            var tuneId = Tune.Id.generate();
            var composerCredit = Credit.of("Composer");
            var arrangerCredit = Credit.of("Arranger");
            var original = TrackTune.create(1, tuneId, composerCredit, arrangerCredit, null);
            var url = Url.of("https://example.com");

            // Act
            var updated = original.changeLinkUrl(url);

            // Assert
            assertEquals(original.seq(), updated.seq());
            assertEquals(original.tuneId(), updated.tuneId());
            assertEquals(original.composerCreditOverride(), updated.composerCreditOverride());
            assertEquals(original.arrangerCreditOverride(), updated.arrangerCreditOverride());
        }
    }

    @Nested
    @DisplayName("等価性テスト")
    class EqualsTest {

        @Test
        @DisplayName("同じseqのTrackTuneは等しいこと")
        void trackTunesWithSameSeqShouldBeEqual() {
            // Arrange
            var tuneId1 = Tune.Id.generate();
            var tuneId2 = Tune.Id.generate();
            var trackTune1 = TrackTune.create(1, tuneId1, null, null, null);
            var trackTune2 = TrackTune.create(1, tuneId2, null, null, null);

            // Act & Assert
            assertEquals(trackTune1, trackTune2);
            assertEquals(trackTune1.hashCode(), trackTune2.hashCode());
        }

        @Test
        @DisplayName("異なるseqのTrackTuneは等しくないこと")
        void trackTunesWithDifferentSeqShouldNotBeEqual() {
            // Arrange
            var tuneId = Tune.Id.generate();
            var trackTune1 = TrackTune.create(1, tuneId, null, null, null);
            var trackTune2 = TrackTune.create(2, tuneId, null, null, null);

            // Act & Assert
            assertNotEquals(trackTune1, trackTune2);
        }
    }
}
