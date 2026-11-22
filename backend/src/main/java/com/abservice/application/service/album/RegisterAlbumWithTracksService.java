package com.abservice.application.service.album;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.service.AlbumCreationService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * アルバムとその初期トラック一覧をワンリクエストで登録するコマンドサービス
 *
 * <p>
 * 作品の子は作品の外に存在できないため、書く経路は集約ルートに1つだけ置きます（#391）。入力自身が値を検証し、
 * 本サービスは1つのAlbumを組み立て、1回だけ永続化します。曲目も外部音源も**並びごと**受け取り、届いた配列がそのまま順になります。
 * </p>
 *
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.CONFLICT})
public class RegisterAlbumWithTracksService
        implements
            CommandService<RegisterAlbumWithTracksInput, RegisterAlbumWithTracksOutput> {

    private final AlbumRepository albumRepository;
    private final AlbumCreationService albumCreationService;

    /** チューン名を繋ぐ区切り（#360）。応答はトラックの名を返すため、組み立てにここでも要る */
    @ConfigProperty(name = "abservice.track.tune-title-separator")
    private final String tuneTitleSeparator;

    @WithTransaction
    @Override
    public Uni<RegisterAlbumWithTracksOutput> execute(RegisterAlbumWithTracksInput input) {
        return input.asValidated()
                .map(this::registered)
                .flatMap(albumRepository::save)
                .map(saved -> toOutput(saved, tuneTitleSeparator));
    }

    private Album registered(RegisterAlbumWithTracksInput input) {
        final var values = input.parsed()
                .resolve(ValidationException::new);
        return albumCreationService.create(values.album())
                .replaceTracks(values.tracks())
                .replaceExternalAudios(values.externalAudios());
    }

    /* 応答が返すのは入力の写しではなくトラックの名（#360）。タイトルを省いたトラックはチューン名で名乗る */
    private static RegisterAlbumWithTracksOutput toOutput(Album album, String tuneTitleSeparator) {
        return new RegisterAlbumWithTracksOutput(
                album.id()
                        .value(),
                album.title()
                        .value(),
                album.releaseDate()
                        .asLocalDate()
                        .toString(),
                album.artistCredit()
                        .displayName()
                        .value(),
                album.getTracksSortedByTrackNo()
                        .stream()
                        .map(
                                track -> new RegisterAlbumWithTracksOutput.TrackSummary(
                                        track.id()
                                                .value(),
                                        track.trackNo(),
                                        track.name(tuneTitleSeparator)
                                                .value()))
                        .toList());
    }
}
