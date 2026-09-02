package com.abservice.presentation.rest.openapi;

import com.abservice.presentation.rest.exception.DomainExceptionMapper;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

/**
 * エラー応答の契約を {@link DomainExceptionMapper} の宣言から読み取る
 *
 * <p>
 * どの状態コードで返すかを決めているのは例外マッパーであり、エンドポイントではない。その宣言をここで読むことで、API
 * 定義のエラー応答をエンドポイントごとに書かずに済ませる。
 * </p>
 */
final class ProblemDetailErrorContract {

    private ProblemDetailErrorContract() {
    }

    /**
     * 例外マッパーが返し得る状態コードを、宣言順で返します。
     *
     * @return 状態コードの並び
     */
    static List<String> declaredStatusCodes() {
        return Stream.of(DomainExceptionMapper.class.getAnnotation(APIResponses.class).value())
                .map(APIResponse::responseCode)
                .toList();
    }
}
