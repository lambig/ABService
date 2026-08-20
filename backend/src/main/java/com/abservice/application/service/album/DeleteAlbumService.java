package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * アルバム削除コマンドサービス
 *
 * <p>
 * べき等な削除ユースケースです。対象アルバムの存在有無は確認せず、常に成功として扱います
 * （DELETEの一般的なべき等性に倣う）。ただしアルバムIDの形式検証は行い、不正な形式は {@link ValidationException}
 * として扱います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class DeleteAlbumService implements CommandService<DeleteAlbumInput, DeleteAlbumOutput> {

    private final AlbumRepository albumRepository;

    @WithTransaction
    @Override
    public Uni<DeleteAlbumOutput> execute(DeleteAlbumInput input) {
        return Uni.createFrom()
                .item(
                        () -> Album.Id.fromInput(input.albumId())
                                .resolve(ValidationException::new))
                .flatMap(albumRepository::deleteById)
                .replaceWith(new DeleteAlbumOutput());
    }
}
