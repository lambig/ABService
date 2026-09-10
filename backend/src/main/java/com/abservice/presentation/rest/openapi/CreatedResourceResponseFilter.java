package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.openapi.DeclaredEndpoints.Endpoint;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.Optional;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.headers.Header;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * 資源を作る操作の応答（201 と {@code Location}）を API 定義へ反映する OpenAPI フィルタ
 *
 * <p>
 * 実装は {@code RestResponse} で 201 と {@code Location} を組むが、smallrye-openapi
 * は戻り値から 状態コードもヘッダも読まないため、定義には成功応答が 200 として現れ、{@code Location} は現れない。要求元はこの定義から
 * 型を生成するので、放っておくと実在しない 200 を待ち、位置を型として受け取れない。
 * </p>
 *
 * <p>
 * どの操作が資源を作るかは {@link CreatesResource} が宣言し、{@link DeclaredEndpoints} が対応する操作を
 * 同定する。成功応答を 200 から 201 へ移し、{@code Location} を足す。状態コードを実装と定義の双方へ書き写さない
 * ための機構であり、エンドポイントごとの {@code @APIResponse} は置かない。宣言と実応答の対応は
 * {@code LayeredArchitectureTest} が {@code CreatedResponses} の利用と双方向に突き合わせて守る。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class CreatedResourceResponseFilter implements OASFilter {

    private static final String OK = "200";
    private static final String CREATED = "201";
    private static final String LOCATION = "Location";

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        final Paths paths = Optional.ofNullable(openAPI.getPaths())
                .orElseGet(OASFactory::createPaths);

        DeclaredEndpoints.declaring(CreatesResource.class)
                .forEach(endpoint -> applyTo(paths, endpoint));
    }

    /**
     * 宣言された操作を定義から引き、その成功応答を書き換える。
     *
     * <p>
     * 定義側を走査して宣言と突き合わせるのではなく、宣言を起点に定義を引く。引けないのは経路の綴りが定義とずれている ときで、そのまま進めると定義は 200
     * のまま残る。黙って無効化されないよう、組み立てを落とす。
     * </p>
     */
    private static void applyTo(Paths paths, Endpoint endpoint) {
        final APIResponses responses = Optional.ofNullable(paths.getPathItem(endpoint.path()))
                .map(PathItem::getOperations)
                .map(operations -> operations.get(endpoint.httpMethod()))
                .map(Operation::getResponses)
                .orElseThrow(() -> unknownEndpoint(endpoint));

        replaceOkWithCreated(
                responses,
                Optional.ofNullable(responses.getAPIResponse(OK))
                        .orElseThrow(() -> missingSuccessResponse(endpoint)));
    }

    private static IllegalStateException unknownEndpoint(Endpoint endpoint) {
        return new IllegalStateException(
                "資源を作ると宣言された操作が API 定義にありません（経路の綴りが定義とずれています）: " + endpoint);
    }

    /**
     * 移す先の成功応答が無いことを失敗として返す。
     *
     * <p>
     * 移すべき 200 が無いのは、smallrye が成功応答を組まなかった（本体の型を持たない）か、既に別の状態コードへ
     * 変わっているとき。素通りさせると宣言した操作の定義だけが 201 にならず、そのことは定義を見に行くまで分からない。
     * </p>
     */
    private static IllegalStateException missingSuccessResponse(Endpoint endpoint) {
        return new IllegalStateException(
                "資源を作ると宣言された操作に、201 へ移す成功応答（200）がありません: " + endpoint);
    }

    /**
     * 成功応答を 200 から 201 へ移す。
     *
     * <p>
     * 本体の型は smallrye が 200 として組んだものをそのまま引き継ぐ。作られた資源の表現は 201 でも同じ型であり、
     * ここで組み直すと応答本体の型を二重に決めることになる。
     * </p>
     */
    /*
     * MODEL-MUTATION: OpenAPI のモデルは可変オブジェクトで、フィルタは受け取った文書を書き換えることで結果を返す （OASFilter
     * の契約）。不変更新の形にする余地がないため、ここでは setter を呼ぶ。
     */
    private static void replaceOkWithCreated(APIResponses responses, APIResponse success) {
        responses.removeAPIResponse(OK);
        responses.addAPIResponse(
                CREATED,
                success.description("作成した")
                        .addHeader(LOCATION, locationHeader()));
    }

    private static Header locationHeader() {
        return OASFactory.createHeader()
                .description("作られた資源の位置（同一オリジンの相対参照）")
                .required(true)
                .schema(
                        OASFactory.createSchema()
                                .addType(Schema.SchemaType.STRING)
                                .format("uri-reference"));
    }
}
