package com.abservice.infrastructure.persistence.datasource;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
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
     * 並びは公開サイトの作品一覧（カタログナンバーの降順、未付与は末尾、同値はドメインIDの降順）に揃える。 公開サイトは
     * {@code sort=catalogNumber} を明示して一覧を引くため、会場の端末でも同じ順に並ぶ。
     */
    private static final String DISTRIBUTABLE_ALBUMS = """
            FROM AlbumTableRecord a
                JOIN AlbumCrossfadeTableRecord c ON c.albumId = a.albumId
                JOIN PrivateAudioRegistrationTableRecord r ON r.audioId = c.audioId
            """;
    private static final String DISTRIBUTABLE = "a.publishedAt IS NOT NULL AND r.state = 'CONFIRMED'";
    private static final String SNAPSHOT = """
            SELECT new com.abservice.infrastructure.persistence.datasource.ListeningPackageRow(
                a.domainId, a.title, r.audioId, r.byteLength, r.sha256, r.sampleRate, r.totalSamples,
                t.domainId, t.trackNo, t.title, tt.id.seq, tt.tuneTitle)
            """ + DISTRIBUTABLE_ALBUMS + """
                LEFT JOIN a.tracks t
                LEFT JOIN t.trackTunes tt
            """ + "WHERE " + DISTRIBUTABLE
            + "\nORDER BY a.catalogNumber DESC NULLS LAST, a.domainId DESC, t.trackNo, tt.id.seq";

    /** 配布対象の作品に関連付いた音源か。snapshot と同じ絞り込みで、URL解決を現在のパッケージの範囲に閉じる。 */
    private static final String DISTRIBUTES = "SELECT COUNT(a) " + DISTRIBUTABLE_ALBUMS + "WHERE " + DISTRIBUTABLE
            + " AND r.audioId = :audioId";

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

    /**
     * 音源が現在の配布対象に含まれるかを返します。
     *
     * @param audioId
     *            音源の登録ID
     * @return 公開済み作品の確定クロスフェードとして関連付いていれば真
     */
    public Uni<Boolean> distributes(UUID audioId) {
        return sessionFactory.withSession(
                session -> session.createQuery(DISTRIBUTES, Long.class)
                        .setParameter("audioId", audioId)
                        .getSingleResult())
                .map(count -> count > 0);
    }
}
