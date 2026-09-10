package com.abservice.presentation.rest.openapi;

import com.abservice.application.exception.Failure;
import com.abservice.presentation.rest.exception.ProblemDetail;
import com.abservice.presentation.rest.openapi.DeclaredEndpoints.Endpoint;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * エラー応答（RFC 9457 Problem Details）を API 定義へ反映する OpenAPI フィルタ
 *
 * <p>
 * エラー契約の出所は2つに分かれる。**どの状態コードで返すか**は例外マッパーが決め（{@link ProblemDetailErrorContract}）、
 * **どの失敗を返し得るか**はユースケースが宣言する（{@code FailureContract}）。エンドポイントごとに
 * {@code @APIResponse} を書くと、同じ契約が API の数だけ複製されるうえ、実装を変えたときに追い漏らしても定義は 生成できてしまう。
 * </p>
 *
 * <p>
 * 反映は2つ。既に定義されている状態コードには応答本体の型と説明を与える（認証・認可の 401/403 は Quarkus が
 * {@code @RolesAllowed} から状態コードだけを付けるため、本体の型はここで与える）。定義に無いものは足す——想定外の 失敗の 500
 * は全オペレーションへ、それ以外はそのエンドポイントが実行するユースケースの宣言（{@link Executes} で
 * 辿る）から決める。経路の形（本体・問合せ文字列・パスパラメータの有無）から失敗を推定しない。推定は実装と食い違う
 * （経路の識別子を検証する操作は本体が無くても 400 を返し、対象の不在を成功とする削除は 404 を返さない）。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class ProblemDetailResponseFilter implements OASFilter {

    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/ProblemDetail";
    private static final String BAD_REQUEST = "400";
    private static final String NOT_FOUND = "404";
    private static final String CONFLICT = "409";
    private static final String INTERNAL_ERROR = "500";

    /**
     * 失敗と状態コードの対応。
     *
     * <p>
     * ユースケースの語彙（{@link Failure}）を HTTP へ写す唯一の場所。写し方は例外マッパーの実装と一致していなければ ならず、その対応は
     * {@code OpenApiSchemaRestIntegrationTest} と各 REST 統合テストが実応答で固定する。
     * </p>
     */
    private static final Map<Failure, String> STATUS_CODES = Map.of(
            Failure.VALIDATION,
            BAD_REQUEST,
            Failure.NOT_FOUND,
            NOT_FOUND,
            Failure.CONFLICT,
            CONFLICT);

    /** 状態コードごとの説明。どのコードを返すかは宣言が持ち、定義上の文言はここが持つ。 */
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            BAD_REQUEST,
            "入力の検証に失敗した",
            "401",
            "認証されていない",
            "403",
            "権限が足りない",
            NOT_FOUND,
            "対象が存在しない",
            CONFLICT,
            "業務ルールに反する、または他の操作と競合した",
            INTERNAL_ERROR,
            "想定外の失敗");

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        final Map<Endpoint, Class<?>> useCases = DeclaredEndpoints.executedUseCases();

        Optional.ofNullable(openAPI.getPaths())
                .map(Paths::getPathItems)
                .orElseGet(Map::of)
                .forEach(
                        (path, pathItem) -> applyToPathItem(
                                path,
                                pathItem,
                                useCases));
    }

    private static void applyToPathItem(
            String path,
            PathItem pathItem,
            Map<Endpoint, Class<?>> useCases) {
        Optional.ofNullable(pathItem.getOperations())
                .orElseGet(Map::of)
                .forEach(
                        (httpMethod, operation) -> applyToOperation(
                                operation,
                                DeclaredEndpoints.failuresOf(useCases, new Endpoint(httpMethod, path))));
    }

    private static void applyToOperation(Operation operation, List<Failure> failures) {
        final APIResponses responses = Optional.ofNullable(operation.getResponses())
                .orElseGet(OASFactory::createAPIResponses);

        ProblemDetailErrorContract.declaredStatusCodes()
                .forEach(code -> describeExisting(responses, code));

        codesToAdd(failures)
                .forEach(code -> addProblemResponse(responses, code));

        operation.setResponses(responses);
    }

    /**
     * 定義に無くても足す状態コード。
     *
     * <p>
     * 想定外の失敗はどのオペレーションでも起こり得るため常に足す。それ以外は宣言された失敗を写す。認証・認可の 401/403 は Quarkus が
     * {@code @RolesAllowed} から付けるため足す側では扱わず、本体の型と説明を与える側で 拾う（認証を要さないオペレーションには現れない）。
     * </p>
     */
    private static List<String> codesToAdd(List<Failure> failures) {
        return Stream.concat(
                Stream.of(INTERNAL_ERROR),
                failures.stream().map(STATUS_CODES::get))
                .toList();
    }

    /*
     * MODEL-MUTATION: OpenAPI のモデルは可変オブジェクトで、フィルタは受け取った文書を書き換えることで結果を返す （OASFilter
     * の契約）。不変更新の形にする余地がないため、ここでは setter を呼ぶ。
     */
    private static void describeExisting(APIResponses responses, String code) {
        Optional.ofNullable(responses.getAPIResponse(code))
                .ifPresent(existing -> describe(existing, code));
    }

    private static void addProblemResponse(APIResponses responses, String code) {
        Optional.ofNullable(responses.getAPIResponse(code))
                .ifPresentOrElse(
                        existing -> describe(existing, code),
                        () -> responses.addAPIResponse(code, problemResponse(code)));
    }

    private static void describe(APIResponse response, String code) {
        response.description(descriptionOf(code))
                .content(problemContent());
    }

    private static APIResponse problemResponse(String code) {
        return OASFactory.createAPIResponse()
                .description(descriptionOf(code))
                .content(problemContent());
    }

    /**
     * 状態コードの説明を引く。
     *
     * <p>
     * 宣言された失敗に対応する説明が無いまま進めると、定義の説明を空へ上書きしてしまう。対応が欠けたことは黙って 通さず、組み立てを落とす。
     * </p>
     */
    private static String descriptionOf(String code) {
        return Optional.ofNullable(DESCRIPTIONS.get(code))
                .orElseThrow(
                        () -> new IllegalStateException(
                                "定義へ載せる状態コードに説明がありません: " + code));
    }

    private static Content problemContent() {
        return OASFactory.createContent()
                .addMediaType(
                        ProblemDetail.MEDIA_TYPE,
                        OASFactory.createMediaType()
                                .schema(OASFactory.createSchema().ref(PROBLEM_SCHEMA_REF)));
    }
}
