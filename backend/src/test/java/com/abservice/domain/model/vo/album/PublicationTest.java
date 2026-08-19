package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.common.BusinessDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("アルバムの公開情報")
class PublicationTest {

    private static final BusinessDateTime PUBLISHED_AT = BusinessDateTime.of(Instant.parse("2024-06-01T00:00:00Z"));

    @DisplayName("draft()は未公開状態を返す")
    @Test
    void testDraft() {
        final Publication publication = Publication.draft();

        assertThat(publication.isPublished()).isFalse();
        assertThat(publication.publishedAt()).isEmpty();
    }

    @DisplayName("published()は公開日時を保持する公開状態を返す")
    @Test
    void testPublished() {
        final Publication publication = Publication.published(PUBLISHED_AT);

        assertThat(publication.isPublished()).isTrue();
        assertThat(publication.publishedAt()).contains(PUBLISHED_AT);
    }

    @DisplayName("published()にnullを渡すと例外となる")
    @Test
    void testPublishedWithNullThrows() {
        assertThatThrownBy(() -> Publication.published(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Published at cannot be null");
    }

    @DisplayName("draft()は常に等価なインスタンスを返す")
    @Test
    void testDraftIsAlwaysEquivalent() {
        assertThat(Publication.draft()).isEqualTo(Publication.draft());
    }

    @DisplayName("同じ公開日時のPublishedは同等と判定される")
    @Test
    void testEquivalentToSamePublished() {
        final Publication publication1 = Publication.published(PUBLISHED_AT);
        final Publication publication2 = Publication.published(PUBLISHED_AT);

        assertThat(publication1.equivalentTo(publication2)).isTrue();
    }

    @DisplayName("異なる公開日時のPublishedは同等でないと判定される")
    @Test
    void testEquivalentToDifferentPublished() {
        final Publication publication1 = Publication.published(PUBLISHED_AT);
        final Publication publication2 = Publication
                .published(BusinessDateTime.of(Instant.parse("2024-07-01T00:00:00Z")));

        assertThat(publication1.equivalentTo(publication2)).isFalse();
    }

    @DisplayName("DraftとPublishedは同等でないと判定される")
    @Test
    void testEquivalentToDraftAndPublished() {
        final Publication draft = Publication.draft();
        final Publication published = Publication.published(PUBLISHED_AT);

        assertThat(draft.equivalentTo(published)).isFalse();
        assertThat(published.equivalentTo(draft)).isFalse();
    }

    @DisplayName("nullとの同等判定はfalseとなる")
    @Test
    void testEquivalentToNull() {
        assertThat(Publication.draft().equivalentTo(null)).isFalse();
        assertThat(Publication.published(PUBLISHED_AT).equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ公開日時のPublishedは等価となる")
    @Test
    void testEquality() {
        final Publication publication1 = Publication.published(PUBLISHED_AT);
        final Publication publication2 = Publication.published(PUBLISHED_AT);

        assertThat(publication1).isEqualTo(publication2);
        assertThat(publication1.hashCode()).isEqualTo(publication2.hashCode());
    }
}
