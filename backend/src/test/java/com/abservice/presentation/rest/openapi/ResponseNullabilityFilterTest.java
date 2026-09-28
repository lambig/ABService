package com.abservice.presentation.rest.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 実際の応答 record を使い、項目単位の出力制御と null 許容が定義へどう写るかを見る。入れ子の record は親のスキーマから
 * 辿って引かれるため、親のスキーマも置く。
 */
@DisplayName("応答項目の必須・null 許容の定義への反映")
class ResponseNullabilityFilterTest {

    @Test
    @DisplayName("null のとき項目ごと省く項目は、必須から外れ null 型も付かない")
    void omittedWhenNullIsOptionalButNotNullable() {
        final var album = schema(
                List.of(
                        "albumId",
                        "title",
                        "artistDisplayName",
                        "releaseDate",
                        "catalogNumber",
                        "description",
                        "descriptionFormat",
                        "originalWorkNote",
                        "artworkAssetId",
                        "tracks"));
        filter(
                Map.of(
                        "ListeningPackageResponse",
                        OASFactory.createSchema(),
                        "ListeningAlbumResponse",
                        album));

        assertThat(album.getRequired()).containsExactlyInAnyOrder(
                "albumId",
                "title",
                "artistDisplayName",
                "tracks");
        List.of(
                "releaseDate",
                "catalogNumber",
                "description",
                "descriptionFormat",
                "originalWorkNote",
                "artworkAssetId")
                .forEach(
                        property -> assertThat(album.getProperties().get(property).getType())
                                .as(property)
                                .containsExactly(Schema.SchemaType.STRING));
    }

    @Test
    @DisplayName("出力制御を持たない @Nullable の項目は、必須のまま null 型が付く")
    void nullableWithoutOutputControlStaysRequiredAndNullable() {
        final var device = schema(
                List.of(
                        "deviceId",
                        "label",
                        "state",
                        "createdAt",
                        "expiresAt",
                        "revokedAt"));
        filter(Map.of("ListeningDeviceResponse", device));

        assertThat(device.getRequired()).contains("revokedAt");
        assertThat(device.getProperties().get("revokedAt").getType())
                .contains(Schema.SchemaType.STRING, Schema.SchemaType.NULL);
    }

    private static void filter(Map<String, Schema> schemas) {
        new ResponseNullabilityFilter().filterOpenAPI(
                OASFactory.createOpenAPI().components(OASFactory.createComponents().schemas(schemas)));
    }

    private static Schema schema(List<String> properties) {
        final var schema = OASFactory.createSchema();
        properties.forEach(
                property -> schema.addProperty(
                        property,
                        OASFactory.createSchema().addType(Schema.SchemaType.STRING)));
        return schema;
    }
}
