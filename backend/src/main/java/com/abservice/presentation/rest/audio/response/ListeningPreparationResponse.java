package com.abservice.presentation.rest.audio.response;

import com.abservice.application.query.audio.GetListeningPreparationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record ListeningPreparationResponse(List<PreparationAlbumResponse> items, int page, int totalPages,
        @Nullable PreparationAlbumResponse focused, List<ListeningDeviceResponse> devices) {
    public static ListeningPreparationResponse of(GetListeningPreparationService.Result result) {
        return new ListeningPreparationResponse(result.items().stream().map(PreparationAlbumResponse::of).toList(),
                result.page(), result.totalPages(),
                Optional.ofNullable(result.focused())
                        .map(PreparationAlbumResponse::of)
                        .orElse(null),
                result.devices().stream().map(ListeningDeviceResponse::of).toList());
    }

    public record PreparationAlbumResponse(String albumId, String title, @Nullable Instant publishedAt,
            boolean audioConfirmed) {
        static PreparationAlbumResponse of(GetListeningPreparationService.Album album) {
            return new PreparationAlbumResponse(album.albumId(), album.title(), album.publishedAt(),
                    album.audioConfirmed());
        }
    }
}
