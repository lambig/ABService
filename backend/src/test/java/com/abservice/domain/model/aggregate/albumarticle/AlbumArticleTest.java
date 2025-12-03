package com.abservice.domain.model.aggregate.albumarticle;

import static org.junit.jupiter.api.Assertions.*;

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
            assertNotNull(albumArticle);
            assertEquals(albumId, albumArticle.albumId());
            assertEquals(albumId, albumArticle.id()); // AlbumArticleのIDはAlbum.Id
            assertEquals(introLong, albumArticle.introLong());
            assertEquals(introShort, albumArticle.introShort());
            assertEquals(firstEventSpace, albumArticle.firstEventSpace());
            assertEquals(labelTag, albumArticle.labelTag());
            assertNull(albumArticle.distribution());
            assertTrue(albumArticle.getAcquisitionChannels().isEmpty());
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
            assertNotNull(albumArticle);
            assertEquals(distribution, albumArticle.distribution());
        }

        @Test
        @DisplayName("アルバムIDがnullの場合は例外が発生すること")
        void createWithNullAlbumIdShouldThrowException() {
            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                AlbumArticle.create(null, "intro", "short", "space", null, null);
            });
            assertEquals("Album ID cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("nullableフィールドがnullでも生成できること")
        void createWithNullableFieldsShouldSucceed() {
            // Arrange
            var albumId = Album.Id.generate();

            // Act
            var albumArticle = AlbumArticle.create(albumId, null, null, null, null, null);

            // Assert
            assertNotNull(albumArticle);
            assertNull(albumArticle.introLong());
            assertNull(albumArticle.introShort());
            assertNull(albumArticle.firstEventSpace());
            assertNull(albumArticle.labelTag());
            assertNull(albumArticle.distribution());
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
            assertEquals(newIntroLong, updated.introLong());
            assertEquals(newIntroShort, updated.introShort());
        }

        @Test
        @DisplayName("紹介文をnullに更新できること")
        void updateIntroWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.updateIntro(null, null);

            // Assert
            assertNull(updated.introLong());
            assertNull(updated.introShort());
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
            assertEquals(newSpace, updated.firstEventSpace());
        }

        @Test
        @DisplayName("初出イベントスペースをnullに変更できること")
        void changeFirstEventSpaceWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.changeFirstEventSpace(null);

            // Assert
            assertNull(updated.firstEventSpace());
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
            assertEquals(newLabelTag, updated.labelTag());
        }

        @Test
        @DisplayName("ラベルタグをnullに更新できること")
        void updateLabelTagWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.updateLabelTag(null);

            // Assert
            assertNull(updated.labelTag());
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
            assertEquals(distribution, updated.distribution());
        }

        @Test
        @DisplayName("頒布情報をnullに設定できること")
        void setDistributionWithNullShouldSucceed() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act
            var updated = albumArticle.setDistribution(null);

            // Assert
            assertNull(updated.distribution());
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
            assertEquals(1, updated.getAcquisitionChannels().size());
            assertTrue(updated.getAcquisitionChannels().contains(channel));
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
            assertEquals(3, updated.getAcquisitionChannels().size());
            assertEquals(List.of(channel1, channel2, channel3), updated.getAcquisitionChannels());
        }

        @Test
        @DisplayName("nullの入手経路を追加しようとすると例外が発生すること")
        void addAcquisitionChannelWithNullShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                albumArticle.addAcquisitionChannel(null);
            });
            assertEquals("Acquisition channel cannot be null", exception.getMessage());
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
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbumArticle.addAcquisitionChannel(channel2);
            });
            assertTrue(exception.getMessage().contains("already exists"));
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
            assertEquals(0, updated.getAcquisitionChannels().size());
            assertFalse(updated.getAcquisitionChannels().contains(channel));
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
            assertEquals(2, updated.getAcquisitionChannels().size());
            assertTrue(updated.getAcquisitionChannels().contains(channel1));
            assertFalse(updated.getAcquisitionChannels().contains(channel2));
            assertTrue(updated.getAcquisitionChannels().contains(channel3));
        }

        @Test
        @DisplayName("存在しない入手経路を削除しようとすると例外が発生すること")
        void removeAcquisitionChannelWithNonExistentChannelShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();
            var nonExistentId = AlbumAcquisitionChannel.Id.generate();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                albumArticle.removeAcquisitionChannel(nonExistentId);
            });
            assertTrue(exception.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("nullのIDで入手経路を削除しようとすると例外が発生すること")
        void removeAcquisitionChannelWithNullIdShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                albumArticle.removeAcquisitionChannel(null);
            });
            assertEquals("Channel ID cannot be null", exception.getMessage());
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
            assertEquals("Updated Name", resultChannel.getName());
            assertEquals(ChannelType.ONLINE_SHOP, resultChannel.getChannelType());
        }

        @Test
        @DisplayName("nullの入手経路で更新しようとすると例外が発生すること")
        void updateAcquisitionChannelWithNullShouldThrowException() {
            // Arrange
            var albumArticle = createTestAlbumArticle();

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                albumArticle.updateAcquisitionChannel(null);
            });
            assertEquals("Updated channel cannot be null", exception.getMessage());
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
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                finalAlbumArticle.updateAcquisitionChannel(nonExistentChannel);
            });
            assertTrue(exception.getMessage().contains("not found"));
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
            assertEquals(albumId, albumArticle.id());
            assertEquals(albumId, albumArticle.albumId());
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
