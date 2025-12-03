package com.abservice.domain.model.vo.article;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleTypeTest {

    @Test
    void testEnumValues() {
        assertThat(ArticleType.values()).containsExactly(ArticleType.ALBUM, ArticleType.NOTE, ArticleType.NEWS,
                ArticleType.EVENT, ArticleType.OTHER);
    }

    @Test
    void testValueOfALBUM() {
        ArticleType type = ArticleType.valueOf("ALBUM");
        assertThat(type).isEqualTo(ArticleType.ALBUM);
    }

    @Test
    void testValueOfNOTE() {
        ArticleType type = ArticleType.valueOf("NOTE");
        assertThat(type).isEqualTo(ArticleType.NOTE);
    }

    @Test
    void testValueOfNEWS() {
        ArticleType type = ArticleType.valueOf("NEWS");
        assertThat(type).isEqualTo(ArticleType.NEWS);
    }

    @Test
    void testValueOfEVENT() {
        ArticleType type = ArticleType.valueOf("EVENT");
        assertThat(type).isEqualTo(ArticleType.EVENT);
    }

    @Test
    void testValueOfOTHER() {
        ArticleType type = ArticleType.valueOf("OTHER");
        assertThat(type).isEqualTo(ArticleType.OTHER);
    }

    @Test
    void testName() {
        assertThat(ArticleType.ALBUM.name()).isEqualTo("ALBUM");
        assertThat(ArticleType.NOTE.name()).isEqualTo("NOTE");
        assertThat(ArticleType.NEWS.name()).isEqualTo("NEWS");
        assertThat(ArticleType.EVENT.name()).isEqualTo("EVENT");
        assertThat(ArticleType.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    void testEnumCount() {
        assertThat(ArticleType.values()).hasSize(5);
    }
}
