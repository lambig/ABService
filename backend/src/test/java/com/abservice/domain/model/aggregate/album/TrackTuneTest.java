package com.abservice.domain.model.aggregate.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.album.TrackTuneTitle;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;

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
            final var trackTune = TrackTune.create(
                    seq,
                    tuneId,
                    null,
                    null,
                    null,
                    null);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.seq()).isEqualTo(seq);
            assertThat(trackTune.tuneId()).isEqualTo(tuneId);
            assertThat(trackTune.tuneTitle()).isNull();
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
            final var tuneTitle = TrackTuneTitle.of("The Butterfly");
            final var composerCredit = Credit.of("Composer");
            final var arrangerCredit = Credit.of("Arranger");
            final var url = Url.of("https://example.com");

            // Act
            final var trackTune = TrackTune.create(
                    seq,
                    tuneId,
                    tuneTitle,
                    composerCredit,
                    arrangerCredit,
                    url);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.seq()).isEqualTo(seq);
            assertThat(trackTune.tuneId()).isEqualTo(tuneId);
            assertThat(trackTune.tuneTitle()).isEqualTo(tuneTitle);
            assertThat(trackTune.composerCreditOverride()).isEqualTo(composerCredit);
            assertThat(trackTune.arrangerCreditOverride()).isEqualTo(arrangerCredit);
            assertThat(trackTune.linkUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("tuneIdがnullでも生成できること（同定を行わない場合・MC、環境音などの場合）")
        void createWithNullTuneIdShouldSucceed() {
            // Arrange
            final var seq = 1;
            final var tuneTitle = TrackTuneTitle.of("MC");

            // Act
            final var trackTune = TrackTune.create(
                    seq,
                    null,
                    tuneTitle,
                    null,
                    null,
                    null);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.tuneId()).isNull();
            assertThat(trackTune.tuneTitle()).isEqualTo(tuneTitle);
        }

        @Test
        @DisplayName("seqがnullの場合は例外が発生すること")
        void createWithNullSeqShouldThrowException() {
            // Arrange
            final var tuneId = Tune.Id.generate();

            // Act & Assert
            assertThatThrownBy(() -> {
                TrackTune.create(
                        null,
                        tuneId,
                        null,
                        null,
                        null,
                        null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Seq cannot be null");
        }

        @Test
        @DisplayName("seqが0以下の場合は例外が発生すること")
        void createWithNonPositiveSeqShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                TrackTune.create(
                        0,
                        null,
                        null,
                        null,
                        null,
                        null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Seq must be a positive integer");
            assertThatThrownBy(() -> {
                TrackTune.create(
                        -1,
                        null,
                        null,
                        null,
                        null,
                        null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Seq must be a positive integer");
        }
    }

    @Nested
    @DisplayName("外部入力からの生成テスト")
    class FromInputTest {

        @Test
        @DisplayName("正常な入力は成功し、tuneIdを持たないTrackTuneを生成すること")
        void validInputSucceeds() {
            // Act
            final var result = TrackTune.fromInput(
                    1,
                    "The Butterfly",
                    "Trad.",
                    "Arranger",
                    "https://example.com");

            // Assert
            assertThat(result).isInstanceOf(Result.Success.class);
            final var trackTune = result.resolve();
            assertThat(trackTune.seq()).isEqualTo(1);
            assertThat(trackTune.tuneId()).isNull();
            assertThat(trackTune.tuneTitle().value()).isEqualTo("The Butterfly");
            assertThat(trackTune.composerCreditOverride().value()).isEqualTo("Trad.");
            assertThat(trackTune.arrangerCreditOverride().value()).isEqualTo("Arranger");
            assertThat(trackTune.linkUrl().value()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("空白のみの任意項目は未指定として扱われること")
        void blankOptionalFieldsAreTreatedAsUnspecified() {
            // Act
            final var result = TrackTune.fromInput(
                    1,
                    "   ",
                    null,
                    null,
                    null);

            // Assert
            assertThat(result).isInstanceOf(Result.Success.class);
            final var trackTune = result.resolve();
            assertThat(trackTune.tuneTitle()).isNull();
            assertThat(trackTune.composerCreditOverride()).isNull();
            assertThat(trackTune.linkUrl()).isNull();
        }

        @Test
        @DisplayName("seqが未指定ならエラーになること")
        void nullSeqFails() {
            // Act
            final var result = TrackTune.fromInput(
                    null,
                    "The Butterfly",
                    null,
                    null,
                    null);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                    .contains("SEQ_REQUIRED");
        }

        @Test
        @DisplayName("seqが0以下ならエラーになること")
        void nonPositiveSeqFails() {
            // Act
            final var result = TrackTune.fromInput(
                    0,
                    "The Butterfly",
                    null,
                    null,
                    null);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                    .containsExactly("SEQ_NOT_POSITIVE");
        }

        @Test
        @DisplayName("チューン名が長すぎる場合とseq未指定は同時に集約されること")
        void errorsAreAggregated() {
            // Act
            final var result = TrackTune.fromInput(
                    null,
                    "a".repeat(256),
                    null,
                    null,
                    null);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                    .contains("SEQ_REQUIRED", "TRACK_TUNE_TITLE_TOO_LONG");
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
            final var tuneTitle = TrackTuneTitle.of("The Butterfly");
            final var composerCredit = Credit.of("Composer");
            final var arrangerCredit = Credit.of("Arranger");
            final var url = Url.of("https://example.com");

            // Act
            final var trackTune = TrackTune.reconstruct(
                    seq,
                    tuneId,
                    tuneTitle,
                    composerCredit,
                    arrangerCredit,
                    url);

            // Assert
            assertThat(trackTune).isNotNull();
            assertThat(trackTune.seq()).isEqualTo(seq);
            assertThat(trackTune.tuneId()).isEqualTo(tuneId);
            assertThat(trackTune.tuneTitle()).isEqualTo(tuneTitle);
            assertThat(trackTune.composerCreditOverride()).isEqualTo(composerCredit);
            assertThat(trackTune.arrangerCreditOverride()).isEqualTo(arrangerCredit);
            assertThat(trackTune.linkUrl()).isEqualTo(url);
        }
    }

    @Nested
    @DisplayName("チューン名変更テスト")
    class ChangeTuneTitleTest {

        @Test
        @DisplayName("チューン名を変更できること")
        void changeTuneTitleShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(
                    1,
                    null,
                    TrackTuneTitle.of("Old Title"),
                    null,
                    null,
                    null);
            final var newTitle = TrackTuneTitle.of("New Title");

            // Act
            final var updated = original.changeTuneTitle(newTitle);

            // Assert
            assertThat(updated.tuneTitle()).isEqualTo(newTitle);
        }

        @Test
        @DisplayName("チューン名を変更しても他のフィールドは変わらないこと")
        void changeTuneTitleShouldNotAffectOtherFields() {
            // Arrange
            final var tuneId = Tune.Id.generate();
            final var composerCredit = Credit.of("Composer");
            final var url = Url.of("https://example.com");
            final var original = TrackTune.create(
                    1,
                    tuneId,
                    null,
                    composerCredit,
                    null,
                    url);

            // Act
            final var updated = original.changeTuneTitle(TrackTuneTitle.of("New Title"));

            // Assert
            assertThat(updated.seq()).isEqualTo(original.seq());
            assertThat(updated.tuneId()).isEqualTo(original.tuneId());
            assertThat(updated.composerCreditOverride()).isEqualTo(original.composerCreditOverride());
            assertThat(updated.linkUrl()).isEqualTo(original.linkUrl());
        }
    }

    @Nested
    @DisplayName("作曲者クレジット上書き変更テスト")
    class ChangeComposerCreditOverrideTest {

        @Test
        @DisplayName("作曲者クレジット上書きを変更できること")
        void changeComposerCreditOverrideShouldSucceed() {
            // Arrange
            final var original = TrackTune.create(
                    1,
                    Tune.Id.generate(),
                    null,
                    null,
                    null,
                    null);
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
            final var original = TrackTune.create(
                    1,
                    Tune.Id.generate(),
                    null,
                    composerCredit,
                    null,
                    null);

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
            final var original = TrackTune.create(
                    1,
                    tuneId,
                    null,
                    null,
                    arrangerCredit,
                    url);
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
            final var original = TrackTune.create(
                    1,
                    Tune.Id.generate(),
                    null,
                    null,
                    null,
                    null);
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
            final var original = TrackTune.create(
                    1,
                    Tune.Id.generate(),
                    null,
                    null,
                    arrangerCredit,
                    null);

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
            final var original = TrackTune.create(
                    1,
                    tuneId,
                    null,
                    composerCredit,
                    null,
                    url);
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
            final var original = TrackTune.create(
                    1,
                    Tune.Id.generate(),
                    null,
                    null,
                    null,
                    null);
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
            final var original = TrackTune.create(
                    1,
                    Tune.Id.generate(),
                    null,
                    null,
                    null,
                    url);

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
            final var original = TrackTune.create(
                    1,
                    tuneId,
                    null,
                    composerCredit,
                    arrangerCredit,
                    null);
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
            final var trackTune1 = TrackTune.create(
                    1,
                    tuneId1,
                    null,
                    null,
                    null,
                    null);
            final var trackTune2 = TrackTune.create(
                    1,
                    tuneId2,
                    null,
                    null,
                    null,
                    null);

            // Act & Assert
            assertThat(trackTune2).isEqualTo(trackTune1);
            assertThat(trackTune2.hashCode()).isEqualTo(trackTune1.hashCode());
        }

        @Test
        @DisplayName("異なるseqのTrackTuneは等しくないこと")
        void trackTunesWithDifferentSeqShouldNotBeEqual() {
            // Arrange
            final var tuneId = Tune.Id.generate();
            final var trackTune1 = TrackTune.create(
                    1,
                    tuneId,
                    null,
                    null,
                    null,
                    null);
            final var trackTune2 = TrackTune.create(
                    2,
                    tuneId,
                    null,
                    null,
                    null,
                    null);

            // Act & Assert
            assertThat(trackTune2).isNotEqualTo(trackTune1);
        }
    }
}
