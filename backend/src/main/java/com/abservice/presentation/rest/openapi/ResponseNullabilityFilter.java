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
import java.util.function.Predicate;
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
 * 例外は Jackson の出力制御（{@code @JsonInclude}）を持つ型と項目で、そこでは項目名が省略され得る。実際にキーが 出ない項目を
 * {@code required} にすると契約が実応答とずれるため、対象から外す。型に付いていれば型全体を、項目に付いていれば その項目だけを外す。
 * 項目単位で外すのは、外部の厳密な schema が null を拒み省略だけを許す項目（試聴端末の Manifest の artwork 等）のためで、
 * 型全体を外すと常にある項目まで省略可能として定義される。
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
            "com.abservice.presentation.rest.audio.response",
            "com.abservice.presentation.rest.publication.response",
            "com.abservice.presentation.rest.security.response",
            "com.abservice.presentation.rest.site.response",
            "com.abservice.presentation.rest.tune.response");

    /** null の値で項目名を出さない出力制御。 */
    private static final Set<JsonInclude.Include> OMITTING_NULL = Set.of(
            JsonInclude.Include.NON_NULL,
            JsonInclude.Include.NON_ABSENT,
            JsonInclude.Include.NON_EMPTY,
            JsonInclude.Include.NON_DEFAULT);

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
                .filter(ResponseNullabilityFilter::alwaysInOutput)
                .map(RecordComponent::getName)
                .collect(Optionals.optionally(Collectors.toUnmodifiableList()))
                .ifPresent(schema::setRequired);

        components.stream()
                .filter(ResponseNullabilityFilter::isNullable)
                .filter(Predicate.not(ResponseNullabilityFilter::omitsNull))
                .map(RecordComponent::getName)
                .forEach(name -> allowNull(schema, name));
    }

    /**
     * null のとき項目名ごと省く出力制御か。Java 側では null を取り得ても、応答に null は現れないため null 型を付けない。
     * 外部の厳密な schema が null を拒む項目（試聴端末の Manifest の artwork 等）と定義を一致させる。
     */
    private static boolean omitsNull(RecordComponent component) {
        return Optional.ofNullable(component.getAccessor().getAnnotation(JsonInclude.class))
                .map(JsonInclude::value)
                .filter(OMITTING_NULL::contains)
                .isPresent();
    }

    private static boolean isNullable(RecordComponent component) {
        return Objects.nonNull(component.getAnnotatedType().getAnnotation(Nullable.class));
    }

    /**
     * 項目単位の出力制御を持たない項目は、値の有無によらず項目名を出す。
     *
     * <p>
     * ACCESSOR-ANNOTATION: {@code @JsonInclude} は record component
     * を対象に宣言していないため、record の項目へ付けた 注釈は component からは見えず、accessor と field
     * へ伝わる。Jackson が読むのと同じ accessor で判定する。
     * </p>
     */
    public static boolean alwaysInOutput(RecordComponent component) {
        return Objects.isNull(component.getAccessor().getAnnotation(JsonInclude.class));
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
