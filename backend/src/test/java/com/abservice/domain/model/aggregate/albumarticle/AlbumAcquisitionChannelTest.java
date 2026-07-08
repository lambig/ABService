package com.abservice.domain.model.aggregate.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            final var channelType = ChannelType.DL_SITE;
            final var name = "Test Shop";

            // Act
            final var channel = AlbumAcquisitionChannel.create(channelType, name, null, null);

            // Assert
            assertThat(channel).isNotNull();
            assertThat(channel.id()).isNotNull();
            assertThat(channel.getChannelType()).isEqualTo(channelType);
            assertThat(channel.getName()).isEqualTo(name);
            assertThat(channel.getUrl()).isNull();
            assertThat(channel.getNote()).isNull();
        }

        @Test
        @DisplayName("すべてのフィールドを指定して生成できること")
        void createWithAllFieldsShouldSucceed() {
            // Arrange
            final var channelType = ChannelType.STREAMING;
            final var name = "My Bandcamp";
            final var url = Url.of("https://example.bandcamp.com");
            final var note = "デジタル版のみ";

            // Act
            final var channel = AlbumAcquisitionChannel.create(channelType, name, url, note);

            // Assert
            assertThat(channel).isNotNull();
            assertThat(channel.getChannelType()).isEqualTo(channelType);
            assertThat(channel.getName()).isEqualTo(name);
            assertThat(channel.getUrl()).isEqualTo(url);
            assertThat(channel.getNote()).isEqualTo(note);
        }

        @Test
        @DisplayName("チャネルタイプがnullの場合は例外が発生すること")
        void createWithNullChannelTypeShouldThrowException() {
            // Arrange
            final var name = "Test Shop";

            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumAcquisitionChannel.create(null, name, null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Channel type cannot be null");
        }

        @Test
        @DisplayName("名前がnullの場合は例外が発生すること")
        void createWithNullNameShouldThrowException() {
            // Arrange
            final var channelType = ChannelType.DL_SITE;

            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumAcquisitionChannel.create(channelType, null, null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Name cannot be blank");
        }

        @Test
        @DisplayName("名前が空文字列の場合は例外が発生すること")
        void createWithBlankNameShouldThrowException() {
            // Arrange
            final var channelType = ChannelType.DL_SITE;

            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumAcquisitionChannel.create(channelType, "   ", null, null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Name cannot be blank");
        }
    }

    @Nested
    @DisplayName("再構成テスト")
    class ReconstructTest {

        @Test
        @DisplayName("永続化層から再構成できること")
        void reconstructShouldSucceed() {
            // Arrange
            final var id = AlbumAcquisitionChannel.Id.generate();
            final var channelType = ChannelType.STREAMING;
            final var name = "My Bandcamp";
            final var url = Url.of("https://example.bandcamp.com");
            final var note = "デジタル版のみ";

            // Act
            final var channel = AlbumAcquisitionChannel.reconstruct(id, channelType, name, url, note);

            // Assert
            assertThat(channel).isNotNull();
            assertThat(channel.id()).isEqualTo(id);
            assertThat(channel.getChannelType()).isEqualTo(channelType);
            assertThat(channel.getName()).isEqualTo(name);
            assertThat(channel.getUrl()).isEqualTo(url);
            assertThat(channel.getNote()).isEqualTo(note);
        }
    }

    @Nested
    @DisplayName("チャネルタイプ変更テスト")
    class ChangeChannelTypeTest {

        @Test
        @DisplayName("チャネルタイプを変更できること")
        void changeChannelTypeShouldSucceed() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);
            final var newChannelType = ChannelType.STREAMING;

            // Act
            final var updated = channel.changeChannelType(newChannelType);

            // Assert
            assertThat(updated.getChannelType()).isEqualTo(newChannelType);
            assertThat(updated.id()).isEqualTo(channel.id());
            assertThat(updated.getName()).isEqualTo(channel.getName());
        }

        @Test
        @DisplayName("nullのチャネルタイプに変更しようとすると例外が発生すること")
        void changeChannelTypeToNullShouldThrowException() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);

            // Act & Assert
            assertThatThrownBy(() -> {
                channel.changeChannelType(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Channel type cannot be null");
        }
    }

    @Nested
    @DisplayName("名前変更テスト")
    class ChangeNameTest {

        @Test
        @DisplayName("名前を変更できること")
        void changeNameShouldSucceed() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Old Name", null, null);
            final var newName = "New Name";

            // Act
            final var updated = channel.changeName(newName);

            // Assert
            assertThat(updated.getName()).isEqualTo(newName);
        }

        @Test
        @DisplayName("nullの名前に変更しようとすると例外が発生すること")
        void changeNameToNullShouldThrowException() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);

            // Act & Assert
            assertThatThrownBy(() -> {
                channel.changeName(null);
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Name cannot be blank");
        }

        @Test
        @DisplayName("空文字列の名前に変更しようとすると例外が発生すること")
        void changeNameToBlankShouldThrowException() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);

            // Act & Assert
            assertThatThrownBy(() -> {
                channel.changeName("   ");
            }).isInstanceOf(IllegalArgumentException.class).hasMessage("Name cannot be blank");
        }
    }

    @Nested
    @DisplayName("URL変更テスト")
    class ChangeUrlTest {

        @Test
        @DisplayName("URLを変更できること")
        void changeUrlShouldSucceed() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);
            final var url = Url.of("https://example.com");

            // Act
            final var updated = channel.changeUrl(url);

            // Assert
            assertThat(updated.getUrl()).isEqualTo(url);
        }

        @Test
        @DisplayName("URLをnullに変更できること")
        void changeUrlToNullShouldSucceed() {
            // Arrange
            final var url = Url.of("https://example.com");
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", url, null);

            // Act
            final var updated = channel.changeUrl(null);

            // Assert
            assertThat(updated.getUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("補足変更テスト")
    class ChangeNoteTest {

        @Test
        @DisplayName("補足を変更できること")
        void changeNoteShouldSucceed() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, null);
            final var note = "新しい補足情報";

            // Act
            final var updated = channel.changeNote(note);

            // Assert
            assertThat(updated.getNote()).isEqualTo(note);
        }

        @Test
        @DisplayName("補足をnullに変更できること")
        void changeNoteToNullShouldSucceed() {
            // Arrange
            final var channel = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop", null, "旧補足");

            // Act
            final var updated = channel.changeNote(null);

            // Assert
            assertThat(updated.getNote()).isNull();
        }
    }

    @Nested
    @DisplayName("ID型テスト")
    class IdTest {

        @Test
        @DisplayName("UUIDv7形式のIDを生成できること")
        void generateShouldCreateValidId() {
            // Act
            final var id = AlbumAcquisitionChannel.Id.generate();

            // Assert
            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
            assertThat(id.value().isBlank()).isFalse();
        }

        @Test
        @DisplayName("文字列からIDを生成できること")
        void ofShouldCreateIdFromString() {
            // Arrange
            final var id1 = AlbumAcquisitionChannel.Id.generate();
            final var value = id1.value();

            // Act
            final var id2 = AlbumAcquisitionChannel.Id.of(value);

            // Assert
            assertThat(id2).isEqualTo(id1);
            assertThat(id2.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("nullの文字列からIDを生成しようとすると例外が発生すること")
        void ofWithNullShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumAcquisitionChannel.Id.of(null);
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空文字列からIDを生成しようとすると例外が発生すること")
        void ofWithBlankStringShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumAcquisitionChannel.Id.of("");
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("不正なUUID形式の文字列からIDを生成しようとすると例外が発生すること")
        void ofWithInvalidUuidShouldThrowException() {
            // Act & Assert
            assertThatThrownBy(() -> {
                AlbumAcquisitionChannel.Id.of("not-a-uuid");
            }).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("等価性テスト")
    class EqualsTest {

        @Test
        @DisplayName("同じIDのAlbumAcquisitionChannelは等しいこと")
        void channelsWithSameIdShouldBeEqual() {
            // Arrange
            final var id = AlbumAcquisitionChannel.Id.generate();
            final var channel1 = AlbumAcquisitionChannel.reconstruct(id, ChannelType.DL_SITE, "Shop1", null, null);
            final var channel2 = AlbumAcquisitionChannel.reconstruct(id, ChannelType.STREAMING, "Shop2", null, null);

            // Act & Assert
            assertThat(channel2).isEqualTo(channel1);
            assertThat(channel2.hashCode()).isEqualTo(channel1.hashCode());
        }

        @Test
        @DisplayName("異なるIDのAlbumAcquisitionChannelは等しくないこと")
        void channelsWithDifferentIdShouldNotBeEqual() {
            // Arrange
            final var channel1 = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop1", null, null);
            final var channel2 = AlbumAcquisitionChannel.create(ChannelType.DL_SITE, "Shop1", null, null);

            // Act & Assert
            assertThat(channel2).isNotEqualTo(channel1);
        }
    }
}
