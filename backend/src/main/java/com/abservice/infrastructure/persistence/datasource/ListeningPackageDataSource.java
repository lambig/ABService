package com.abservice.infrastructure.persistence.datasource;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.hibernate.reactive.mutiny.Mutiny;

/**
 * 配布パッケージの snapshot を読む DataSource
 *
 * <p>
 * 作品・確定音源・収録曲・チューン構成を1つの問い合わせで読む。複数の問い合わせに分けると、その間に関連付けや 公開状態が変わった作品が混ざり、1つの
 * packageVersion の中で実体とメタデータが食い違う。1問い合わせなら DB の1時点の読みになり、混在は構造上起きない。
 * </p>
 */
@ApplicationScoped
public class ListeningPackageDataSource {

    /**
     * 公開済みで、CONFIRMED のクロスフェードが関連付いた作品だけを対象にする。下書きは含めない。
     * 並びは公開向け一覧の既定（リリース日の新しい順、同値はドメインIDの降順）に揃える。
     */
    private static final String SNAPSHOT = """
            SELECT new com.abservice.infrastructure.persistence.datasource.ListeningPackageRow(
                a.domainId, a.title, r.audioId, r.byteLength, r.sha256, r.sampleRate, r.totalSamples,
                t.domainId, t.trackNo, t.title, tt.id.seq, tt.tuneTitle)
            FROM AlbumTableRecord a
                JOIN AlbumCrossfadeTableRecord c ON c.albumId = a.albumId
                JOIN PrivateAudioRegistrationTableRecord r ON r.audioId = c.audioId
                LEFT JOIN a.tracks t
                LEFT JOIN t.trackTunes tt
            WHERE a.publishedAt IS NOT NULL AND r.state = 'CONFIRMED'
            ORDER BY a.releaseDate DESC, a.domainId DESC, t.trackNo, tt.id.seq
            """;

    private final Mutiny.SessionFactory sessionFactory;

    public ListeningPackageDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * 配布対象の snapshot を読みます。
     *
     * @return 作品・曲・チューン構成の平坦な行（作品順・曲順・登場順）
     */
    public Uni<List<ListeningPackageRow>> snapshot() {
        return sessionFactory.withSession(
                session -> session.createQuery(SNAPSHOT, ListeningPackageRow.class).getResultList());
    }
}
