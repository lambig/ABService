package com.abservice.application.service.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.service.albumarticle.UpdateAlbumArticleInput.DistributionInput;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumAcquisitionChannel;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.vo.album.ChannelType;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateAlbumArticleService.validateAndApply（更新差分適用の集約）のテスト")
class UpdateAlbumArticleServiceTest {

    private static final Album.Id ALBUM_ID = Album.Id.generate();

    private static AlbumArticle existingArticle() {
        return AlbumArticle.create(
                ALBUM_ID,
                "元の記事本文",
                "元のショートコメント",
                "東X-00a",
                null,
                null);
    }

    @Test
    @DisplayName("正常な入力は成功しCreate相当フィールドを置換する")
    void validInputSucceeds() {
        final var updated = UpdateAlbumArticleService.validateAndApply(
                existingArticle(),
                new UpdateAlbumArticleInput(
                        null,
                        "新記事本文",
                        "新ショートコメント",
                        "東X-00b",
                        "NEW",
                        new DistributionInput(
                                1000,
                                500,
                                "https://example.com/demo",
                                "補足")))
                .resolve();

        assertThat(updated.introLong()).isEqualTo("新記事本文");
        assertThat(updated.introShort()).isEqualTo("新ショートコメント");
        assertThat(updated.firstEventSpace()).isEqualTo("東X-00b");
        assertThat(updated.labelTag().name()).isEqualTo("NEW");
        assertThat(updated.distribution().getPhysicalPrice().amount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("albumIdと入手経路はUpdateの対象外のため既存の値を維持する")
    void albumIdAndAcquisitionChannelsAreUnaffected() {
        final var existing = existingArticle()
                .addAcquisitionChannel(
                        AlbumAcquisitionChannel.create(
                                ChannelType.ONLINE_SHOP,
                                "既存ショップ",
                                Url.of("https://example.com/shop"),
                                null));

        final var updated = UpdateAlbumArticleService.validateAndApply(
                existing,
                new UpdateAlbumArticleInput(
                        null,
                        "新記事本文",
                        null,
                        null,
                        null,
                        null))
                .resolve();

        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.getAcquisitionChannels()).hasSize(1);
    }

    @Test
    @DisplayName("labelTagが不正な値ならエラー")
    void invalidLabelTagFails() {
        final var result = UpdateAlbumArticleService.validateAndApply(
                existingArticle(),
                new UpdateAlbumArticleInput(
                        null,
                        null,
                        null,
                        null,
                        "BAD",
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("LABEL_TAG_INVALID");
    }

    @Test
    @DisplayName("頒布情報のデモURLが不正な形式ならエラー")
    void invalidDemoUrlFails() {
        final var result = UpdateAlbumArticleService.validateAndApply(
                existingArticle(),
                new UpdateAlbumArticleInput(
                        null,
                        null,
                        null,
                        null,
                        null,
                        new DistributionInput(
                                null,
                                null,
                                "not a url",
                                null)));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("URL_INVALID_FORMAT");
    }
}
