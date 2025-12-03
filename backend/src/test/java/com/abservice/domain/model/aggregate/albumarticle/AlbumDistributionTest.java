package com.abservice.domain.model.aggregate.albumarticle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;

@DisplayName("AlbumDistributionエンティティのテスト")
class AlbumDistributionTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("すべてnullで生成できること")
        void createWithAllNullShouldSucceed() {
            // Act
            var distribution = AlbumDistribution.create(null, null, null, null);

            // Assert
            assertNotNull(distribution);
            assertNull(distribution.getPhysicalPrice());
            assertNull(distribution.getDownloadPrice());
            assertNull(distribution.getDemoUrl());
            assertNull(distribution.getNote());
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var physicalPrice = Price.of(1000);
            var downloadPrice = Price.of(500);
            var demoUrl = Url.of("https://example.com/demo");
            var note = "頒布情報";

            // Act
            var distribution = AlbumDistribution.create(physicalPrice, downloadPrice, demoUrl, note);

            // Assert
            assertNotNull(distribution);
            assertEquals(physicalPrice, distribution.getPhysicalPrice());
            assertEquals(downloadPrice, distribution.getDownloadPrice());
            assertEquals(demoUrl, distribution.getDemoUrl());
            assertEquals(note, distribution.getNote());
        }

        @Test
        @DisplayName("物理頒価のみを指定して生成できること")
        void createWithPhysicalPriceOnlyShouldSucceed() {
            // Arrange
            var physicalPrice = Price.of(1000);

            // Act
            var distribution = AlbumDistribution.create(physicalPrice, null, null, null);

            // Assert
            assertEquals(physicalPrice, distribution.getPhysicalPrice());
            assertNull(distribution.getDownloadPrice());
        }

        @Test
        @DisplayName("DL価格のみを指定して生成できること")
        void createWithDownloadPriceOnlyShouldSucceed() {
            // Arrange
            var downloadPrice = Price.of(500);

            // Act
            var distribution = AlbumDistribution.create(null, downloadPrice, null, null);

            // Assert
            assertNull(distribution.getPhysicalPrice());
            assertEquals(downloadPrice, distribution.getDownloadPrice());
        }
    }

    @Nested
    @DisplayName("再構成テスト")
    class ReconstructTest {

        @Test
        @DisplayName("永続化層から再構成できること")
        void reconstructShouldSucceed() {
            // Arrange
            var physicalPrice = Price.of(1000);
            var downloadPrice = Price.of(500);
            var demoUrl = Url.of("https://example.com/demo");
            var note = "頒布情報";

            // Act
            var distribution = AlbumDistribution.reconstruct(physicalPrice, downloadPrice, demoUrl, note);

            // Assert
            assertNotNull(distribution);
            assertEquals(physicalPrice, distribution.getPhysicalPrice());
            assertEquals(downloadPrice, distribution.getDownloadPrice());
            assertEquals(demoUrl, distribution.getDemoUrl());
            assertEquals(note, distribution.getNote());
        }
    }

    @Nested
    @DisplayName("物理頒価変更テスト")
    class ChangePhysicalPriceTest {

        @Test
        @DisplayName("物理頒価を変更できること")
        void changePhysicalPriceShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(Price.of(1000), null, null, null);
            var newPrice = Price.of(1500);

            // Act
            var updated = distribution.changePhysicalPrice(newPrice);

            // Assert
            assertEquals(newPrice, updated.getPhysicalPrice());
        }

        @Test
        @DisplayName("物理頒価をnullに変更できること")
        void changePhysicalPriceToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(Price.of(1000), null, null, null);

            // Act
            var updated = distribution.changePhysicalPrice(null);

            // Assert
            assertNull(updated.getPhysicalPrice());
        }

        @Test
        @DisplayName("物理頒価を変更しても他のフィールドは変わらないこと")
        void changePhysicalPriceShouldNotAffectOtherFields() {
            // Arrange
            var downloadPrice = Price.of(500);
            var demoUrl = Url.of("https://example.com/demo");
            var note = "補足";
            var distribution = AlbumDistribution.create(Price.of(1000), downloadPrice, demoUrl, note);
            var newPrice = Price.of(1500);

            // Act
            var updated = distribution.changePhysicalPrice(newPrice);

            // Assert
            assertEquals(downloadPrice, updated.getDownloadPrice());
            assertEquals(demoUrl, updated.getDemoUrl());
            assertEquals(note, updated.getNote());
        }
    }

    @Nested
    @DisplayName("DL価格変更テスト")
    class ChangeDownloadPriceTest {

        @Test
        @DisplayName("DL価格を変更できること")
        void changeDownloadPriceShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, Price.of(500), null, null);
            var newPrice = Price.of(800);

            // Act
            var updated = distribution.changeDownloadPrice(newPrice);

            // Assert
            assertEquals(newPrice, updated.getDownloadPrice());
        }

        @Test
        @DisplayName("DL価格をnullに変更できること")
        void changeDownloadPriceToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, Price.of(500), null, null);

            // Act
            var updated = distribution.changeDownloadPrice(null);

            // Assert
            assertNull(updated.getDownloadPrice());
        }

        @Test
        @DisplayName("DL価格を変更しても他のフィールドは変わらないこと")
        void changeDownloadPriceShouldNotAffectOtherFields() {
            // Arrange
            var physicalPrice = Price.of(1000);
            var demoUrl = Url.of("https://example.com/demo");
            var note = "補足";
            var distribution = AlbumDistribution.create(physicalPrice, Price.of(500), demoUrl, note);
            var newPrice = Price.of(800);

            // Act
            var updated = distribution.changeDownloadPrice(newPrice);

            // Assert
            assertEquals(physicalPrice, updated.getPhysicalPrice());
            assertEquals(demoUrl, updated.getDemoUrl());
            assertEquals(note, updated.getNote());
        }
    }

    @Nested
    @DisplayName("デモURL変更テスト")
    class ChangeDemoUrlTest {

        @Test
        @DisplayName("デモURLを変更できること")
        void changeDemoUrlShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, null, Url.of("https://old.example.com"), null);
            var newUrl = Url.of("https://new.example.com");

            // Act
            var updated = distribution.changeDemoUrl(newUrl);

            // Assert
            assertEquals(newUrl, updated.getDemoUrl());
        }

        @Test
        @DisplayName("デモURLをnullに変更できること")
        void changeDemoUrlToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, null, Url.of("https://example.com"), null);

            // Act
            var updated = distribution.changeDemoUrl(null);

            // Assert
            assertNull(updated.getDemoUrl());
        }

        @Test
        @DisplayName("デモURLを変更しても他のフィールドは変わらないこと")
        void changeDemoUrlShouldNotAffectOtherFields() {
            // Arrange
            var physicalPrice = Price.of(1000);
            var downloadPrice = Price.of(500);
            var note = "補足";
            var distribution = AlbumDistribution.create(physicalPrice, downloadPrice, Url.of("https://old.com"), note);
            var newUrl = Url.of("https://new.com");

            // Act
            var updated = distribution.changeDemoUrl(newUrl);

            // Assert
            assertEquals(physicalPrice, updated.getPhysicalPrice());
            assertEquals(downloadPrice, updated.getDownloadPrice());
            assertEquals(note, updated.getNote());
        }
    }

    @Nested
    @DisplayName("補足メモ変更テスト")
    class ChangeNoteTest {

        @Test
        @DisplayName("補足メモを変更できること")
        void changeNoteShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, null, null, "旧補足");
            var newNote = "新補足";

            // Act
            var updated = distribution.changeNote(newNote);

            // Assert
            assertEquals(newNote, updated.getNote());
        }

        @Test
        @DisplayName("補足メモをnullに変更できること")
        void changeNoteToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, null, null, "旧補足");

            // Act
            var updated = distribution.changeNote(null);

            // Assert
            assertNull(updated.getNote());
        }

        @Test
        @DisplayName("補足メモを変更しても他のフィールドは変わらないこと")
        void changeNoteShouldNotAffectOtherFields() {
            // Arrange
            var physicalPrice = Price.of(1000);
            var downloadPrice = Price.of(500);
            var demoUrl = Url.of("https://example.com");
            var distribution = AlbumDistribution.create(physicalPrice, downloadPrice, demoUrl, "旧補足");
            var newNote = "新補足";

            // Act
            var updated = distribution.changeNote(newNote);

            // Assert
            assertEquals(physicalPrice, updated.getPhysicalPrice());
            assertEquals(downloadPrice, updated.getDownloadPrice());
            assertEquals(demoUrl, updated.getDemoUrl());
        }
    }

    @Nested
    @DisplayName("統合シナリオテスト")
    class IntegrationScenarioTest {

        @Test
        @DisplayName("頒布情報の段階的な設定ができること")
        void gradualSetupShouldSucceed() {
            // Arrange - 最初は空の状態
            var distribution = AlbumDistribution.create(null, null, null, null);

            // Act - 段階的に情報を追加
            var step1 = distribution.changePhysicalPrice(Price.of(1000));
            var step2 = step1.changeDownloadPrice(Price.of(500));
            var step3 = step2.changeDemoUrl(Url.of("https://demo.example.com"));
            var step4 = step3.changeNote("イベント頒布開始");

            // Assert
            assertEquals(Price.of(1000), step4.getPhysicalPrice());
            assertEquals(Price.of(500), step4.getDownloadPrice());
            assertEquals(Url.of("https://demo.example.com"), step4.getDemoUrl());
            assertEquals("イベント頒布開始", step4.getNote());
        }

        @Test
        @DisplayName("価格改定ができること")
        void priceRevisionShouldSucceed() {
            // Arrange - 初期価格設定
            var distribution = AlbumDistribution.create(Price.of(1000), Price.of(500), null, "初期価格");

            // Act - 価格改定
            var revised = distribution.changePhysicalPrice(Price.of(800)).changeDownloadPrice(Price.of(400))
                    .changeNote("価格改定後");

            // Assert
            assertEquals(Price.of(800), revised.getPhysicalPrice());
            assertEquals(Price.of(400), revised.getDownloadPrice());
            assertEquals("価格改定後", revised.getNote());
        }

        @Test
        @DisplayName("頒布終了時に価格情報をクリアできること")
        void clearPricesOnDistributionEndShouldSucceed() {
            // Arrange - 頒布中の状態
            var distribution = AlbumDistribution.create(Price.of(1000), Price.of(500),
                    Url.of("https://demo.example.com"), "頒布中");

            // Act - 頒布終了
            var ended = distribution.changePhysicalPrice(null).changeDownloadPrice(null).changeNote("頒布終了");

            // Assert
            assertNull(ended.getPhysicalPrice());
            assertNull(ended.getDownloadPrice());
            assertEquals(Url.of("https://demo.example.com"), ended.getDemoUrl()); // デモは残す
            assertEquals("頒布終了", ended.getNote());
        }
    }
}
