package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("URL値オブジェクト")
class UrlTest {

    @DisplayName("http形式のURLで生成でき、値が保持される")
    @Test
    void testCreateValidUrlHttp() {
        final Url url = new Url("http://example.com");
        assertThat(url.value()).isEqualTo("http://example.com");
    }

    @DisplayName("https形式のURLで生成でき、値が保持される")
    @Test
    void testCreateValidUrlHttps() {
        final Url url = new Url("https://example.com");
        assertThat(url.value()).isEqualTo("https://example.com");
    }

    @DisplayName("パス付きURLで生成でき、値が保持される")
    @Test
    void testCreateValidUrlWithPath() {
        final Url url = new Url("https://example.com/path/to/page");
        assertThat(url.value()).isEqualTo("https://example.com/path/to/page");
    }

    @DisplayName("クエリパラメータ付きURLで生成でき、値が保持される")
    @Test
    void testCreateValidUrlWithQuery() {
        final Url url = new Url("https://example.com/search?q=test&lang=ja");
        assertThat(url.value()).isEqualTo("https://example.com/search?q=test&lang=ja");
    }

    @DisplayName("フラグメント付きURLで生成でき、値が保持される")
    @Test
    void testCreateValidUrlWithFragment() {
        final Url url = new Url("https://example.com/page#section");
        assertThat(url.value()).isEqualTo("https://example.com/page#section");
    }

    @DisplayName("最大長500文字のURLで生成できる")
    @Test
    void testCreateValidUrlMaxLength() {
        final String domain = "https://example.com/";
        final String path = "a".repeat(500 - domain.length());
        final Url url = new Url(domain + path);
        assertThat(url.value()).hasSize(500);
    }

    @DisplayName("nullを渡すとIllegalArgumentException（URL cannot be blank）を送出する")
    @Test
    void testCreateUrlNull() {
        assertThatThrownBy(() -> new Url(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be blank");
    }

    @DisplayName("空文字を渡すとIllegalArgumentException（URL cannot be blank）を送出する")
    @Test
    void testCreateUrlEmpty() {
        assertThatThrownBy(() -> new Url("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be blank");
    }

    @DisplayName("空白のみの文字列を渡すとIllegalArgumentException（URL cannot be blank）を送出する")
    @Test
    void testCreateUrlBlank() {
        assertThatThrownBy(() -> new Url("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be blank");
    }

    @DisplayName("500文字を超えるURLはIllegalArgumentException（URL must be 500 characters or less）を送出する")
    @Test
    void testCreateUrlTooLong() {
        final String tooLongUrl = "https://example.com/" + "a".repeat(500);
        assertThatThrownBy(() -> new Url(tooLongUrl)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL must be 500 characters or less");
    }

    @DisplayName("不正な形式の文字列はIllegalArgumentExceptionを送出する")
    @Test
    void testCreateUrlInvalidFormat() {
        assertThatThrownBy(() -> new Url("not a valid url")).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("スキームに空白を含む不正なURLはIllegalArgumentExceptionを送出する")
    @Test
    void testCreateUrlInvalidScheme() {
        assertThatThrownBy(() -> new Url("ht tp://example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("同じ値のURL同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentToSame() {
        final Url url1 = new Url("https://example.com");
        final Url url2 = new Url("https://example.com");

        assertThat(url1.equivalentTo(url2)).isTrue();
    }

    @DisplayName("異なる値のURL同士はequivalentToがfalseを返す")
    @Test
    void testEquivalentToDifferent() {
        final Url url1 = new Url("https://example.com");
        final Url url2 = new Url("https://different.com");

        assertThat(url1.equivalentTo(url2)).isFalse();
    }

    @DisplayName("nullとの比較ではequivalentToがfalseを返す")
    @Test
    void testEquivalentToNull() {
        final Url url = new Url("https://example.com");
        assertThat(url.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値はequalsで等しく、異なる値は等しくない")
    @Test
    void testEquality() {
        final Url url1 = new Url("https://example.com");
        final Url url2 = new Url("https://example.com");
        final Url url3 = new Url("https://different.com");

        assertThat(url1).isEqualTo(url2);
        assertThat(url1).isNotEqualTo(url3);
    }

    @DisplayName("同じ値のURLは同一のhashCodeを返す")
    @Test
    void testHashCode() {
        final Url url1 = new Url("https://example.com");
        final Url url2 = new Url("https://example.com");

        assertThat(url1.hashCode()).isEqualTo(url2.hashCode());
    }

    @DisplayName("日本語ドメイン・日本語パスを含むURLで生成でき、値が保持される")
    @Test
    void testUrlWithJapaneseDomain() {
        final Url url = new Url("https://例え.jp/パス");
        assertThat(url.value()).isEqualTo("https://例え.jp/パス");
    }
}
