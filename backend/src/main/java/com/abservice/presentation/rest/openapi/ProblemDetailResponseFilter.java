package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.exception.ProblemDetail;
import com.abservice.presentation.rest.openapi.DeclaredEndpoints.Endpoint;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.jspecify.annotations.Nullable;

/**
 * エラー応答（RFC 9457 Problem Details）を API 定義へ反映する OpenAPI フィルタ
 *
 * <p>
 * エラー契約の出所は例外マッパーであり、個々のエンドポイントではない。エンドポイントごとに {@code @APIResponse} を書くと同じ契約が
 * API の数だけ複製されるため、マッパーが宣言する状態コード
 * （{@link ProblemDetailErrorContract}）を読んで定義側へ一括で反映する。
 * </p>
 *
 * <p>
 * 反映は2つ。既に定義されている状態コードには応答本体の型と説明を与える（認証・認可の 401/403 は Quarkus が
 * {@code @RolesAllowed} から状態コードだけを付けるため、本体の型はここで与える）。定義に無いものは足す——想定外の 失敗の 500
 * は全オペレーションへ、未存在の 404 はパスで対象を指すオペレーションへ、入力の検証失敗の 400 は本体か
 * 問合せ文字列を受け取るオペレーションへ、業務ルール違反と競合の 409 は {@link MayConflict} を宣言した操作へ。
 * 何をどこへ足すかの根拠は {@link #codesToAdd} が持つ。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class ProblemDetailResponseFilter implements OASFilter {

    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/ProblemDetail";
    private static final String BAD_REQUEST = "400";
    private static final String NOT_FOUND = "404";
    private static final String CONFLICT = "409";
    private static final String INTERNAL_ERROR = "500";

    /** 状態コードごとの説明。どのコードを返すかはマッパーの宣言が持ち、定義上の文言はここが持つ。 */
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
        final Set<Endpoint> conflicting = DeclaredEndpoints.declaring(MayConflict.class);

        Optional.ofNullable(openAPI.getPaths())
                .map(Paths::getPathItems)
                .orElseGet(Map::of)
                .forEach(
                        (path, pathItem) -> applyToPathItem(
                                path,
                                pathItem,
                                conflicting));
    }

    private static void applyToPathItem(
            String path,
            PathItem pathItem,
            Set<Endpoint> conflicting) {
        Optional.ofNullable(pathItem.getOperations())
                .orElseGet(Map::of)
                .forEach(
                        (httpMethod, operation) -> applyToOperation(
                                operation,
                                pathItem,
                                conflicting.contains(new Endpoint(httpMethod, path))));
    }

    private static void applyToOperation(
            Operation operation,
            PathItem pathItem,
            boolean mayConflict) {
        final APIResponses responses = Optional.ofNullable(operation.getResponses())
                .orElseGet(OASFactory::createAPIResponses);

        ProblemDetailErrorContract.declaredStatusCodes()
                .forEach(code -> describeExisting(responses, code));

        codesToAdd(
                operation,
                pathItem,
                mayConflict)
                .forEach(code -> addProblemResponse(responses, code));

        operation.setResponses(responses);
    }

    /**
     * 定義に無くても足す状態コード。
     *
     * <p>
     * 想定外の失敗はどのオペレーションでも起こり得るため常に足す。未存在はパスで対象を指すオペレーションだけが返し得る （形式が不正なIDも未存在として 404
     * になる）。入力の検証失敗は本体か問合せ文字列を受け取るオペレーションが 返し得る（並び順のキーや向きが閉じた選択肢の外なら
     * 400）。パスで対象を指すだけのオペレーションは 400 を返さない。
     * </p>
     *
     * <p>
     * 業務ルール違反と競合の 409 は、返し得ると宣言した操作（{@link MayConflict}）だけが返す。HTTP メソッドから
     * 導くと、資源を書き換えない操作にまで宣言が広がる。
     * </p>
     *
     * <p>
     * 認証・認可の 401/403 は Quarkus が {@code @RolesAllowed} から付けるため足す側では扱わず、本体の型と
     * 説明を与える側で拾う（認証を要さないオペレーションには現れない）。
     * </p>
     */
    private static List<String> codesToAdd(
            Operation operation,
            PathItem pathItem,
            boolean mayConflict) {
        return Stream.of(
                Stream.of(INTERNAL_ERROR),
                identifiesTargetByPath(pathItem, operation)
                        ? Stream.of(NOT_FOUND)
                        : Stream.<String>empty(),
                acceptsInput(pathItem, operation)
                        ? Stream.of(BAD_REQUEST)
                        : Stream.<String>empty(),
                mayConflict
                        ? Stream.of(CONFLICT)
                        : Stream.<String>empty())
                .flatMap(codes -> codes)
                .toList();
    }

    /**
     * 入力を受け取るかどうか。
     *
     * <p>
     * 受け取るのは要求本体と問合せ文字列で、どちらも値が閉じた選択肢や検証規則の外にあれば 400 になる。パスパラメータは
     * 対象の同定に使われ、形式が不正でも未存在として扱われるため入力に数えない。
     * </p>
     */
    private static boolean acceptsInput(PathItem pathItem, Operation operation) {
        return Stream.of(
                Objects.nonNull(operation.getRequestBody()),
                hasQueryParameter(pathItem, operation))
                .anyMatch(Boolean::booleanValue);
    }

    private static boolean hasQueryParameter(PathItem pathItem, Operation operation) {
        return allParametersOf(pathItem, operation)
                .anyMatch(parameter -> Parameter.In.QUERY.equals(parameter.getIn()));
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
     * マッパーが宣言した状態コードに説明が無いまま進めると、定義の説明を空へ上書きしてしまう。宣言と説明の対応が 欠けたことは黙って通さず、組み立てを落とす。
     * </p>
     */
    private static String descriptionOf(String code) {
        return Optional.ofNullable(DESCRIPTIONS.get(code))
                .orElseThrow(
                        () -> new IllegalStateException(
                                "例外マッパーが宣言する状態コードに定義上の説明がありません: " + code));
    }

    private static Content problemContent() {
        return OASFactory.createContent()
                .addMediaType(
                        ProblemDetail.MEDIA_TYPE,
                        OASFactory.createMediaType()
                                .schema(OASFactory.createSchema().ref(PROBLEM_SCHEMA_REF)));
    }

    private static boolean identifiesTargetByPath(PathItem pathItem, Operation operation) {
        return allParametersOf(pathItem, operation)
                .anyMatch(parameter -> Parameter.In.PATH.equals(parameter.getIn()));
    }

    /** パラメータはパスアイテム側とオペレーション側の両方に置ける（前者はそのパスの全メソッドで共通）。 */
    private static Stream<Parameter> allParametersOf(PathItem pathItem, Operation operation) {
        return Stream.concat(
                parametersOf(pathItem.getParameters()),
                parametersOf(operation.getParameters()));
    }

    private static Stream<Parameter> parametersOf(@Nullable List<Parameter> parameters) {
        return Optional.ofNullable(parameters)
                .orElseGet(List::of)
                .stream();
    }
}
