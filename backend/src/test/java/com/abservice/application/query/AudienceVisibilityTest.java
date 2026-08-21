package com.abservice.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.datasource.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AudienceVisibility（要求元→検索スコープ変換）のテスト")
class AudienceVisibilityTest {

    @Test
    @DisplayName("公開向けは公開中のみを対象にする")
    void publicYieldsPublicOnly() {
        assertThat(AudienceVisibility.of(Audience.PUBLIC)).isEqualTo(Visibility.PUBLIC_ONLY);
    }

    @Test
    @DisplayName("管理向けは下書きを含む全件を対象にする")
    void adminYieldsAll() {
        assertThat(AudienceVisibility.of(Audience.ADMIN)).isEqualTo(Visibility.ALL);
    }
}
