package com.abservice.presentation.rest.openapi;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * 応答の必須項目を API 定義へ反映する OpenAPI フィルタ
 *
 * <p>
 * 応答の record は {@code @NullMarked} のパッケージにあり、値の不在があり得る項目にだけ {@code @Nullable}
 * が付いている。この有無がそのまま必須・任意であるため、注釈を別に書かず、record の宣言から {@code required} を導く。
 * </p>
 *
 * <p>
 * これが無いと、生成される型では「必ずある項目」と「無いことがある項目」が同じ optional になり、
 * 値の不在と概念の不在を分ける設計（DECISIONS 20）を利用側が型で受け取れない。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class RequiredPropertyFilter implements OASFilter {

    /**
     * 応答の型が属するパッケージ。
     *
     * <p>
     * スキーマ名は型の単純名のため、どのパッケージの型かはここから探す。応答の型はこれらのパッケージにしか置かない。
     * </p>
     */
    private static final List<String> RESPONSE_PACKAGES = List.of(
            "com.abservice.presentation.rest.album.response",
            "com.abservice.presentation.rest.article.response",
            "com.abservice.presentation.rest.asset.response",
            "com.abservice.presentation.rest.site.response",
            "com.abservice.presentation.rest.tune.response",
            "com.abservice.presentation.rest.exception");

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Optional.ofNullable(openAPI.getComponents())
                .map(Components::getSchemas)
                .orElseGet(Map::of)
                .forEach(RequiredPropertyFilter::applyTo);
    }

    /*
     * MODEL-MUTATION: OpenAPI のモデルは可変オブジェクトで、フィルタは受け取った文書を書き換えることで結果を返す （OASFilter
     * の契約）。不変更新の形にする余地がないため、ここでは setter を呼ぶ。
     */
    private static void applyTo(String schemaName, Schema schema) {
        resolveResponseRecord(schemaName)
                .map(RequiredPropertyFilter::requiredPropertiesOf)
                .filter(Predicate.not(List::isEmpty))
                .ifPresent(schema::setRequired);
    }

    private static List<String> requiredPropertiesOf(Class<?> type) {
        return Stream.of(type.getRecordComponents())
                .filter(RequiredPropertyFilter::isRequired)
                .map(RecordComponent::getName)
                .toList();
    }

    /**
     * 必須かどうか。primitive は値を持たない状態がなく、参照型は {@code @Nullable} が付いていなければ必ず値を持つ。
     */
    private static boolean isRequired(RecordComponent component) {
        return Optional.of(component)
                .filter(Predicate.not(c -> c.getType().isPrimitive()))
                .map(c -> c.getAnnotatedType().getAnnotation(Nullable.class))
                .isEmpty();
    }

    private static Optional<Class<?>> resolveResponseRecord(String schemaName) {
        return RESPONSE_PACKAGES.stream()
                .map(responsePackage -> responsePackage + "." + schemaName)
                .map(RequiredPropertyFilter::findClass)
                .flatMap(Optional::stream)
                .filter(Class::isRecord)
                .findFirst();
    }

    /*
     * MISSING-IS-EXPECTED: スキーマ名を各パッケージへ当てて探すため、見つからないのは通常の経路である（java.time.Instant
     * のように応答の型ではないスキーマも走査対象に含まれる）。存在しないことを空で表す。
     */
    private static Optional<Class<?>> findClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException notFound) {
            return Optional.empty();
        }
    }
}
