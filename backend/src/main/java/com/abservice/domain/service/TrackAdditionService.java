package com.abservice.domain.service;

import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * アルバムへのトラック追加（検証・組み立て）を担うドメインサービス
 *
 * <p>
 * 外部入力の生の値からTrackを検証・組み立て、対象のAlbumに追加するロジックを提供します。単体の
 * {@link com.abservice.application.service.album.AddTrackService}
 * だけでなく、アルバムと同時にトラックを登録するユースケースからも呼ばれる（{@code
 * com.abservice.application.service.album.RegisterAlbumWithTracksService}）ため、
 * 特定のCommandServiceに属さないドメインサービスとして切り出しています。トラック番号の重複はAlbum集約自身が検証します
 * （{@link com.abservice.domain.exception.BusinessRuleViolationException}）。
 * </p>
 */
@ApplicationScoped
public class TrackAdditionService implements DomainService {

    /**
     * 外部入力からトラックを検証・生成し、アルバムに追加する
     *
     * @param album
     *            追加先のアルバム
     * @param fields
     *            追加するトラックの入力値
     * @return 追加後のアルバムと追加されたトラックの組。検証失敗時は{@link ValidationException}で失敗する
     */
    public Uni<Addition> addTrack(Album album, TrackFields fields) {
        return Uni.createFrom()
                .item(
                        () -> validate(fields)
                                .map(track -> new Addition(album.addTrack(track), track))
                                .resolve(ValidationException::new));
    }

    /**
     * 追加するトラックの入力値
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistDisplayName
     *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
     * @param artistSortKey
     *            アーティストソートキー（nullable）
     */
    public record TrackFields(
            @Nullable Integer trackNo,
            @Nullable String title,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey) {
    }

    /**
     * トラック追加の結果
     *
     * @param album
     *            追加後のアルバム
     * @param track
     *            追加されたトラック
     */
    public record Addition(Album album, Track track) {
    }

    static Result<Track> validate(TrackFields fields) {
        return resolveArtistCredit(fields.artistDisplayName(), fields.artistSortKey())
                .flatMap(
                        artistCredit -> Track.fromInput(
                                fields.trackNo(),
                                fields.title(),
                                artistCredit.orElse(null)));
    }

    private static Result<Optional<ArtistCredit>> resolveArtistCredit(
            @Nullable String displayName,
            @Nullable String sortKey) {
        return Optional.ofNullable(displayName)
                .filter(StringUtils::isNotBlank)
                .map(
                        name -> ArtistCredit.fromInput(name, sortKey)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<ArtistCredit>>success(Optional.empty()));
    }
}
