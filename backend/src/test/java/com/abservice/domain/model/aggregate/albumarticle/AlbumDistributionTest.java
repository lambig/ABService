package com.abservice.domain.model.aggregate.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(distribution).isNotNull();
            assertThat(distribution.getPhysicalPrice()).isNull();
            assertThat(distribution.getDownloadPrice()).isNull();
            assertThat(distribution.getDemoUrl()).isNull();
            assertThat(distribution.getNote()).isNull();
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
            assertThat(distribution).isNotNull();
            assertThat(distribution.getPhysicalPrice()).isEqualTo(physicalPrice);
            assertThat(distribution.getDownloadPrice()).isEqualTo(downloadPrice);
            assertThat(distribution.getDemoUrl()).isEqualTo(demoUrl);
            assertThat(distribution.getNote()).isEqualTo(note);
        }

        @Test
        @DisplayName("物理頒価のみを指定して生成できること")
        void createWithPhysicalPriceOnlyShouldSucceed() {
            // Arrange
            var physicalPrice = Price.of(1000);

            // Act
            var distribution = AlbumDistribution.create(physicalPrice, null, null, null);

            // Assert
            assertThat(distribution.getPhysicalPrice()).isEqualTo(physicalPrice);
            assertThat(distribution.getDownloadPrice()).isNull();
        }

        @Test
        @DisplayName("DL価格のみを指定して生成できること")
        void createWithDownloadPriceOnlyShouldSucceed() {
            // Arrange
            var downloadPrice = Price.of(500);

            // Act
            var distribution = AlbumDistribution.create(null, downloadPrice, null, null);

            // Assert
            assertThat(distribution.getPhysicalPrice()).isNull();
            assertThat(distribution.getDownloadPrice()).isEqualTo(downloadPrice);
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
            assertThat(distribution).isNotNull();
            assertThat(distribution.getPhysicalPrice()).isEqualTo(physicalPrice);
            assertThat(distribution.getDownloadPrice()).isEqualTo(downloadPrice);
            assertThat(distribution.getDemoUrl()).isEqualTo(demoUrl);
            assertThat(distribution.getNote()).isEqualTo(note);
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
            assertThat(updated.getPhysicalPrice()).isEqualTo(newPrice);
        }

        @Test
        @DisplayName("物理頒価をnullに変更できること")
        void changePhysicalPriceToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(Price.of(1000), null, null, null);

            // Act
            var updated = distribution.changePhysicalPrice(null);

            // Assert
            assertThat(updated.getPhysicalPrice()).isNull();
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
            assertThat(updated.getDownloadPrice()).isEqualTo(downloadPrice);
            assertThat(updated.getDemoUrl()).isEqualTo(demoUrl);
            assertThat(updated.getNote()).isEqualTo(note);
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
            assertThat(updated.getDownloadPrice()).isEqualTo(newPrice);
        }

        @Test
        @DisplayName("DL価格をnullに変更できること")
        void changeDownloadPriceToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, Price.of(500), null, null);

            // Act
            var updated = distribution.changeDownloadPrice(null);

            // Assert
            assertThat(updated.getDownloadPrice()).isNull();
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
            assertThat(updated.getPhysicalPrice()).isEqualTo(physicalPrice);
            assertThat(updated.getDemoUrl()).isEqualTo(demoUrl);
            assertThat(updated.getNote()).isEqualTo(note);
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
            assertThat(updated.getDemoUrl()).isEqualTo(newUrl);
        }

        @Test
        @DisplayName("デモURLをnullに変更できること")
        void changeDemoUrlToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, null, Url.of("https://example.com"), null);

            // Act
            var updated = distribution.changeDemoUrl(null);

            // Assert
            assertThat(updated.getDemoUrl()).isNull();
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
            assertThat(updated.getPhysicalPrice()).isEqualTo(physicalPrice);
            assertThat(updated.getDownloadPrice()).isEqualTo(downloadPrice);
            assertThat(updated.getNote()).isEqualTo(note);
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
            assertThat(updated.getNote()).isEqualTo(newNote);
        }

        @Test
        @DisplayName("補足メモをnullに変更できること")
        void changeNoteToNullShouldSucceed() {
            // Arrange
            var distribution = AlbumDistribution.create(null, null, null, "旧補足");

            // Act
            var updated = distribution.changeNote(null);

            // Assert
            assertThat(updated.getNote()).isNull();
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
            assertThat(updated.getPhysicalPrice()).isEqualTo(physicalPrice);
            assertThat(updated.getDownloadPrice()).isEqualTo(downloadPrice);
            assertThat(updated.getDemoUrl()).isEqualTo(demoUrl);
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
            assertThat(step4.getPhysicalPrice()).isEqualTo(Price.of(1000));
            assertThat(step4.getDownloadPrice()).isEqualTo(Price.of(500));
            assertThat(step4.getDemoUrl()).isEqualTo(Url.of("https://demo.example.com"));
            assertThat(step4.getNote()).isEqualTo("イベント頒布開始");
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
            assertThat(revised.getPhysicalPrice()).isEqualTo(Price.of(800));
            assertThat(revised.getDownloadPrice()).isEqualTo(Price.of(400));
            assertThat(revised.getNote()).isEqualTo("価格改定後");
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
            assertThat(ended.getPhysicalPrice()).isNull();
            assertThat(ended.getDownloadPrice()).isNull();
            assertThat(ended.getDemoUrl()).isEqualTo(Url.of("https://demo.example.com")); // デモは残す
            assertThat(ended.getNote()).isEqualTo("頒布終了");
        }
    }
}
