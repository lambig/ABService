package com.abservice.presentation.rest.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * オリジンへの到達制限の統合テスト
 *
 * <p>
 * 自分の配信だけが付ける値を持たない要求が、公開APIへ届かないことを実際のHTTPリクエストで確認する。
 * 検査する値は既定の設定に無いため、このテストだけプロファイルで与える（他の統合テストは配信を挟まず、 値が空のまま検査されない状態で動く）。
 * </p>
 *
 * <p>
 * 稼働確認の経路（{@code /q/*}）を自分自身から引く場合の素通しは、ここでは確認できない。テストは loopback
 * から接続するため常にその条件を満たす。公開ポート経由での拒否は CI の container-check が見る。
 * </p>
 */
@QuarkusTest
@TestProfile(OriginVerificationRestIntegrationTest.VerifyingOrigin.class)
@ExtendWith(CleanDatabase.class)
@DisplayName("オリジンへの到達制限の統合テスト")
class OriginVerificationRestIntegrationTest {

    private static final String TOKEN = "integration-test-origin-token";

    private static final String VERIFY_HEADER = "X-Origin-Verify";

    /** 配信が付ける値を設定した状態を作る */
    public static class VerifyingOrigin implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("abservice.origin.verify-token", TOKEN);
        }
    }

    @Test
    @DisplayName("値を持たない要求は公開APIへ届かない")
    void requestWithoutTokenIsRefused() {
        given().when().get("/api/v1/albums").then().statusCode(403);
    }

    @Test
    @DisplayName("値が違う要求は公開APIへ届かない")
    void requestWithWrongTokenIsRefused() {
        given().header(VERIFY_HEADER, TOKEN + "-wrong").when().get("/api/v1/albums").then().statusCode(403);
    }

    @Test
    @DisplayName("値を持つ要求は従来どおり届く")
    void requestWithTokenReachesTheApi() {
        given().header(VERIFY_HEADER, TOKEN).when().get("/api/v1/albums").then().statusCode(200);
    }

    @Test
    @DisplayName("拒否は本文を持たない（何を期待しているかを教えない）")
    void refusalCarriesNoBody() {
        given().when().get("/api/v1/albums").then().statusCode(403).body(emptyString());
    }
}
