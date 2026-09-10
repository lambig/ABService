package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.album.AlbumCommandResource;
import com.abservice.presentation.rest.album.AlbumExternalAudioCommandResource;
import com.abservice.presentation.rest.album.AlbumTrackCommandResource;
import com.abservice.presentation.rest.article.ArticleCommandResource;
import com.abservice.presentation.rest.article.ArticleTagCommandResource;
import com.abservice.presentation.rest.tune.TuneCommandResource;
import io.github.lambig.textescape.TextEscape;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
 * どの操作が資源を作るかは {@link CreatesResource} が宣言する。その宣言が付くメソッドから経路と HTTP メソッドを
 * 組み立て、対応する操作の成功応答を 200 から 201 へ移し、{@code Location} を足す。状態コードを実装と定義の双方へ
 * 書き写さないための機構であり、エンドポイントごとの {@code @APIResponse} は置かない。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class CreatedResourceResponseFilter implements OASFilter {

    /**
     * 資源を作る操作を持つリソース。
     *
     * <p>
     * ビルド時フィルタはクラスパスを走査できないため、走査対象をここで数え上げる。数え上げから漏れたリソースは 200 のまま
     * 定義され、そのことは定義を見に行くまで分からない。漏れは {@code LayeredArchitectureTest} が
     * {@link CreatesResource} の付いたメソッドを持つクラスと突き合わせて落とす。
     * </p>
     */
    public static final List<Class<?>> RESOURCE_CLASSES = List.of(
            AlbumCommandResource.class,
            AlbumExternalAudioCommandResource.class,
            AlbumTrackCommandResource.class,
            ArticleCommandResource.class,
            ArticleTagCommandResource.class,
            TuneCommandResource.class);

    private static final String OK = "200";
    private static final String CREATED = "201";
    private static final String LOCATION = "Location";

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        final Paths paths = Optional.ofNullable(openAPI.getPaths())
                .orElseGet(OASFactory::createPaths);

        creatingEndpoints().forEach(endpoint -> applyTo(paths, endpoint));
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
        moveSuccessToCreated(
                Optional.ofNullable(paths.getPathItem(endpoint.path()))
                        .map(PathItem::getOperations)
                        .map(operations -> operations.get(endpoint.httpMethod()))
                        .map(Operation::getResponses)
                        .orElseThrow(() -> unknownEndpoint(endpoint)));
    }

    private static IllegalStateException unknownEndpoint(Endpoint endpoint) {
        return new IllegalStateException(
                "資源を作ると宣言された操作が API 定義にありません（経路の綴りが定義とずれています）: " + endpoint);
    }

    /**
     * 成功応答を 200 から 201 へ移す。
     *
     * <p>
     * 本体の型は smallrye が 200 として組んだものをそのまま引き継ぐ。作られた資源の表現は 201 でも同じ型であり、ここで
     * 組み直すと応答本体の型を二重に決めることになる。
     * </p>
     */
    private static void moveSuccessToCreated(APIResponses responses) {
        Optional.ofNullable(responses.getAPIResponse(OK))
                .ifPresent(success -> replaceOkWithCreated(responses, success));
    }

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

    private static Set<Endpoint> creatingEndpoints() {
        return RESOURCE_CLASSES.stream()
                .flatMap(CreatedResourceResponseFilter::endpointsOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<Endpoint> endpointsOf(Class<?> resourceClass) {
        final String basePath = resourceClass.getAnnotation(Path.class).value();

        return Arrays.stream(resourceClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(CreatesResource.class))
                .map(method -> new Endpoint(httpMethodOf(method), pathOf(basePath, method)));
    }

    /**
     * メソッドが受け付ける HTTP メソッドを、JAX-RS の宣言から読む。
     *
     * <p>
     * {@code @POST} などは {@code @HttpMethod} を持つ注釈であり、その値が HTTP メソッド名である。作る操作を POST
     * に限らないため、注釈の種類ではなくこのメタ注釈から導く。
     * </p>
     */
    private static PathItem.HttpMethod httpMethodOf(Method method) {
        return Arrays.stream(method.getAnnotations())
                .map(annotation -> annotation.annotationType().getAnnotation(HttpMethod.class))
                .filter(Objects::nonNull)
                .map(HttpMethod::value)
                .map(PathItem.HttpMethod::valueOf)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "資源を作る操作に HTTP メソッドの宣言がありません: " + method));
    }

    private static String pathOf(String basePath, Method method) {
        return TextEscape.escape("${base}${sub}")
                .where("base", basePath)
                .where(
                        "sub",
                        Optional.ofNullable(method.getAnnotation(Path.class))
                                .map(Path::value)
                                .orElse(""))
                .compile();
    }

    /**
     * API 定義の中で操作を一つに定める組。
     *
     * @param httpMethod
     *            HTTP メソッド
     * @param path
     *            経路
     */
    private record Endpoint(PathItem.HttpMethod httpMethod, String path) {
    }
}
