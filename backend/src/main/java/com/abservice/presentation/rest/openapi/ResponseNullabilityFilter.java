package com.abservice.presentation.rest.openapi;

import com.abservice.lib.Optionals;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * 応答の項目が「常にあるか」「null を取り得るか」を API 定義へ反映する OpenAPI フィルタ
 *
 * <p>
 * 応答の record はそのままシリアライズされるため、**項目名は常に出る**。値が無いことは null で表し、その項目を
 * そもそも持たない種別はレスポンス型自体を分ける（DECISIONS 20）。したがって定義では、全項目を {@code required}
 * にし、{@code @Nullable} が付く項目だけを null 許容にする。
 * </p>
 *
 * <p>
 * 応答の record は {@code @NullMarked} のパッケージにあり、null を取り得る項目にだけ {@code @Nullable}
 * が付いている。この有無がそのまま null 許容であるため、注釈を別に書かず record の宣言から導く。
 * </p>
 *
 * <p>
 * 例外は Jackson の出力制御を持つ型（{@code @JsonInclude}）で、そこでは項目名が省略され得る。実際にキーが 出ない項目を
 * {@code required} にすると契約が実応答とずれるため、対象から外す。
 * </p>
 *
 * <p>
 * NESTED-RECORDS: 応答の record は入れ子を持つ（一覧の要素など）。入れ子は {@code 親$子} という名前で読み込まれる
 * ため、スキーマ名（単純名）をパッケージへ繋いだ綴りでは解決できない。パッケージ直下の record から入れ子を辿って
 * 索引を作り、スキーマ名で引く。辿らないと入れ子だけが素通りし、常にある項目が省略可能として定義される。
 * </p>
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class ResponseNullabilityFilter implements OASFilter {

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
            "com.abservice.presentation.rest.tune.response");

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        final Map<String, Schema> schemas = Optional.ofNullable(openAPI.getComponents())
                .map(Components::getSchemas)
                .orElseGet(Map::of);

        applyToAll(schemas, responseRecordsBySchemaName(schemas.keySet()));
    }

    private static void applyToAll(Map<String, Schema> schemas, Map<String, Class<?>> responseRecords) {
        schemas.forEach(
                (schemaName, schema) -> Optional.ofNullable(responseRecords.get(schemaName))
                        .filter(ResponseNullabilityFilter::keepsEveryPropertyInOutput)
                        .ifPresent(type -> applyToRecord(schema, type)));
    }

    /*
     * MODEL-MUTATION: OpenAPI のモデルは可変オブジェクトで、フィルタは受け取った文書を書き換えることで結果を返す （OASFilter
     * の契約）。不変更新の形にする余地がないため、ここでは setter を呼ぶ。
     */
    private static void applyToRecord(Schema schema, Class<?> type) {
        final List<RecordComponent> components = List.of(type.getRecordComponents());

        components.stream()
                .map(RecordComponent::getName)
                .collect(Optionals.optionally(Collectors.toUnmodifiableList()))
                .ifPresent(schema::setRequired);

        components.stream()
                .filter(ResponseNullabilityFilter::isNullable)
                .map(RecordComponent::getName)
                .forEach(name -> allowNull(schema, name));
    }

    private static boolean isNullable(RecordComponent component) {
        return Objects.nonNull(component.getAnnotatedType().getAnnotation(Nullable.class));
    }

    /** Jackson の出力制御を持たない型は、値の有無によらず項目名を出す。 */
    private static boolean keepsEveryPropertyInOutput(Class<?> type) {
        return Objects.isNull(type.getAnnotation(JsonInclude.class));
    }

    private static void allowNull(Schema schema, String propertyName) {
        Optional.ofNullable(schema.getProperties())
                .map(properties -> properties.get(propertyName))
                .ifPresent(ResponseNullabilityFilter::addNullToType);
    }

    /**
     * null を取り得ることを型に加える。
     *
     * <p>
     * 他のスキーマを参照する項目（{@code $ref}）は参照だけで型を持たないため、参照と null の選択として組み直す。
     * </p>
     */
    private static void addNullToType(Schema propertySchema) {
        Optional.ofNullable(propertySchema.getRef())
                .ifPresentOrElse(
                        ref -> makeReferenceNullable(propertySchema, ref),
                        () -> propertySchema.addType(Schema.SchemaType.NULL));
    }

    private static void makeReferenceNullable(Schema propertySchema, String ref) {
        propertySchema.setRef(null);
        propertySchema.setAnyOf(
                List.of(
                        OASFactory.createSchema().ref(ref),
                        OASFactory.createSchema().addType(Schema.SchemaType.NULL)));
    }

    /**
     * スキーマ名から応答の record を引く索引を作る。
     *
     * <p>
     * パッケージ直下の record はスキーマ名から直接解決できる。入れ子はそこから辿るしかないため、直下のものを起点に
     * 集める。単純名が衝突すると、smallrye が付ける連番（{@code Foo2}）と索引の対応が崩れ、別の型の項目を当てて
     * しまうため、衝突は組み立ての時点で落とす（宣言側の検査は {@code LayeredArchitectureTest}）。
     * </p>
     */
    private static Map<String, Class<?>> responseRecordsBySchemaName(Set<String> schemaNames) {
        final List<Class<?>> declared = schemaNames.stream()
                .map(ResponseNullabilityFilter::resolveResponseRecord)
                .flatMap(Optional::stream)
                .toList();

        return Stream.concat(declared.stream(), declared.stream().flatMap(ResponseNullabilityFilter::nestedRecordsOf))
                .collect(
                        Collectors.toUnmodifiableMap(
                                Class::getSimpleName,
                                Function.identity(),
                                ResponseNullabilityFilter::rejectDuplicate));
    }

    private static Stream<Class<?>> nestedRecordsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredClasses())
                .filter(Class::isRecord)
                .flatMap(nested -> Stream.concat(Stream.of(nested), nestedRecordsOf(nested)));
    }

    private static Class<?> rejectDuplicate(Class<?> first, Class<?> second) {
        throw new IllegalStateException(
                "応答の record の単純名が衝突しています。スキーマ名で一意に引けないため改名してください: "
                        + first.getName() + " / " + second.getName());
    }

    private static Optional<Class<?>> resolveResponseRecord(String schemaName) {
        return RESPONSE_PACKAGES.stream()
                .map(responsePackage -> responsePackage + "." + schemaName)
                .map(ResponseNullabilityFilter::findClass)
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
