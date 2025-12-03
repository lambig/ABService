package com.abservice.domain.model.aggregate.albumarticle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.common.Url;

@DisplayName("AlbumAcquisitionChannelエンティティのテスト")
class AlbumAcquisitionChannelTest {

    @Nested
    @DisplayName("生成テスト")
    class CreateTest {

        @Test
        @DisplayName("必須フィールドのみで生成できること")
        void createWithMinimalFieldsShouldSucceed() {
            // Arrange
            var channelType = ChannelType.DL_SITE;
            var name = "Test Shop";

            // Act
            var channel = AlbumAcquisitionChannel.create(channelType, name, null, null);

            // Assert
            assertNotNull(channel);
            assertNotNull(channel.id());
            assertEquals(channelType, channel.getChannelType());
            assertEquals(name, channel.getName());
            assertNull(channel.getUrl());
            assertNull(channel.getNote());
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            var channelType = ChannelType.STREAMING;
            var name = "My Bandcamp";
            var url = Url.of("https://example.bandcamp.com");
            var note = "デジタル版のみ";

            // Act
            var channel = AlbumAcquisitionChannel.create(channelType, name, url, note);

            // Assert
            assertNotNull(channel);
            assertEquals(channelType, channel.getChannelType());
            assertEquals(name, channel.getName());
            assertEquals(url, channel.getUrl());
            assertEquals(note, channel.getNote());
        }

        @Test
        @DisplayName("チャネルタイプがnullの場合は例外が発生すること")
        void createWithNullChannelTypeShouldThrowException() {
            // Arrange
            var name = "Test Shop";

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                AlbumAcquisitionChannel.create(null, name, null, null);
            });
            assertEquals("Channel type cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("名前がnullの場合は例外が発生すること")
        void createWithNullNameShouldThrowException() {
            // Arrange
            var channelType = ChannelType.DL_SITE;

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                AlbumAcquisitionChannel.create(channelType, null, null, null);
            });
            assertEquals("Name cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("名前が空文字列の場合は例外が発生すること")
        void createWithBlankNameShouldThrowException() {
            // Arrange
            var channelType = ChannelType.DL_SITE;

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                AlbumAcquisitionChannel.create(channelType, "   ", null, null);
            });
            assertEquals("Name cannot be blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("再構成テスト")
    class ReconstructTest {

        @Test
        @DisplayName("永続化層から再構成できること")
        void reconstructShouldSucceed() {
            // Arrange
            var id = AlbumAcquisitionChannel.Id.generate();
            var channelType = ChannelType.STREAMING;
            var name = "My Bandcamp";
            var url = Url.of("https://example.bandcamp.com");
            var note = "デジタル版のみ";

            // Act
            var channel = AlbumAcquisitionChannel.reconstruct(id, channelType, name, url, note);

            // Assert
            assertNotNull(channel);
            assertEquals(id, channel.id());
            assertEquals(channelType, channel.getChannelType());
            assertEquals(name, channel.getName());
            assertEquals(url, channel.getUrl());
            assertEquals(note, channel.getNote());
        }
    }

    @Nested
    @DisplayName("チャネルタイプ変更テスト")
    class ChangeChannelTypeTest {

        @Test
        @DisplayName("チャネルタイプを変更できること")
        void changeChannelTypeShouldSucceed() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);
            var newChannelType = ChannelType.STREAMING;

            // Act
            var updated = channel.changeChannelType(newChannelType);

            // Assert
            assertEquals(newChannelType, updated.getChannelType());
            assertEquals(channel.id(), updated.id());
            assertEquals(channel.getName(), updated.getName());
        }

