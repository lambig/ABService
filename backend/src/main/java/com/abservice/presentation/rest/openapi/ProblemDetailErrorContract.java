package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.exception.AuthenticationFailedExceptionMapper;
import com.abservice.presentation.rest.exception.ConflictingEditExceptionMapper;
import com.abservice.presentation.rest.exception.ConflictingUpdateExceptionMapper;
import com.abservice.presentation.rest.exception.DomainExceptionMapper;
import com.abservice.presentation.rest.exception.ForbiddenExceptionMapper;
import com.abservice.presentation.rest.exception.StaleStateExceptionMapper;
import com.abservice.presentation.rest.exception.UnauthorizedExceptionMapper;
import com.abservice.presentation.rest.exception.UncaughtExceptionMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

/**
 * エラー応答の契約を、例外マッパーの宣言から読み取る
 *
 * <p>
 * どの状態コードで返すかを決めているのは例外マッパーであり、エンドポイントではない。各マッパーが自分の返す状態コードを
 * 宣言し、ここがそれを集めることで、API 定義のエラー応答をエンドポイントごとに書かずに済ませる。
 * </p>
 *
 * <p>
 * 応答本体はどのエラーでも {@code ProblemDetail} 一つ（RFC 9457）であり、マッパーごとに違わない。したがって
 * ここが読むのは状態コードだけで、本体の型と説明は {@link ProblemDetailResponseFilter} が与える。本体の スキーマ自体は
 * {@code DomainExceptionMapper} の宣言が登録する（型を持つ宣言が1つも無いと、フィルタが書く 参照の先が生成されない）。
 * </p>
 */
public final class ProblemDetailErrorContract {

    /**
     * エラー応答を返す例外マッパー。
     *
     * <p>
     * 数え上げから漏れたマッパーの状態コードは、本体の型を持たないまま（Quarkus が付ける説明だけで）定義に残るか、
     * そもそも定義に現れない。どちらも要求元は本体を型として読めない。漏れは {@code LayeredArchitectureTest} が
     * {@code ExceptionMapper} の実装と突き合わせて落とす。
     * </p>
     */
    public static final List<Class<?>> MAPPERS = List.of(
            AuthenticationFailedExceptionMapper.class,
            ConflictingEditExceptionMapper.class,
            ConflictingUpdateExceptionMapper.class,
            DomainExceptionMapper.class,
            ForbiddenExceptionMapper.class,
            StaleStateExceptionMapper.class,
            UnauthorizedExceptionMapper.class,
            UncaughtExceptionMapper.class);

    private ProblemDetailErrorContract() {
    }

    /**
     * 例外マッパーが返し得る状態コードを、重複なく昇順で返します。
     *
     * <p>
     * 同じ状態コードを複数のマッパーが返す（409 は競合の検出位置ごとに3つ、401 は未提示と不一致で2つ）。定義側で
     * 区別する意味はないため、重ねずに1つとして扱う。
     * </p>
     *
     * @return 状態コードの並び
     */
    static List<String> declaredStatusCodes() {
        return MAPPERS.stream()
                .flatMap(ProblemDetailErrorContract::declaredBy)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * マッパーが宣言する応答を読む。
     *
     * <p>
     * {@code @APIResponse} は繰り返し可能な注釈で、2つ以上付くとコンテナ（{@code @APIResponses}）へ
     * まとめられ、単体の注釈としては読めなくなる。返す状態コードが1つのマッパーと複数のマッパーが混在するため、 両方の形を読む。
     * </p>
     */
    private static Stream<String> declaredBy(Class<?> mapper) {
        return Stream.concat(groupedOn(mapper), singleOn(mapper))
                .map(APIResponse::responseCode);
    }

    private static Stream<APIResponse> groupedOn(Class<?> mapper) {
        return Optional.ofNullable(mapper.getAnnotation(APIResponses.class))
                .map(APIResponses::value)
                .stream()
                .flatMap(Arrays::stream);
    }

    private static Stream<APIResponse> singleOn(Class<?> mapper) {
        return Optional.ofNullable(mapper.getAnnotation(APIResponse.class)).stream();
    }
}
