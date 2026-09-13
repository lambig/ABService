package com.abservice.application.query.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * チューン名を繋ぐ区切りの既定値の統合テスト
 *
 * <p>
 * タイトルを持たないトラックの名は、チューン名をこの区切りで繋いだものになる（#360）。既定は半角スペース・
 * スラッシュ・半角スペースだが、<b>{@code .properties} は値の先頭の空白を落とす</b>ため、設定ファイルは
 * 空白をユニコードエスケープで書いている。読み込んだ結果が意図どおりかは、実際に設定を解かせないと分からない。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("チューン名を繋ぐ区切りの既定値の統合テスト")
class TuneTitleSeparatorConfigIntegrationTest {

    @Inject
    @ConfigProperty(name = "abservice.track.tune-title-separator")
    private String tuneTitleSeparator;

    @Test
    @DisplayName("既定の区切りは、前後に半角スペースを持つスラッシュである")
    void defaultSeparatorShouldBeSlashSurroundedBySpaces() {
        assertThat(tuneTitleSeparator).isEqualTo(" / ");
    }
}
