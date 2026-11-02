package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.service.AlbumDeletionService.AlbumDeletion;
import com.abservice.domain.service.AlbumDeletionService.ArticleEffect;
import com.abservice.domain.service.AlbumUnpublicationService.AlbumUnpublication;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("作品から記事への連動操作")
class AlbumArticleTransitionsTest {

    private static final Album.Id ALBUM_ID = Album.Id.of("01900000-0000-7000-8000-000000000001");
    private static final BusinessDateTime CREATED = BusinessDateTime.of(Instant.parse("2026-01-01T00:00:00Z"));
    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2026-02-01T00:00:00Z"));

    @Test
    @DisplayName("削除は公開記事を非公開にしてから参照を失効させ、元の影響を保持する")
    void deletionPreservesOriginalEffectsAndTransitionTime() {
        final var published = article("published").publish(CREATED);
        final var draft = article("draft");
        final var operation = new AlbumDeletion(ALBUM_ID, List.of(published, draft));

        final var changes = operation.apply(NOW);

        assertThat(changes).hasSize(2);
        assertThat(changes).extracting(change -> change.effect().becomesUnpublished()).containsExactly(true, false);
        assertThat(changes).extracting(change -> change.effect().article()).containsExactly(published, draft);
        assertThat(changes).allSatisfy(change -> {
            assertThat(change.article().isPublic()).isFalse();
            assertThat(change.article().updatedAtBusiness()).isEqualTo(NOW);
            assertThat(AlbumArticle.from(change.article())).hasValueSatisfying(
                    albumArticle -> assertThat(albumArticle.albumReference()).isEqualTo(
                            new AlbumReference.Lost(
                                    ALBUM_ID,
                                    NOW,
                                    AlbumReferenceLostReason.ALBUM_DELETED)));
        });
        assertThat(published.isPublic()).isTrue();
        assertThat(operation.effects()).extracting(ArticleEffect::becomesUnpublished).containsExactly(true, false);
    }

    @Test
    @DisplayName("作品非公開は公開記事だけを変更し、作品参照を維持する")
    void unpublicationChangesOnlyPublicArticles() {
        final var published = article("published").publish(CREATED);
        final var draft = article("draft");
        final var operation = new AlbumUnpublication(ALBUM_ID, List.of(published, draft));

        final var changed = operation.apply(NOW);

        assertThat(changed).singleElement().satisfies(article -> {
            assertThat(article.id()).isEqualTo(published.id());
            assertThat(article.isPublic()).isFalse();
            assertThat(article.updatedAtBusiness()).isEqualTo(NOW);
            assertThat(AlbumArticle.from(article)).hasValueSatisfying(
                    albumArticle -> assertThat(albumArticle.albumReference().activeAlbumId()).contains(ALBUM_ID));
        });
        assertThat(operation.articlesBecomingUnpublished()).containsExactly(published);
        assertThat(draft.updatedAtBusiness()).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("参照元がなければ両操作の変更も影響も空になる")
    void noReferencesProduceNoChanges() {
        assertThat(new AlbumDeletion(ALBUM_ID, List.of()).apply(NOW)).isEmpty();
        assertThat(new AlbumUnpublication(ALBUM_ID, List.of()).apply(NOW)).isEmpty();
    }

    @Test
    @DisplayName("変更後の下書きへの再適用で参照失効日時を上書きしない")
    void repeatedDeletionKeepsLostReference() {
        final var changed = new AlbumDeletion(ALBUM_ID, List.of(article("draft"))).apply(CREATED).getFirst().article();
        final var repeated = new AlbumDeletion(ALBUM_ID, List.of(changed)).apply(NOW).getFirst();
        assertThat(repeated.article()).isSameAs(changed);
        assertThat(repeated.effect().becomesUnpublished()).isFalse();
    }

    @Test
    @DisplayName("記事自身の非公開は連動操作なしで使える")
    void ownUnpublicationRemainsAvailable() {
        final var published = article("standalone").publish(CREATED);
        final var changed = published.unpublish(NOW);
        assertThat(changed.isPublic()).isFalse();
        assertThat(AlbumArticle.from(changed)).hasValueSatisfying(
                albumArticle -> assertThat(albumArticle.albumReference().activeAlbumId()).contains(ALBUM_ID));
    }

    private static Article article(String title) {
        return Article.create(
                ArticleType.ALBUM,
                ALBUM_ID,
                new ArticleTitle(title),
                null,
                null,
                CREATED);
    }
}
