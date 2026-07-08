package com.abservice.domain.model.aggregate.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            final var seq = 1;
            final var tuneId = Tune.Id.generate();

            // Act
            final var trackTune = TrackTune.create(seq, tuneId, null, null, null);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.seq()).isEqualTo(seq);
            assertThat(trackTune.tuneId()).isEqualTo(tuneId);
            assertThat(trackTune.composerCreditOverride()).isNull();
            assertThat(trackTune.arrangerCreditOverride()).isNull();
            assertThat(trackTune.linkUrl()).isNull();
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            final var seq = 1;
            final var tuneId = Tune.Id.generate();
            final var composerCredit = Credit.of("Composer");
            final var arrangerCredit = Credit.of("Arranger");
            final var url = Url.of("https://example.com");

            // Act
            final var trackTune = TrackTune.create(seq, tuneId, composerCredit, arrangerCredit, url);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.seq()).isEqualTo(seq);
            assertThat(trackTune.tuneId()).isEqualTo(tuneId);
            assertThat(trackTune.composerCreditOverride()).isEqualTo(composerCredit);
            assertThat(trackTune.arrangerCreditOverride()).isEqualTo(arrangerCredit);
            assertThat(trackTune.linkUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("tuneIdがnullでも生成できること（MC、環境音などの場合）")
        void createWithNullTuneIdShouldSucceed() {
            // Arrange
            final var seq = 1;

            // Act
            final var trackTune = TrackTune.create(seq, null, null, null, null);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.tuneId()).isNull();
        }

        @Test
        @DisplayName("seqがnullの場合は例外が発生すること")
        void createWithNullSeqShouldThrowException() {
            // Arrange
            final var tuneId = Tune.Id.generate();

            // Act & Assert
            assertThatThrownBy(() -> {
                TrackTune.create(null, tuneId, null, null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Seq cannot be null");
        }
    }

    @Nested
    @DisplayName("再構成テスト")
    class ReconstructTest {

        @Test
        @DisplayName("永続化層から再構成できること")
        void reconstructShouldSucceed() {
            // Arrange
            final var seq = 1;
            final var tuneId = Tune.Id.generate();
            final var composerCredit = Credit.of("Composer");
            final var arrangerCredit = Credit.of("Arranger");
            final var url = Url.of("https://example.com");

            // Act
            final var trackTune = TrackTune.reconstruct(seq, tuneId, composerCredit, arrangerCredit, url);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.seq()).isEqualTo(seq);
            assertThat(trackTune.tuneId()).isEqualTo(tuneId);
            assertThat(trackTune.composerCreditOverride()).isEqualTo(composerCredit);
            assertThat(trackTune.arrangerCreditOverride()).isEqualTo(arrangerCredit);
            assertThat(trackTune.linkUrl()).isEqualTo(url);
        }
    }

    @Nested
    @DisplayName("チューンID変更テスト")
    class ChangeTuneIdTest {

        @Test
        @DisplayName("チューンIDを変更できること")
        void changeTuneIdShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var newTuneId = Tune.Id.generate();

            // Act
            final var updated = original.changeTuneId(newTuneId);

            // Assert
            assertThat(updated.tuneId()).isEqualTo(newTuneId);
            assertThat(updated.seq()).isEqualTo(original.seq());
        }

        @Test
        @DisplayName("チューンIDをnullに変更できること")
        void changeTuneIdToNullShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);

            // Act
            final var updated = original.changeTuneId(null);

            // Assert
            assertThat(updated.tuneId()).isNull();
        }
    }

    @Nested
    @DisplayName("作曲者クレジット上書き変更テスト")
    class ChangeComposerCreditOverrideTest {

        @Test
        @DisplayName("作曲者クレジット上書きを変更できること")
        void changeComposerCreditOverrideShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var composerCredit = Credit.of("New Composer");

            // Act
            final var updated = original.changeComposerCreditOverride(composerCredit);

            // Assert
            assertThat(updated.composerCreditOverride()).isEqualTo(composerCredit);
        }

        @Test
        @DisplayName("作曲者クレジット上書きをnullに変更できること")
        void changeComposerCreditOverrideToNullShouldSucceed() {
            // Arrange
            final var composerCredit = Credit.of("Composer");
            final var original = TrackTune.create(1, Tune.Id.generate(), composerCredit, null, null);

            // Act
            final var updated = original.changeComposerCreditOverride(null);

            // Assert
            assertThat(updated.composerCreditOverride()).isNull();
        }

        @Test
        @DisplayName("作曲者クレジット上書きを変更しても他のフィールドは変わらないこと")
        void changeComposerCreditOverrideShouldNotAffectOtherFields() {
            // Arrange
            final var tuneId = Tune.Id.generate();
            final var arrangerCredit = Credit.of("Arranger");
            final var url = Url.of("https://example.com");
            final var original = TrackTune.create(1, tuneId, null, arrangerCredit, url);
            final var composerCredit = Credit.of("Composer");

            // Act
            final var updated = original.changeComposerCreditOverride(composerCredit);

            // Assert
            assertThat(updated.seq()).isEqualTo(original.seq());
            assertThat(updated.tuneId()).isEqualTo(original.tuneId());
            assertThat(updated.arrangerCreditOverride()).isEqualTo(original.arrangerCreditOverride());
            assertThat(updated.linkUrl()).isEqualTo(original.linkUrl());
        }
    }

    @Nested
    @DisplayName("アレンジャークレジット上書き変更テスト")
    class ChangeArrangerCreditOverrideTest {

        @Test
        @DisplayName("アレンジャークレジット上書きを変更できること")
        void changeArrangerCreditOverrideShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var arrangerCredit = Credit.of("New Arranger");

            // Act
            final var updated = original.changeArrangerCreditOverride(arrangerCredit);

            // Assert
            assertThat(updated.arrangerCreditOverride()).isEqualTo(arrangerCredit);
        }

        @Test
        @DisplayName("アレンジャークレジット上書きをnullに変更できること")
        void changeArrangerCreditOverrideToNullShouldSucceed() {
            // Arrange
            final var arrangerCredit = Credit.of("Arranger");
            final var original = TrackTune.create(1, Tune.Id.generate(), null, arrangerCredit, null);

            // Act
            final var updated = original.changeArrangerCreditOverride(null);

            // Assert
            assertThat(updated.arrangerCreditOverride()).isNull();
        }

        @Test
        @DisplayName("アレンジャークレジット上書きを変更しても他のフィールドは変わらないこと")
        void changeArrangerCreditOverrideShouldNotAffectOtherFields() {
            // Arrange
            final var tuneId = Tune.Id.generate();
            final var composerCredit = Credit.of("Composer");
            final var url = Url.of("https://example.com");
            final var original = TrackTune.create(1, tuneId, composerCredit, null, url);
            final var arrangerCredit = Credit.of("Arranger");

            // Act
            final var updated = original.changeArrangerCreditOverride(arrangerCredit);

            // Assert
            assertThat(updated.seq()).isEqualTo(original.seq());
            assertThat(updated.tuneId()).isEqualTo(original.tuneId());
            assertThat(updated.composerCreditOverride()).isEqualTo(original.composerCreditOverride());
            assertThat(updated.linkUrl()).isEqualTo(original.linkUrl());
        }
    }

    @Nested
    @DisplayName("リンクURL変更テスト")
    class ChangeLinkUrlTest {

        @Test
        @DisplayName("リンクURLを変更できること")
        void changeLinkUrlShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(1, Tune.Id.generate(), null, null, null);
            final var url = Url.of("https://example.com");

            // Act
            final var updated = original.changeLinkUrl(url);

            // Assert
            assertThat(updated.linkUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("リンクURLをnullに変更できること")
        void changeLinkUrlToNullShouldSucceed() {
            // Arrange
            final var url = Url.of("https://example.com");
            final var original = TrackTune.create(1, Tune.Id.generate(), null, null, url);

            // Act
            final var updated = original.changeLinkUrl(null);

            // Assert
            assertThat(updated.linkUrl()).isNull();
        }

        @Test
        @DisplayName("リンクURLを変更しても他のフィールドは変わらないこと")
        void changeLinkUrlShouldNotAffectOtherFields() {
            // Arrange
            final var tuneId = Tune.Id.generate();
            final var composerCredit = Credit.of("Composer");
            final var arrangerCredit = Credit.of("Arranger");
            final var original = TrackTune.create(1, tuneId, composerCredit, arrangerCredit, null);
            final var url = Url.of("https://example.com");

            // Act
            final var updated = original.changeLinkUrl(url);

            // Assert
            assertThat(updated.seq()).isEqualTo(original.seq());
            assertThat(updated.tuneId()).isEqualTo(original.tuneId());
            assertThat(updated.composerCreditOverride()).isEqualTo(original.composerCreditOverride());
            assertThat(updated.arrangerCreditOverride()).isEqualTo(original.arrangerCreditOverride());
        }
    }

    @Nested
    @DisplayName("等価性テスト")
    class EqualsTest {

        @Test
        @DisplayName("同じseqのTrackTuneは等しいこと")
        void trackTunesWithSameSeqShouldBeEqual() {
            // Arrange
            final var tuneId1 = Tune.Id.generate();
            final var tuneId2 = Tune.Id.generate();
            final var trackTune1 = TrackTune.create(1, tuneId1, null, null, null);
            final var trackTune2 = TrackTune.create(1, tuneId2, null, null, null);

            // Act & Assert
            assertThat(trackTune2).isEqualTo(trackTune1);
            assertThat(trackTune2.hashCode()).isEqualTo(trackTune1.hashCode());
        }

        @Test
        @DisplayName("異なるseqのTrackTuneは等しくないこと")
        void trackTunesWithDifferentSeqShouldNotBeEqual() {
            // Arrange
            final var tuneId = Tune.Id.generate();
            final var trackTune1 = TrackTune.create(1, tuneId, null, null, null);
            final var trackTune2 = TrackTune.create(2, tuneId, null, null, null);

            // Act & Assert
            assertThat(trackTune2).isNotEqualTo(trackTune1);
        }
    }
}
