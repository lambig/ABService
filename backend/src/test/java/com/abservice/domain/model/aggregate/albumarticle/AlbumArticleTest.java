package com.abservice.domain.model.aggregate.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.model.vo.common.Price;
import com.abservice.domain.model.vo.common.Url;

@DisplayName("AlbumArticle集約のテスト")
class AlbumArticleTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("正常な値で生成できること")
        void createWithValidValuesShouldSucceed() {
            // Arrange
            var albumId = Album.Id.generate();
            var introLong = "This is a long introduction.";
            var introShort = "Short intro";
            var firstEventSpace = "東X-00b";
            var labelTag = LabelTag.NEW;

            // Act
            var albumArticle = AlbumArticle.create(albumId, introLong, introShort, firstEventSpace, labelTag, null);

            // Assert
            assertThat(albumArticle).isNotNull();
            assertThat(albumArticle.albumId()).isEqualTo(albumId);
            assertThat(albumArticle.id()).isEqualTo(albumId); // AlbumArticleのIDはAlbum.Id
            assertThat(albumArticle.introLong()).isEqualTo(introLong);
            assertThat(albumArticle.introShort()).isEqualTo(introShort);
            assertThat(albumArticle.firstEventSpace()).isEqualTo(firstEventSpace);
            assertThat(albumArticle.labelTag()).isEqualTo(labelTag);
            assertThat(albumArticle.distribution()).isNull();
            assertThat(albumArticle.getAcquisitionChannels().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var albumId = Album.Id.generate();
            var introLong = "Complete introduction text.";
            var introShort = "Complete short";
            var firstEventSpace = "西Y-99c";
            var labelTag = LabelTag.OTHER;
            var distribution = AlbumDistribution.create(Price.of(1000), Price.of(500),
                    Url.of("https://example.com/demo"), "Demo available");

            // Act
            var albumArticle = AlbumArticle.create(albumId, introLong, introShort, firstEventSpace, labelTag,
                    distribution);

            // Assert
            assertThat(albumArticle).isNotNull();
            assertThat(albumArticle.distribution()).isEqualTo(distribution);
        }

        @Test
        @DisplayName("アルバムIDがnullの場合は例外が発生すること")
        void createWithNullAlbumIdShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumArticle.create(null, "intro", "short", "space", null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Album ID cannot be null");
        }

        @Test
        @DisplayName("nullableフィールドがnullでも生成できること")
        void createWithNullableFieldsShouldSucceed() {
            // Arrange
            var albumId = Album.Id.generate();

            // Act
            var albumArticle = AlbumArticle.create(albumId, null, null, null, null, null);

            // Assert
            assertThat(albumArticle).isNotNull();
            assertThat(albumArticle.introLong()).isNull();
            assertThat(albumArticle.introShort()).isNull();
            assertThat(albumArticle.firstEventSpace()).isNull();
            assertThat(albumArticle.labelTag()).isNull();
            assertThat(albumArticle.distribution()).isNull();
        }
    }

    @Nested
    @DisplayName("紹介文更新テスト")
    class UpdateIntroTest {

        @Test
        @DisplayName("紹介文を更新できること")
        void updateIntroWithValidValuesShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var newIntroLong = "Updated long introduction.";
            var newIntroShort = "Updated short";

            // Act
            var updated = albumArticle.updateIntro(newIntroLong, newIntroShort);

            // Assert
            assertThat(updated.introLong()).isEqualTo(newIntroLong);
            assertThat(updated.introShort()).isEqualTo(newIntroShort);
        }

        @Test
        @DisplayName("紹介文をnullに更新できること")
        void updateIntroWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.updateIntro(null, null);

            // Assert
            assertThat(updated.introLong()).isNull();
            assertThat(updated.introShort()).isNull();
        }
    }

    @Nested
    @DisplayName("初出イベントスペース変更テスト")
    class ChangeFirstEventSpaceTest {

        @Test
        @DisplayName("初出イベントスペースを変更できること")
        void changeFirstEventSpaceWithValidSpaceShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var newSpace = "東Z-12c";

            // Act
            var updated = albumArticle.changeFirstEventSpace(newSpace);

            // Assert
            assertThat(updated.firstEventSpace()).isEqualTo(newSpace);
        }

        @Test
        @DisplayName("初出イベントスペースをnullに変更できること")
        void changeFirstEventSpaceWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.changeFirstEventSpace(null);

            // Assert
            assertThat(updated.firstEventSpace()).isNull();
        }
    }

    @Nested
    @DisplayName("ラベルタグ更新テスト")
    class UpdateLabelTagTest {

        @Test
        @DisplayName("ラベルタグを更新できること")
        void updateLabelTagWithValidTagShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var newLabelTag = LabelTag.COMPILATION;

            // Act
            var updated = albumArticle.updateLabelTag(newLabelTag);

            // Assert
            assertThat(updated.labelTag()).isEqualTo(newLabelTag);
        }

        @Test
        @DisplayName("ラベルタグをnullに更新できること")
        void updateLabelTagWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.updateLabelTag(null);

            // Assert
            assertThat(updated.labelTag()).isNull();
        }
    }

    @Nested
    @DisplayName("頒布情報設定テスト")
    class SetDistributionTest {

        @Test
        @DisplayName("頒布情報を設定できること")
        void setDistributionWithValidDistributionShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var distribution = AlbumDistribution.create(Price.of(1500), Price.of(800),
                    Url.of("https://example.com/demo"), "New distribution info");

            // Act
            var updated = albumArticle.setDistribution(distribution);

            // Assert
            assertThat(updated.distribution()).isEqualTo(distribution);
        }

        @Test
        @DisplayName("頒布情報をnullに設定できること")
        void setDistributionWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.setDistribution(null);

            // Assert
            assertThat(updated.distribution()).isNull();
        }
    }

    @Nested
    @DisplayName("入手経路追加テスト")
    class AddAcquisitionChannelTest {

        @Test
        @DisplayName("入手経路を追加できること")
        void addAcquisitionChannelWithValidChannelShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var channel = createTestChannel("BOOTH");

            // Act
            var updated = albumArticle.addAcquisitionChannel(channel);

            // Assert
            assertThat(updated.getAcquisitionChannels().size()).isEqualTo(1);
            assertThat(updated.getAcquisitionChannels().contains(channel)).isTrue();
        }

        @Test
        @DisplayName("複数の入手経路を追加できること")
        void addAcquisitionChannelWithMultipleChannelsShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var channel1 = createTestChannel("BOOTH");
            var channel2 = createTestChannel("Bandcamp");
            var channel3 = createTestChannel("委託ショップ");

            // Act
            var updated = albumArticle.addAcquisitionChannel(channel1).addAcquisitionChannel(channel2)
                    .addAcquisitionChannel(channel3);

            // Assert
            assertThat(updated.getAcquisitionChannels().size()).isEqualTo(3);
            assertThat(updated.getAcquisitionChannels()).isEqualTo(List.of(channel1, channel2, channel3));
        }

        @Test
        @DisplayName("nullの入手経路を追加しようとすると例外が発生すること")
        void addAcquisitionChannelWithNullShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act & Assert
            assertThatThrownBy(() -> {
                albumArticle.addAcquisitionChannel(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Acquisition channel cannot be null");
        }

        @Test
        @DisplayName("重複するIDの入手経路を追加しようとすると例外が発生すること")
        void addAcquisitionChannelWithDuplicateIdShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var channel1 = createTestChannel("BOOTH");
            var channel2 = AlbumAcquisitionChannel.reconstruct(channel1.id(), ChannelType.ONLINE_SHOP, "Different Name",
                    null, null);
            albumArticle = albumArticle.addAcquisitionChannel(channel1);

            // Act & Assert
            var finalAlbumArticle = albumArticle;
            assertThatThrownBy(() -> {
                finalAlbumArticle.addAcquisitionChannel(channel2);
            }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("入手経路削除テスト")
    class RemoveAcquisitionChannelTest {

        @Test
        @DisplayName("入手経路を削除できること")
        void removeAcquisitionChannelWithExistingChannelShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var channel = createTestChannel("BOOTH");
            albumArticle = albumArticle.addAcquisitionChannel(channel);

            // Act
            var updated = albumArticle.removeAcquisitionChannel(channel.id());

            // Assert
            assertThat(updated.getAcquisitionChannels().size()).isEqualTo(0);
            assertThat(updated.getAcquisitionChannels().contains(channel)).isFalse();
        }

        @Test
        @DisplayName("複数の入手経路から特定の入手経路を削除できること")
        void removeAcquisitionChannelFromMultipleChannelsShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var channel1 = createTestChannel("BOOTH");
            var channel2 = createTestChannel("Bandcamp");
            var channel3 = createTestChannel("委託ショップ");
            albumArticle = albumArticle.addAcquisitionChannel(channel1).addAcquisitionChannel(channel2)
                    .addAcquisitionChannel(channel3);

            // Act
            var updated = albumArticle.removeAcquisitionChannel(channel2.id());

            // Assert
            assertThat(updated.getAcquisitionChannels().size()).isEqualTo(2);
            assertThat(updated.getAcquisitionChannels().contains(channel1)).isTrue();
            assertThat(updated.getAcquisitionChannels().contains(channel2)).isFalse();
            assertThat(updated.getAcquisitionChannels().contains(channel3)).isTrue();
        }

        @Test
        @DisplayName("存在しない入手経路を削除しようとすると例外が発生すること")
        void removeAcquisitionChannelWithNonExistentChannelShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var nonExistentId = AlbumAcquisitionChannel.Id.generate();

            // Act & Assert
            assertThatThrownBy(() -> {
                albumArticle.removeAcquisitionChannel(nonExistentId);
            }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        }

        @Test
        @DisplayName("nullのIDで入手経路を削除しようとすると例外が発生すること")
        void removeAcquisitionChannelWithNullIdShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act & Assert
            assertThatThrownBy(() -> {
                albumArticle.removeAcquisitionChannel(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Channel ID cannot be null");
        }
    }

    @Nested
    @DisplayName("入手経路更新テスト")
    class UpdateAcquisitionChannelTest {

        @Test
        @DisplayName("入手経路を更新できること")
        void updateAcquisitionChannelWithValidChannelShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var originalChannel = createTestChannel("Original Name");
            albumArticle = albumArticle.addAcquisitionChannel(originalChannel);

            var updatedChannel = AlbumAcquisitionChannel.reconstruct(originalChannel.id(), ChannelType.ONLINE_SHOP,
                    "Updated Name", Url.of("https://updated.example.com"), "Updated note");

            // Act
            var updated = albumArticle.updateAcquisitionChannel(updatedChannel);

            // Assert
            var resultChannel = updated.getAcquisitionChannels().get(0);
            assertThat(resultChannel.getName()).isEqualTo("Updated Name");
            assertThat(resultChannel.getChannelType()).isEqualTo(ChannelType.ONLINE_SHOP);
        }

        @Test
        @DisplayName("nullの入手経路で更新しようとすると例外が発生すること")
        void updateAcquisitionChannelWithNullShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act & Assert
            assertThatThrownBy(() -> {
                albumArticle.updateAcquisitionChannel(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Updated channel cannot be null");
        }

        @Test
        @DisplayName("存在しない入手経路を更新しようとすると例外が発生すること")
        void updateAcquisitionChannelWithNonExistentChannelShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var channel1 = createTestChannel("Channel 1");
            albumArticle = albumArticle.addAcquisitionChannel(channel1);

            var nonExistentChannel = createTestChannel("Non Existent");

            // Act & Assert
            var finalAlbumArticle = albumArticle;
            assertThatThrownBy(() -> {
                finalAlbumArticle.updateAcquisitionChannel(nonExistentChannel);
            }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("AlbumArticle.Idのテスト")
    class IdTest {

        @Test
        @DisplayName("AlbumArticleのIDはAlbum.Idと同じであること")
        void idShouldMatchAlbumId() {
            // Arrange
            var albumId = Album.Id.generate();
            var albumArticle = AlbumArticle.create(albumId, "intro", "short", "space", null, null);

            // Act & Assert
            assertThat(albumArticle.id()).isEqualTo(albumId);
            assertThat(albumArticle.albumId()).isEqualTo(albumId);
        }
    }

    // テストヘルパーメソッド

    private AlbumArticle createTestAlbumArticle() {
        return AlbumArticle.create(Album.Id.generate(), "Test long introduction", "Test short intro", "東X-00a",
                LabelTag.NEW, null);
    }

    private AlbumAcquisitionChannel createTestChannel(String name) {
        return AlbumAcquisitionChannel.create(ChannelType.ONLINE_SHOP, name, Url.of("https://example.com"),
                "Test note");
    }
}
