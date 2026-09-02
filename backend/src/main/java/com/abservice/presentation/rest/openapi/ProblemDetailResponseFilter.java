package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.exception.ProblemDetail;
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
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.jspecify.annotations.Nullable;

/**
 * エラー応答（RFC 9457 Problem Details）を API 定義へ反映する OpenAPI フィルタ
 *
 * <p>
 * エラー契約の出所は {@code DomainExceptionMapper} であり、個々のエンドポイントではない。エンドポイントごとに
 * {@code @APIResponse} を書くと同じ契約が API の数だけ複製されるため、マッパーが宣言する状態コード
 * （{@link ProblemDetailErrorContract}）を読んで定義側へ一括で反映する。
 * </p>
 *
 * <p>
 * 反映は2つ。既に定義されている状態コードには応答本体の型と説明を与え、パスで対象を指す（パスパラメータを持つ） オペレーションには未存在の 404
 * を足す。一覧のようにパスで対象を指さないオペレーションへ 404 は足さない。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class ProblemDetailResponseFilter implements OASFilter {

    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/ProblemDetail";
    private static final String NOT_FOUND = "404";

    /** 状態コードごとの説明。どのコードを返すかはマッパーの宣言が持ち、定義上の文言はここが持つ。 */
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "400",
            "入力の検証に失敗した",
            NOT_FOUND,
            "対象が存在しない",
            "409",
            "業務ルールに反する");

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Optional.ofNullable(openAPI.getPaths())
                .map(Paths::getPathItems)
                .map(Map::values)
                .orElseGet(List::of)
                .forEach(ProblemDetailResponseFilter::applyToPathItem);
    }

    private static void applyToPathItem(PathItem pathItem) {
        Optional.ofNullable(pathItem.getOperations())
                .map(Map::values)
                .orElseGet(List::of)
                .forEach(operation -> applyToOperation(operation, pathItem));
    }

    private static void applyToOperation(Operation operation, PathItem pathItem) {
        final APIResponses responses = Optional.ofNullable(operation.getResponses())
                .orElseGet(OASFactory::createAPIResponses);

        ProblemDetailErrorContract.declaredStatusCodes()
                .forEach(code -> describeExisting(responses, code));

        codesToAdd(pathItem, operation)
                .forEach(code -> addProblemResponse(responses, code));

        operation.setResponses(responses);
    }

    /**
     * 定義に無くても足す状態コード。パスで対象を指すオペレーションだけが未存在を返し得る。
     */
    private static List<String> codesToAdd(PathItem pathItem, Operation operation) {
        return identifiesTargetByPath(pathItem, operation)
                ? List.of(NOT_FOUND)
                : List.of();
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
        response.description(DESCRIPTIONS.get(code))
                .content(problemContent());
    }

    private static APIResponse problemResponse(String code) {
        return OASFactory.createAPIResponse()
                .description(DESCRIPTIONS.get(code))
                .content(problemContent());
    }

    private static Content problemContent() {
        return OASFactory.createContent()
                .addMediaType(
                        ProblemDetail.MEDIA_TYPE,
                        OASFactory.createMediaType()
                                .schema(OASFactory.createSchema().ref(PROBLEM_SCHEMA_REF)));
    }

    private static boolean identifiesTargetByPath(PathItem pathItem, Operation operation) {
        return Stream.concat(
                parametersOf(pathItem.getParameters()),
                parametersOf(operation.getParameters()))
                .anyMatch(parameter -> Parameter.In.PATH.equals(parameter.getIn()));
    }

    private static Stream<Parameter> parametersOf(@Nullable List<Parameter> parameters) {
        return Optional.ofNullable(parameters)
                .orElseGet(List::of)
                .stream();
    }
}
