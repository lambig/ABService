package com.abservice.infrastructure.persistence.repository;

import com.abservice.application.port.AlbumAudioAssignments;
import com.abservice.infrastructure.persistence.entity.AlbumCrossfadeTableRecord;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

/** 呼出元のAlbum参照ロックとトランザクションを共有する。外部I/Oは行わない。 */
@ApplicationScoped
public class DatabaseAlbumAudioAssignments implements AlbumAudioAssignments {
    @Override
    public Uni<Optional<Assignment>> findCrossfade(String albumId) {
        return Panache.getSession().chain(session -> session.createQuery("""
                SELECT c FROM AlbumCrossfadeTableRecord c, AlbumTableRecord a
                WHERE c.albumId = a.albumId AND a.domainId = :id
                """, AlbumCrossfadeTableRecord.class).setParameter("id", albumId).getResultList())
                .map(rows -> rows.stream().findFirst().map(row -> new Assignment(row.getAudioId(), row.getRevision())));
    }

    @Override
    public Uni<Boolean> replaceCrossfade(
            String albumId,
            UUID audioId,
            int expectedRevision) {
        return Panache.getSession().chain(
                session -> session.createNativeQuery(
                        expectedRevision == 0
                                ? """
                                        INSERT INTO album_crossfade (album_id, audio_id, revision)
                                        SELECT a.album_id, r.audio_id, 1 FROM album a, private_audio_registration r
                                        WHERE a.domain_id = :album AND r.audio_id = :audio AND r.state = 'CONFIRMED'
                                            AND :revision = 0
                                        ON CONFLICT (album_id) DO NOTHING
                                        """
                                : """
                                        UPDATE album_crossfade c SET audio_id = :audio, revision = c.revision + 1
                                        FROM album a, private_audio_registration r
                                        WHERE a.album_id = c.album_id AND a.domain_id = :album
                                            AND c.revision = :revision AND r.audio_id = :audio AND r.state = 'CONFIRMED'
                                        """)
                        .setParameter("album", albumId).setParameter("audio", audioId)
                        .setParameter("revision", expectedRevision).executeUpdate())
                .map(count -> count == 1);
    }
}
