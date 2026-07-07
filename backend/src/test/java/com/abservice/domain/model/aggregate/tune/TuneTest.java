package com.abservice.domain.model.aggregate.tune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;

@DisplayName("Tune集約のテスト")
class TuneTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("正常な値で生成できること")
        void createWithValidValuesShouldSucceed() {
            // Arrange
            var title = TuneTitle.of("Test Tune");
            var tuneKind = TuneKind.TRAD;
            var composerCredit = Credit.of("Test Composer");

            // Act
            var tune = Tune.create(title, tuneKind, composerCredit, null, null, null, null, null, null);

            // Assert
            assertThat(tune).isNotNull();
            assertThat(tune.id()).isNotNull();
            assertThat(tune.title()).isEqualTo(title);
            assertThat(tune.tuneKind()).isEqualTo(tuneKind);
            assertThat(tune.defaultComposerCredit()).isEqualTo(composerCredit);
            assertThat(tune.defaultArrangerCredit()).isNull();
            assertThat(tune.originalWorkTitle()).isNull();
            assertThat(tune.originalWorkCredit()).isNull();
            assertThat(tune.tuneType()).isNull();
            assertThat(tune.defaultKey()).isNull();
            assertThat(tune.defaultTempo()).isNull();
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var title = TuneTitle.of("Complete Tune");
            var tuneKind = TuneKind.ARRANGEMENT;
            var composerCredit = Credit.of("Original Composer");
            var arrangerCredit = Credit.of("Arranger Name");
            var originalWorkTitle = "Original Work Title";
            var originalWorkCredit = "Original Artist";
            var tuneType = "Reel";
            var defaultKey = "D Major";
            var defaultTempo = 120;

            // Act
            var tune = Tune.create(title, tuneKind, composerCredit, arrangerCredit, originalWorkTitle,
                    originalWorkCredit, tuneType, defaultKey, defaultTempo);

            // Assert
            assertThat(tune).isNotNull();
            assertThat(tune.defaultArrangerCredit()).isEqualTo(arrangerCredit);
            assertThat(tune.originalWorkTitle()).isEqualTo(originalWorkTitle);
            assertThat(tune.originalWorkCredit()).isEqualTo(originalWorkCredit);
            assertThat(tune.tuneType()).isEqualTo(tuneType);
            assertThat(tune.defaultKey()).isEqualTo(defaultKey);
            assertThat(tune.defaultTempo()).isEqualTo(defaultTempo);
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Arrange
            var tuneKind = TuneKind.ORIGINAL;
            var composerCredit = Credit.of("Composer");

            // Act & Assert
            assertThatThrownBy(() -> {
                Tune.create(null, tuneKind, composerCredit, null, null, null, null, null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune title cannot be null");
        }

        @Test
        @DisplayName("チューン種別がnullの場合は例外が発生すること")
        void createWithNullTuneKindShouldThrowException() {
            // Arrange
            var title = TuneTitle.of("Test Tune");
            var composerCredit = Credit.of("Composer");

            // Act & Assert
            assertThatThrownBy(() -> {
                Tune.create(title, null, composerCredit, null, null, null, null, null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune kind cannot be null");
        }
    }

    @Nested
    @DisplayName("タイトル変更テスト")
    class ChangeTitleTest {

        @Test
        @DisplayName("タイトルを変更できること")
        void changeTitleWithValidTitleShouldSucceed() {
            // Arrange
            var tune = createTestTune();
            var newTitle = TuneTitle.of("Updated Tune Title");

            // Act
            var updated = tune.changeTitle(newTitle);

            // Assert
            assertThat(updated.title()).isEqualTo(newTitle);
            assertThat(updated.id()).isEqualTo(tune.id()); // IDは変わらない
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            var tune = createTestTune();

            // Act & Assert
            assertThatThrownBy(() -> {
                tune.changeTitle(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tune title cannot be null");
        }
    }

    @Nested
    @DisplayName("作曲者クレジット変更テスト")
    class ChangeComposerCreditTest {

        @Test
        @DisplayName("作曲者クレジットを変更できること")
        void changeDefaultComposerCreditWithValidCreditShouldSucceed() {
            // Arrange
            var tune = createTestTune();
            var newCredit = Credit.of("New Composer");

            // Act
            var updated = tune.changeDefaultComposerCredit(newCredit);

            // Assert
            assertThat(updated.defaultComposerCredit()).isEqualTo(newCredit);
        }

        @Test
        @DisplayName("作曲者クレジットをnullに変更できること")
        void changeDefaultComposerCreditWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultComposerCredit(null);

            // Assert
            assertThat(updated.defaultComposerCredit()).isNull();
        }
    }

    @Nested
    @DisplayName("アレンジャークレジット変更テスト")
    class ChangeArrangerCreditTest {

        @Test
        @DisplayName("アレンジャークレジットを変更できること")
        void changeDefaultArrangerCreditWithValidCreditShouldSucceed() {
            // Arrange
            var tune = createTestTune();
            var newCredit = Credit.of("New Arranger");

            // Act
            var updated = tune.changeDefaultArrangerCredit(newCredit);

            // Assert
            assertThat(updated.defaultArrangerCredit()).isEqualTo(newCredit);
        }

        @Test
        @DisplayName("アレンジャークレジットをnullに変更できること")
        void changeDefaultArrangerCreditWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultArrangerCredit(null);

            // Assert
            assertThat(updated.defaultArrangerCredit()).isNull();
        }
    }

    @Nested
    @DisplayName("原曲情報変更テスト")
    class ChangeOriginalWorkInfoTest {

        @Test
        @DisplayName("原曲情報を変更できること")
        void changeOriginalWorkInfoWithValidInfoShouldSucceed() {
            // Arrange
            var tune = createArrangementTune();
            var newTitle = "New Original Title";
            var newCredit = "New Original Artist";

            // Act
            var updated = tune.changeOriginalWorkInfo(newTitle, newCredit);

            // Assert
            assertThat(updated.originalWorkTitle()).isEqualTo(newTitle);
            assertThat(updated.originalWorkCredit()).isEqualTo(newCredit);
        }

        @Test
        @DisplayName("アレンジ曲の原曲タイトルをnullや空文字に変更しようとすると例外が発生すること")
        void changeOriginalWorkInfoForArrangementWithNullShouldThrowException() {
            // Arrange
            var tune = createArrangementTune();

            // Act & Assert
            assertThatThrownBy(() -> {
                tune.changeOriginalWorkInfo(null, "Credit");
            }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Original work title is required");

            assertThatThrownBy(() -> {
                tune.changeOriginalWorkInfo("   ", "Credit");
            }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Original work title is required");
        }

        @Test
        @DisplayName("オリジナル曲の原曲情報をnullに変更できること")
        void changeOriginalWorkInfoForOriginalWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune(); // ORIGINAL

            // Act
            var updated = tune.changeOriginalWorkInfo(null, null);

            // Assert
            assertThat(updated.originalWorkTitle()).isNull();
            assertThat(updated.originalWorkCredit()).isNull();
        }
    }

    @Nested
    @DisplayName("チューンタイプ変更テスト")
    class ChangeTuneTypeTest {

        @Test
        @DisplayName("チューンタイプを変更できること")
        void changeTuneTypeWithValidTypeShouldSucceed() {
            // Arrange
            var tune = createTestTune();
            var newTuneType = "Jig";

            // Act
            var updated = tune.changeTuneType(newTuneType);

            // Assert
            assertThat(updated.tuneType()).isEqualTo(newTuneType);
        }

        @Test
        @DisplayName("チューンタイプをnullに変更できること")
        void changeTuneTypeWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeTuneType(null);

            // Assert
            assertThat(updated.tuneType()).isNull();
        }
    }

    @Nested
    @DisplayName("デフォルトキー変更テスト")
    class ChangeDefaultKeyTest {

        @Test
        @DisplayName("デフォルトキーを変更できること")
        void changeDefaultKeyWithValidKeyShouldSucceed() {
            // Arrange
            var tune = createTestTune();
            var newKey = "E Minor";

            // Act
            var updated = tune.changeDefaultKey(newKey);

            // Assert
            assertThat(updated.defaultKey()).isEqualTo(newKey);
        }

        @Test
        @DisplayName("デフォルトキーをnullに変更できること")
        void changeDefaultKeyWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultKey(null);

            // Assert
            assertThat(updated.defaultKey()).isNull();
        }
    }

    @Nested
    @DisplayName("デフォルトテンポ変更テスト")
    class ChangeDefaultTempoTest {

        @Test
        @DisplayName("デフォルトテンポを変更できること")
        void changeDefaultTempoWithValidTempoShouldSucceed() {
            // Arrange
            var tune = createTestTune();
            var newTempo = 140;

            // Act
            var updated = tune.changeDefaultTempo(newTempo);

            // Assert
            assertThat(updated.defaultTempo()).isEqualTo(newTempo);
        }

        @Test
        @DisplayName("デフォルトテンポをnullに変更できること")
        void changeDefaultTempoWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultTempo(null);

            // Assert
            assertThat(updated.defaultTempo()).isNull();
        }

        @Test
        @DisplayName("負のテンポに変更しようとすると例外が発生すること")
        void changeDefaultTempoWithNegativeShouldThrowException() {
            // Arrange
            var tune = createTestTune();

            // Act & Assert
            assertThatThrownBy(() -> {
                tune.changeDefaultTempo(-1);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tempo must be positive");
        }

        @Test
        @DisplayName("ゼロのテンポに変更しようとすると例外が発生すること")
        void changeDefaultTempoWithZeroShouldThrowException() {
            // Arrange
            var tune = createTestTune();

            // Act & Assert
            assertThatThrownBy(() -> {
                tune.changeDefaultTempo(0);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Tempo must be positive");
        }
    }

    @Nested
    @DisplayName("Tune.Idのテスト")
    class IdTest {

        @Test
        @DisplayName("IDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            var id = Tune.Id.generate();

            // Assert
            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
            assertThat(id.value().isBlank()).isFalse();
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            var validUuid = Tune.Id.generate().value();

            // Act
            var id = Tune.Id.of(validUuid);

            // Assert
            assertThat(id.value()).isEqualTo(validUuid);
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Tune.Id.of("");
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                Tune.Id.of("invalid-uuid");
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("同じ値のIDは等しいこと")
        void equalsWithSameValueShouldBeEqual() {
            // Arrange
            var value = Tune.Id.generate().value();
            var id1 = Tune.Id.of(value);
            var id2 = Tune.Id.of(value);

            // Act & Assert
            assertThat(id2).isEqualTo(id1);
            assertThat(id2.hashCode()).isEqualTo(id1.hashCode());
        }
    }

    // テストヘルパーメソッド

    private Tune createTestTune() {
        return Tune.create(TuneTitle.of("Test Tune"), TuneKind.ORIGINAL, Credit.of("Test Composer"), null, null, null,
                null, null, null);
    }

    private Tune createArrangementTune() {
        return Tune.create(TuneTitle.of("Arranged Tune"), TuneKind.ARRANGEMENT, Credit.of("Original Composer"),
                Credit.of("Arranger"), "Original Work Title", "Original Artist", "Reel", "D Major", 120);
    }
}