        @Test
        @DisplayName("nullのチャネルタイプに変更しようとすると例外が発生すること")
        void changeChannelTypeToNullShouldThrowException() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                channel.changeChannelType(null);
            });
            assertEquals("Channel type cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("名前変更テスト")
    class ChangeNameTest {

        @Test
        @DisplayName("名前を変更できること")
        void changeNameShouldSucceed() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Old Name", null, null);
            var newName = "New Name";

            // Act
            var updated = channel.changeName(newName);

            // Assert
            assertEquals(newName, updated.getName());
        }

        @Test
        @DisplayName("nullの名前に変更しようとすると例外が発生すること")
        void changeNameToNullShouldThrowException() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                channel.changeName(null);
            });
            assertEquals("Name cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("空文字列の名前に変更しようとすると例外が発生すること")
        void changeNameToBlankShouldThrowException() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);

            // Act & Assert
            var exception = assertThrows(IllegalArgumentException.class, () -> {
                channel.changeName("   ");
            });
            assertEquals("Name cannot be blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("URL変更テスト")
    class ChangeUrlTest {

        @Test
        @DisplayName("URLを変更できること")
        void changeUrlShouldSucceed() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);
            var url = Url.of("https://example.com");

            // Act
            var updated = channel.changeUrl(url);

            // Assert
            assertEquals(url, updated.getUrl());
        }

        @Test
        @DisplayName("URLをnullに変更できること")
        void changeUrlToNullShouldSucceed() {
            // Arrange
            var url = Url.of("https://example.com");
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", url, null);

            // Act
            var updated = channel.changeUrl(null);

            // Assert
            assertNull(updated.getUrl());
        }
    }

    @Nested
    @DisplayName("補足変更テスト")
    class ChangeNoteTest {

        @Test
        @DisplayName("補足を変更できること")
        void changeNoteShouldSucceed() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);
            var note = "新しい補足情報";

            // Act
            var updated = channel.changeNote(note);

            // Assert
            assertEquals(note, updated.getNote());
        }

        @Test
        @DisplayName("補足をnullに変更できること")
        void changeNoteToNullShouldSucceed() {
            // Arrange
            var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, "旧補足");

            // Act
            var updated = channel.changeNote(null);

            // Assert
            assertNull(updated.getNote());
        }
    }

    @Nested
    @DisplayName("ID型テスト")
    class IdTest {

        @Test
        @DisplayName("UUIDv7形式のIDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            var id = AlbumAcquisitionChannel.Id.generate();

            // Assert
            assertNotNull(id);
            assertNotNull(id.value());
            assertFalse(id.value().isBlank());
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofShouldCreateIdFromString() {
            // Arrange
            var id1 = AlbumAcquisitionChannel.Id.generate();
            var value = id1.value();

            // Act
            var id2 = AlbumAcquisitionChannel.Id.of(value);

            // Assert
            assertEquals(id1, id2);
            assertEquals(value, id2.value());
        }

        @Test
        @DisplayName("nullの文字列からIDを生成しようとすると例外が発生すること")
        void ofWithNullShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                AlbumAcquisitionChannel.Id.of(null);
            });
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                AlbumAcquisitionChannel.Id.of("");
            });
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                AlbumAcquisitionChannel.Id.of("not-a-uuid");
            });
        }
    }

    @Nested
    @DisplayName("等価性テスト")
    class EqualsTest {

        @Test
        @DisplayName("同じIDのAlbumAcquisitionChannelは等しいこと")
        void channelsWithSameIdShouldBeEqual() {
            // Arrange
            var id = AlbumAcquisitionChannel.Id.generate();
            var channel1 = AlbumAcquisitionChannel.reconstruct(id, ChannelType.DL_SITE, "Shop1", null, null);
            var channel2 = AlbumAcquisitionChannel.reconstruct(id, ChannelType.STREAMING, "Shop2", null, null);

            // Act & Assert
            assertEquals(channel1, channel2);
            assertEquals(channel1.hashCode(), channel2.hashCode());
        }

        @Test
        @DisplayName("異なるIDのAlbumAcquisitionChannelは等しくないこと")
        void channelsWithDifferentIdShouldNotBeEqual() {
            // Arrange
            var channel1 = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop1", null, null);
            var channel2 = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop1", null, null);

            // Act & Assert
            assertNotEquals(channel1, channel2);
        }
    }
}
