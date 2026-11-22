package com.abservice.application.service.album;

import com.abservice.application.exception.ConflictingEditException;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumAccessService;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * アルバム更新コマンドサービス
 *
 * <p>
 * 外部入力（{@link UpdateAlbumInput}）から既存 {@link Album} をPUT風に全項目置換するユースケースです。
 * <b>曲目と外部音源も同じ置換の対象</b>で、届いた並びがそのまま作品の曲目・音源になります（#391）。作品の子は
 * 作品の外に存在できないため、書く経路を子ごとに分けず、集約ルートに1つだけ置きます。
 * </p>
 *
 * <p>
 * 値の検証は入力自身が担い、本サービスは対象の取得・状態確認・集約への反映と保存を担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class UpdateAlbumService implements CommandService<UpdateAlbumInput, UpdateAlbumOutput> {

    private final AlbumRepository albumRepository;

    private final AlbumAccessService albumAccessService;

    @WithTransaction
    @Override
    public Uni<UpdateAlbumOutput> execute(UpdateAlbumInput input) {
        return input.asRequested()
                .flatMap(albumAccessService::findExistingAndClaimEditWithRevision)
                .map(claimed -> claimedAsOf(claimed, input))
                .call(input::asValidated)
                .map(
                        existing -> validateAndApply(existing, input)
                                .resolve(ValidationException::new))
                .flatMap(albumRepository::saveWithRevision)
                .map(UpdateAlbumService::toOutput);
    }

    /**
     * 掴んだ行の世代が、編集を始めた時点と同じであることを確かめる。
     *
     * <p>
     * 違っていれば、この更新が持っている値は既に古い。届いた値を最新へ適用すると、間に入った保存を消すため拒む。
     * </p>
     */
    private static Album claimedAsOf(AlbumRepository.Revisioned claimed, UpdateAlbumInput input) {
        return Objects.equals(
                claimed.revision()
                        .value(),
                input.expectedRevision())
                        ? claimed.album()
                        : conflicting(claimed);
    }

    private static Album conflicting(AlbumRepository.Revisioned claimed) {
        throw new ConflictingEditException(
                "アルバム %s は編集を始めた後に更新されています".formatted(
                        claimed.album()
                                .id()
                                .value()));
    }

    /**
     * 入力を検証し、既存の作品へ適用する。
     *
     * <p>
     * 本体の項目と曲目・外部音源を1つの結果へまとめる。曲目だけが誤っている場合も本体の誤りと並べて返すため、 呼び出し元は1往復ですべての位置を受け取る。
     * </p>
     */
    Result<Album> validateAndApply(Album existing, UpdateAlbumInput input) {
        return input.bodyValues()
                .map(values -> applied(existing, values));
    }

    private static Album applied(Album existing, RegisterAlbumWithTracksInput.Values values) {
        final var body = values.album();
        return existing.changeTitle(body.title())
                .changeReleaseDate(body.releaseDate())
                .changeArtistCredit(body.artistCredit())
                .changeDescription(body.description())
                .changeEventReleasedAt(body.event())
                .changeCatalogNumber(body.catalogNumber())
                .changeIsdn(body.isdn())
                .changeCoverImageKey(body.coverImageKey())
                .changeBasePrice(body.basePrice())
                .changeOriginalWorkNote(body.originalWorkNote())
                .replaceTracks(values.tracks())
                .replaceExternalAudios(values.externalAudios());
    }

    private static UpdateAlbumOutput toOutput(AlbumRepository.Revisioned saved) {
        return new UpdateAlbumOutput(
                saved.album()
                        .id()
                        .value(),
                saved.revision()
                        .value(),
                saved.album()
                        .title()
                        .value(),
                saved.album()
                        .releaseDate()
                        .asLocalDate()
                        .toString(),
                saved.album()
                        .artistCredit()
                        .displayName()
                        .value());
    }
}
