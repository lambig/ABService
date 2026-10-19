package com.abservice.infrastructure.persistence.datasource;

import com.abservice.application.query.PageCounts;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

/** 作品数によらず、ページ・件数・指定作品の最大3クエリで準備状態を取得する。 */
@ApplicationScoped
@AllArgsConstructor
public class ListeningPreparationDataSource {
    public static final int PAGE_SIZE = 50;
    private static final String SELECT = """
            select new com.abservice.infrastructure.persistence.datasource.ListeningPreparationRow(
                a.domainId, a.title, a.publishedAt, r.state)
            from AlbumTableRecord a
            left join AlbumCrossfadeTableRecord c on c.albumId = a.albumId
            left join PrivateAudioRegistrationTableRecord r on r.audioId = c.audioId
            """;
    private final Mutiny.SessionFactory sessions;

    public Uni<Page> page(int page, @Nullable String albumId) {
        return sessions.withSession(
                session -> session.createQuery(
                        SELECT + " order by a.domainId desc",
                        ListeningPreparationRow.class)
                        .setFirstResult(Math.multiplyExact(page, PAGE_SIZE)).setMaxResults(PAGE_SIZE).getResultList()
                        .chain(
                                items -> session.createQuery("select count(a) from AlbumTableRecord a", Long.class)
                                        .getSingleResult().map(
                                                count -> new Page(
                                                        items,
                                                        page,
                                                        PageCounts.totalPages(count, PAGE_SIZE),
                                                        null)))
                        .chain(
                                result -> withFocus(
                                        session,
                                        result,
                                        albumId)));
    }

    private static Uni<Page> withFocus(
            Mutiny.Session session,
            Page page,
            @Nullable String albumId) {
        return Optional.ofNullable(albumId)
                .map(
                        id -> session
                                .createQuery(SELECT + " where a.domainId = :albumId", ListeningPreparationRow.class)
                                .setParameter("albumId", id).getSingleResultOrNull()
                                .map(
                                        focused -> new Page(
                                                page.items(),
                                                page.page(),
                                                page.totalPages(),
                                                focused)))
                .orElseGet(() -> Uni.createFrom().item(page));
    }

    public record Page(List<ListeningPreparationRow> items, int page, int totalPages,
            @Nullable ListeningPreparationRow focused) {
    }
}
