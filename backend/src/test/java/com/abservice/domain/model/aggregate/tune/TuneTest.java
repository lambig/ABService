package com.abservice.domain.model.aggregate.tune;

import static org.junit.jupiter.api.Assertions.*;

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
            assertNotNull(tune);
            assertNotNull(tune.id());
            assertEquals(title, tune.title());
            assertEquals(tuneKind, tune.tuneKind());
            assertEquals(composerCredit, tune.defaultComposerCredit());
            assertNull(tune.defaultArrangerCredit());
            assertNull(tune.originalWorkTitle());
            assertNull(tune.originalWorkCredit());
            assertNull(tune.tuneType());
            assertNull(tune.defaultKey());
            assertNull(tune.defaultTempo());
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
            assertNotNull(tune);
            assertEquals(arrangerCredit, tune.defaultArrangerCredit());
            assertEquals(originalWorkTitle, tune.originalWorkTitle());
            assertEquals(originalWorkCredit, tune.originalWorkCredit());
            assertEquals(tuneType, tune.tuneType());
            assertEquals(defaultKey, tune.defaultKey());
            assertEquals(defaultTempo, tune.defaultTempo());
        }

        @Test
        @DisplayName("タイトルがnullの場合は例外が発生すること")
        void createWithNullTitleShouldThrowException() {
            // Arrange
            var tuneKind = TuneKind.ORIGINAL;
            var composerCredit = Credit.of("Composer");

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Tune.create(null, tuneKind, composerCredit, null, null, null, null, null, null);
            });
            assertEquals("Tune title cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("チューン種別がnullの場合は例外が発生すること")
        void createWithNullTuneKindShouldThrowException() {
            // Arrange
            var title = TuneTitle.of("Test Tune");
            var composerCredit = Credit.of("Composer");

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                Tune.create(title, null, composerCredit, null, null, null, null, null, null);
            });
            assertEquals("Tune kind cannot be null", exception.getMessage());
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
            assertEquals(newTitle, updated.title());
            assertEquals(tune.id(), updated.id()); // IDは変わらない
        }

        @Test
        @DisplayName("nullのタイトルに変更しようとすると例外が発生すること")
        void changeTitleWithNullShouldThrowException() {
            // Arrange
            var tune = createTestTune();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                tune.changeTitle(null);
            });
            assertEquals("Tune title cannot be null", exception.getMessage());
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
            assertEquals(newCredit, updated.defaultComposerCredit());
        }

        @Test
        @DisplayName("作曲者クレジットをnullに変更できること")
        void changeDefaultComposerCreditWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultComposerCredit(null);

            // Assert
            assertNull(updated.defaultComposerCredit());
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
            assertEquals(newCredit, updated.defaultArrangerCredit());
        }

        @Test
        @DisplayName("アレンジャークレジットをnullに変更できること")
        void changeDefaultArrangerCreditWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultArrangerCredit(null);

            // Assert
            assertNull(updated.defaultArrangerCredit());
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
            assertEquals(newTitle, updated.originalWorkTitle());
            assertEquals(newCredit, updated.originalWorkCredit());
        }

        @Test
        @DisplayName("アレンジ曲の原曲タイトルをnullや空文字に変更しようとすると例外が発生すること")
        void changeOriginalWorkInfoForArrangementWithNullShouldThrowException() {
            // Arrange
            var tune = createArrangementTune();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                tune.changeOriginalWorkInfo(null, "Credit");
            });
            assertTrue(exception.getMessage().contains("Original work title is required"));

            exception = assertThrows(IllegalArgumentException.class, () -> {
                tune.changeOriginalWorkInfo("   ", "Credit");
            });
            assertTrue(exception.getMessage().contains("Original work title is required"));
        }

        @Test
        @DisplayName("オリジナル曲の原曲情報をnullに変更できること")
        void changeOriginalWorkInfoForOriginalWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune(); // ORIGINAL

            // Act
            var updated = tune.changeOriginalWorkInfo(null, null);

            // Assert
            assertNull(updated.originalWorkTitle());
            assertNull(updated.originalWorkCredit());
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
            assertEquals(newTuneType, updated.tuneType());
        }

        @Test
        @DisplayName("チューンタイプをnullに変更できること")
        void changeTuneTypeWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeTuneType(null);

            // Assert
            assertNull(updated.tuneType());
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
            assertEquals(newKey, updated.defaultKey());
        }

        @Test
        @DisplayName("デフォルトキーをnullに変更できること")
        void changeDefaultKeyWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultKey(null);

            // Assert
            assertNull(updated.defaultKey());
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
            assertEquals(newTempo, updated.defaultTempo());
        }

        @Test
        @DisplayName("デフォルトテンポをnullに変更できること")
        void changeDefaultTempoWithNullShouldSucceed() {
            // Arrange
            var tune = createTestTune();

            // Act
            var updated = tune.changeDefaultTempo(null);

            // Assert
            assertNull(updated.defaultTempo());
        }

        @Test
        @DisplayName("負のテンポに変更しようとすると例外が発生すること")
        void changeDefaultTempoWithNegativeShouldThrowException() {
            // Arrange
            var tune = createTestTune();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                tune.changeDefaultTempo(-1);
            });
            assertEquals("Tempo must be positive", exception.getMessage());
        }

        @Test
        @DisplayName("ゼロのテンポに変更しようとすると例外が発生すること")
        void changeDefaultTempoWithZeroShouldThrowException() {
            // Arrange
            var tune = createTestTune();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                tune.changeDefaultTempo(0);
            });
            assertEquals("Tempo must be positive", exception.getMessage());
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
            assertNotNull(id);
            assertNotNull(id.value());
            assertFalse(id.value().isBlank());
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofWithValidUuidShouldSucceed() {
            // Arrange
            var validUuid = Tune.Id.generate().value();

            // Act
            var id = Tune.Id.of(validUuid);

            // Assert
            assertEquals(validUuid, id.value());
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Tune.Id.of("");
            });
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                Tune.Id.of("invalid-uuid");
            });
        }

        @Test
        @DisplayName("同じ値のIDは等しいこと")
        void equalsWithSameValueShouldBeEqual() {
            // Arrange
            var value = Tune.Id.generate().value();
            var id1 = Tune.Id.of(value);
            var id2 = Tune.Id.of(value);

            // Act & Assert
            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
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
