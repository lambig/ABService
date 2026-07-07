package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlTest {

    @Test
    void testCreateValidUrlHttp() {
        Url url = new Url("http://example.com");
        assertThat(url.value()).isEqualTo("http://example.com");
    }

    @Test
    void testCreateValidUrlHttps() {
        Url url = new Url("https://example.com");
        assertThat(url.value()).isEqualTo("https://example.com");
    }

    @Test
    void testCreateValidUrlWithPath() {
        Url url = new Url("https://example.com/path/to/page");
        assertThat(url.value()).isEqualTo("https://example.com/path/to/page");
    }

    @Test
    void testCreateValidUrlWithQuery() {
        Url url = new Url("https://example.com/search?q=test&lang=ja");
        assertThat(url.value()).isEqualTo("https://example.com/search?q=test&lang=ja");
    }

    @Test
    void testCreateValidUrlWithFragment() {
        Url url = new Url("https://example.com/page#section");
        assertThat(url.value()).isEqualTo("https://example.com/page#section");
    }

    @Test
    void testCreateValidUrlMaxLength() {
        String domain = "https://example.com/";
        String path = "a".repeat(500 - domain.length());
        Url url = new Url(domain + path);
        assertThat(url.value()).hasSize(500);
    }

    @Test
    void testCreateUrlNull() {
        assertThatThrownBy(() -> new Url(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be blank");
    }

    @Test
    void testCreateUrlEmpty() {
        assertThatThrownBy(() -> new Url("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be blank");
    }

    @Test
    void testCreateUrlBlank() {
        assertThatThrownBy(() -> new Url("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be blank");
    }

    @Test
    void testCreateUrlTooLong() {
        String tooLongUrl = "https://example.com/" + "a".repeat(500);
        assertThatThrownBy(() -> new Url(tooLongUrl)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL must be 500 characters or less");
    }

    @Test
    void testCreateUrlInvalidFormat() {
        assertThatThrownBy(() -> new Url("not a valid url")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCreateUrlInvalidScheme() {
        assertThatThrownBy(() -> new Url("ht tp://example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEquivalentToSame() {
        Url url1 = new Url("https://example.com");
        Url url2 = new Url("https://example.com");

        assertThat(url1.equivalentTo(url2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        Url url1 = new Url("https://example.com");
        Url url2 = new Url("https://different.com");

        assertThat(url1.equivalentTo(url2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        Url url = new Url("https://example.com");
        assertThat(url.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        Url url1 = new Url("https://example.com");
        Url url2 = new Url("https://example.com");
        Url url3 = new Url("https://different.com");

        assertThat(url1).isEqualTo(url2);
        assertThat(url1).isNotEqualTo(url3);
    }

    @Test
    void testHashCode() {
        Url url1 = new Url("https://example.com");
        Url url2 = new Url("https://example.com");

        assertThat(url1.hashCode()).isEqualTo(url2.hashCode());
    }

    @Test
    void testUrlWithJapaneseDomain() {
        Url url = new Url("https://例え.jp/パス");
        assertThat(url.value()).isEqualTo("https://例え.jp/パス");
    }
}
