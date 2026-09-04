package com.abservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static java.util.function.Predicate.not;

import com.abservice.domain.repository.album.AlbumRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * アーキテクチャ制約テスト（フェーズA: 基本ルール）
 *
 * <p>
 * ABService のアーキテクチャ制約を ArchUnit で強制する。
 * ここでは既存構造だけで検証できる基本ルール（レイヤー依存方向・レイヤー配置・ライブラリ依存・戻り値契約）を扱う。 app/presentation
 * の構造に依存する命名・戻り値・テスト規約ルールは、それらのレイヤー実装後（フェーズD）に追加する。
 * </p>
 *
 * <p>
 * ルールは {@code @ArchTest} を付与したメソッド形式で定義する。 {@code static final ArchRule}
 * フィールド形式は checkstyle の ConstantName / HideUtilityClassConstructor
 * と衝突するため採用しない。
 * </p>
 *
 * <p>
 * 対応ドキュメント: {@code docs/CODING_GUIDELINES.md} §1（静的解析ガバナンス）。
 * </p>
 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String APPLICATION_QUERY = "..application.query..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String PRESENTATION = "..presentation..";
    private static final String READ_DATASOURCE = "..infrastructure.persistence.datasource..";
    private static final String READ_ENTITY = "..infrastructure.persistence.entity..";
    private static final String REST_RESPONSE = "..presentation.rest..response";
    private static final List<String> ALBUM_LOOKUP_METHODS = List.of(
            "findById",
            "findByIdExclusively",
            "findAllById",
            "findAll");

    /**
     * レイヤー依存方向: ドメイン層は他のどのレイヤーにも依存してはならない。
     */
    @ArchTest
    void domainShouldNotDependOnOuterLayers(JavaClasses classes) {
        noClasses().that().resideInAPackage(DOMAIN).should().dependOnClassesThat()
                .resideInAnyPackage(
                        APPLICATION,
                        INFRASTRUCTURE,
                        PRESENTATION)
                .as("ドメイン層は application / infrastructure / presentation に依存してはならない").check(classes);
    }

    /**
     * レイヤー依存方向（Command 側）: アプリケーション層の Command 側（{@code application.query} を除く）は
     * infrastructure / presentation に依存してはならない（domain・Repository 経由を強制）。
     *
     * <p>
     * CQRS の非対称性を反映する。Command は Repository（Write Model）経由でドメインを通す。Query 側
     * （{@code application.query}）は読み取り専用の別スタックのため本ルールの対象外とし、
     * {@link #applicationQueryMayDependOnlyOnReadModelInfra} で別途規定する。
     * </p>
     */
    @ArchTest
    void applicationCommandShouldNotDependOnInfraOrPresentation(JavaClasses classes) {
        noClasses().that().resideInAPackage(APPLICATION).and().resideOutsideOfPackage(APPLICATION_QUERY).should()
                .dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, PRESENTATION)
                .as("アプリケーション層の Command 側（application.query を除く）は infrastructure / presentation に依存してはならない")
                .check(classes);
    }

    /**
     * レイヤー依存方向（Query 側）: {@code application.query} は読み取り側として
     * {@code infrastructure.persistence.datasource} と
     * {@code infrastructure.persistence.entity} にのみ依存してよい。書き込み側 Repository・その他
     * infrastructure・presentation への依存は禁止。
     *
     * <p>
     * CQRS の Read/Write 分離: Query は DataSource 直アクセスで Read Model DTO
     * を返す（{@code docs/ARCHITECTURE.md} レイヤと依存方向）。この読み取り経路のみを許可するため、infrastructure
     * のうち datasource / entity 以外への依存を禁止する。
     * </p>
     */
    @ArchTest
    void applicationQueryMayDependOnlyOnReadModelInfra(JavaClasses classes) {
        final DescribedPredicate<JavaClass> forbidden = JavaClass.Predicates.resideInAnyPackage(PRESENTATION)
                .or(
                        JavaClass.Predicates.resideInAnyPackage(INFRASTRUCTURE)
                                .and(JavaClass.Predicates.resideOutsideOfPackages(READ_DATASOURCE, READ_ENTITY)));
        noClasses().that().resideInAPackage(APPLICATION_QUERY).should().dependOnClassesThat(forbidden)
                .as("application.query は datasource/entity 以外の infrastructure と presentation に依存してはならない")
                .check(classes);
    }

    /**
     * JPAエンティティ（{@code @Entity}）は永続化エンティティ層にのみ配置する。
     */
    @ArchTest
    void jpaEntitiesShouldResideInPersistenceEntityPackage(JavaClasses classes) {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity").should()
                .resideInAPackage("..infrastructure.persistence.entity..")
                .as("@Entity 付与クラスは infrastructure.persistence.entity 配下にのみ配置する").check(classes);
    }

    /**
     * ドメイン層は {@code java.time}
     * に直接依存してはならない（{@code BusinessDate}/{@code BusinessDateTime} は日時の唯一の窓口として除外）。
     */
    @ArchTest
    void domainShouldNotUseJavaTimeDirectly(JavaClasses classes) {
        noClasses().that().resideInAPackage(DOMAIN).and().haveSimpleNameNotStartingWith("BusinessDate").should()
                .dependOnClassesThat().resideInAPackage("java.time..")
                .as("ドメイン層は java.time に直接依存してはならない（BusinessDate/BusinessDateTime を経由する）").check(classes);
    }

    /**
     * {@code @Transactional} は禁止（Reactive では {@code @WithTransaction} を使う）。
     */
    @ArchTest
    void noJakartaTransactionalOnClasses(JavaClasses classes) {
        noClasses().should().beAnnotatedWith("jakarta.transaction.Transactional")
                .as("@Transactional は禁止（Reactive では @WithTransaction を使う）").check(classes);
    }

    /**
     * {@code @Transactional} は禁止（メソッドレベル）。
     */
    @ArchTest
    void noJakartaTransactionalOnMethods(JavaClasses classes) {
        noMethods().should().beAnnotatedWith("jakarta.transaction.Transactional")
                .as("@Transactional は禁止（Reactive では @WithTransaction を使う）").check(classes);
    }

    /**
     * リポジトリインターフェースのメソッドは {@code Uni<...>} を返さなければならない（リアクティブ契約）。
     */
    @ArchTest
    void repositoryMethodsShouldReturnUni(JavaClasses classes) {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..domain.repository..").and()
                .areDeclaredInClassesThat().areInterfaces().should().haveRawReturnType("io.smallrye.mutiny.Uni")
                .as("Repository インターフェースのメソッドは Uni<...> を返さなければならない").check(classes);
    }

    /**
     * ドメインモデルは {@code BusinessDateTimeProvider} を保持してはならない（日時は引数で受け取る）。
     */
    @ArchTest
    void domainModelShouldNotHoldBusinessDateTimeProvider(JavaClasses classes) {
        noFields().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..").should()
                .haveRawType("com.abservice.domain.service.BusinessDateTimeProvider")
                .as("ドメインモデルは BusinessDateTimeProvider をフィールドに保持してはならない").check(classes);
    }

    /**
     * ドメインモデルのメソッドは {@code Uni<...>} を返してはならない（同期実装）。
     */
    @ArchTest
    void domainModelMethodsShouldNotReturnUni(JavaClasses classes) {
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..").should()
                .haveRawReturnType("io.smallrye.mutiny.Uni").as("ドメインモデルの戻り値に Uni を使わない（同期実装）").check(classes);
    }

    /**
     * ドメインモデル（record を除く）のコンストラクタは非 public でなければならない（生成はファクトリ経由）。
     *
     * <p>
     * record は Java の制約上 canonical constructor を record 自身より狭くできないため対象外とする。
     * record（VO / EntityId）の生成制御はコンパクトコンストラクタの検証とファクトリ（{@code of} /
     * {@code fromInput}）で担保する。
     * </p>
     */
    @ArchTest
    void domainModelConstructorsShouldNotBePublic(JavaClasses classes) {
        noConstructors().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..").and()
                .areDeclaredInClassesThat().areNotRecords().should().bePublic()
                .as("ドメインモデル（非record）のコンストラクタは非publicにする（生成はファクトリ経由）").check(classes);
    }

    /**
     * JPAエンティティ（{@code @Entity}）のクラス名は {@code *TableRecord} サフィックスにする。
     * DDDのDomainEntityと紛らわしい{@code *Entity}を避け、JPA永続化用のテーブルレコードであることを明示する。
     */
    @ArchTest
    void jpaEntitiesShouldHaveTableRecordSuffix(JavaClasses classes) {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity").should()
                .haveSimpleNameEndingWith("TableRecord")
                .as("@Entity 付与クラス名は *TableRecord サフィックスにする").check(classes);
    }

    /**
     * 標準出力ストリーム（{@code System.out} / {@code System.err} /
     * {@code printStackTrace}）を使わない。
     */
    @ArchTest
    void classesShouldNotAccessStandardStreams(JavaClasses classes) {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .as("System.out / System.err / printStackTrace をロギング以外で使わない").check(classes);
    }

    /**
     * {@code EntityId} を実装する具象型は record でなければならない（値型・不変）。
     *
     * <p>
     * Kotlin の {@code value class} 相当を Java では record で表現する。生成の一元化と検証は record
     * のコンパクトコンストラクタ＋ファクトリ（{@code of} / {@code generate}）で担保する。
     * </p>
     */
    @ArchTest
    void entityIdImplementationsShouldBeRecords(JavaClasses classes) {
        classes().that().implement("com.abservice.domain.model.EntityId").should().beRecords()
                .as("EntityId 実装は record にする（値型・不変。生成はコンパクトコンストラクタ＋ファクトリで担保）").check(classes);
    }

    /**
     * ドメインモデルのフィールドは {@code final} でなければならない（不変な状態）。
     *
     * <p>
     * Kotlin の {@code ForbiddenVarInDomain}（domain の可変変数禁止）に対応する Java 制約。Java の
     * {@code var} は型推論であり可変性とは無関係のため、意図（＝ドメイン状態の不変性）はフィールドの {@code final}
     * 化で担保する。状態変更は Wither（新インスタンス生成）で表現する。
     * </p>
     */
    @ArchTest
    void domainModelFieldsShouldBeFinal(JavaClasses classes) {
        fields().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..").should().beFinal()
                .as("ドメインモデルのフィールドは final にする（不変。状態変更は Wither で表現）").check(classes);
    }

    /**
     * Command REST リソース（{@code *CommandResource}）は {@code @RolesAllowed}
     * で保護しなければならない。
     *
     * <p>
     * 作成・更新・削除・公開/非公開といった書き込み操作を無認証で公開しないための強制。集約を追加した際に認可の付与を
     * 忘れても検出できるよう、クラス名の規約（{@code *CommandResource}）を対象に判定する。
     * </p>
     */
    @ArchTest
    void commandResourcesShouldRequireAuthorization(JavaClasses classes) {
        classes().that().resideInAPackage(PRESENTATION).and().haveSimpleNameEndingWith("CommandResource").should()
                .beAnnotatedWith("jakarta.annotation.security.RolesAllowed")
                .as("Command REST リソースは @RolesAllowed で保護する（書き込み操作を無認証で公開しない）").check(classes);
    }

    /**
     * 管理向け Query REST リソース（{@code *AdminQueryResource}）は {@code @RolesAllowed}
     * で保護しなければならない。
     *
     * <p>
     * 管理向け Query は下書き（未公開）を含む全件を返すため、無認証で公開すると未公開コンテンツが漏洩する。
     * </p>
     */
    @ArchTest
    void adminQueryResourcesShouldRequireAuthorization(JavaClasses classes) {
        classes().that().resideInAPackage(PRESENTATION).and().haveSimpleNameEndingWith("AdminQueryResource").should()
                .beAnnotatedWith("jakarta.annotation.security.RolesAllowed")
                .as("管理向け Query REST リソースは @RolesAllowed で保護する（下書きを無認証で公開しない）").check(classes);
    }

    /**
     * ApplicationService の {@code execute} / {@code query} は {@code Uni<...>}
     * を返さなければならない。
     *
     * <p>
     * {@code CommandService.execute} / {@code QueryService.query} の基底契約（リアクティブ）を、
     * 具象ユースケース実装が生えても維持させる前方ガード。具象未整備の現時点では基底IFの2メソッドが対象。
     * </p>
     */
    @ArchTest
    void applicationServiceExecuteAndQueryShouldReturnUni(JavaClasses classes) {
        methods().that().haveNameMatching("execute|query").and().areDeclaredInClassesThat()
                .resideInAPackage(APPLICATION).should().haveRawReturnType("io.smallrye.mutiny.Uni")
                .as("ApplicationService の execute/query は Uni<...> を返す").allowEmptyShould(true).check(classes);
    }

    /**
     * アルバムの取得は、編集権または参照の主張を伴う唯一の入口を通す（#178 A-2）。
     *
     * <p>
     * 集約をまたぐ不変条件は、判定に使ったアルバムが書き込みまでの間に動かないことを前提にする。主張を伴わない取得が
     * 業務コードから使えると、その前提を満たさない経路が増える。取得の口を {@code AlbumAccessService} に閉じることで、
     * 主張の取り忘れをビルドで検出する。
     * </p>
     */
    @ArchTest
    void albumShouldOnlyBeObtainedThroughAClaim(JavaClasses classes) {
        classes().that().resideOutsideOfPackage(INFRASTRUCTURE)
                .and().doNotHaveSimpleName("AlbumAccessService")
                .should(obtainAlbumOnlyThroughClaim())
                .as(
                        "アルバムの取得は AlbumAccessService（編集権・参照の主張を伴う唯一の入口）を通す"
                                + "（主張を伴わない取得を業務コードから使わせない、#178 A-2）")
                .allowEmptyShould(true).check(classes);
    }

    /**
     * 応答の record の単純名は、入れ子を含めて一意でなければならない。
     *
     * <p>
     * OpenAPI のスキーマ名は型の単純名で、衝突すると smallrye が連番（{@code Foo2}）を付ける。定義へ「常にある項目」を
     * 反映するフィルタ（{@code ResponseNullabilityFilter}）はスキーマ名から型を引くため、衝突した側は索引から外れ、
     * 別の型の項目を当てるか素通りするかのどちらかになる。どちらも黙って起きるので、宣言の時点で落とす。
     * </p>
     */
    @ArchTest
    void responseRecordSimpleNamesShouldBeUnique(JavaClasses classes) {
        classes().that().resideInAPackage(REST_RESPONSE).and(areRecords())
                .should(haveAnUnsharedSimpleName(sharedSimpleNames(classes)))
                .as("応答の record の単純名は入れ子を含めて一意にする（OpenAPI のスキーマ名が単純名のため）")
                .allowEmptyShould(true).check(classes);
    }

    private static DescribedPredicate<JavaClass> areRecords() {
        return DescribedPredicate.describe("are records", JavaClass::isRecord);
    }

    private static List<String> sharedSimpleNames(JavaClasses classes) {
        return responseRecords(classes)
                .collect(Collectors.groupingBy(JavaClass::getSimpleName, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Stream<JavaClass> responseRecords(JavaClasses classes) {
        return classes.stream()
                .filter(JavaClass::isRecord)
                .filter(javaClass -> javaClass.getPackageName().endsWith(".response"));
    }

    private static ArchCondition<JavaClass> haveAnUnsharedSimpleName(List<String> sharedSimpleNames) {
        return new ArchCondition<>("have a simple name shared with no other response record") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional.of(javaClass)
                        .filter(type -> sharedSimpleNames.contains(type.getSimpleName()))
                        .map(LayeredArchitectureTest::sharedSimpleNameViolation)
                        .ifPresent(events::add);
            }
        };
    }

    private static ConditionEvent sharedSimpleNameViolation(JavaClass javaClass) {
        return SimpleConditionEvent.violated(
                javaClass,
                "%s の単純名 %s が他の応答 record と重複している".formatted(
                        javaClass.getFullName(),
                        javaClass.getSimpleName()));
    }

    /*
     * METHOD-REFERENCE: getAccessesFromSelf()
     * はメソッド参照（Foo::bar）を含まないため、参照経由で迂回できてしまう。 呼び出しと参照の両方を集めて検査する。
     */
    private static ArchCondition<JavaClass> obtainAlbumOnlyThroughClaim() {
        return new ArchCondition<>("obtain albums only through AlbumAccessService") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                albumLookups(javaClass).forEach(access -> events.add(lookupViolation(javaClass, access)));
            }
        };
    }

    private static Stream<JavaAccess<?>> albumLookups(JavaClass javaClass) {
        return javaClass.getCodeUnits().stream()
                .flatMap(
                        unit -> Stream.<JavaAccess<?>>concat(
                                unit.getMethodCallsFromSelf().stream(),
                                unit.getMethodReferencesFromSelf().stream()))
                .filter(LayeredArchitectureTest::isAlbumLookup);
    }

    /*
     * BRIDGE-METHOD: AlbumRepository が基底の findById を再宣言するため javac がブリッジメソッドを生成し、
     * それ自身が findById を呼ぶ。リポジトリ内部の委譲は取得の入口を迂回していないので除外する。
     */
    private static boolean isAlbumLookup(JavaAccess<?> access) {
        return Optional.of(access)
                .filter(target -> target.getTargetOwner().isAssignableTo(AlbumRepository.class))
                .filter(not(target -> target.getOriginOwner().isAssignableTo(AlbumRepository.class)))
                .map(target -> target.getTarget().getName())
                .filter(ALBUM_LOOKUP_METHODS::contains)
                .isPresent();
    }

    private static ConditionEvent lookupViolation(JavaClass javaClass, JavaAccess<?> access) {
        return SimpleConditionEvent.violated(
                javaClass,
                "%s が %s を直接使っている（AlbumAccessService の主張を伴う取得を通していない）".formatted(
                        access.getOrigin().getFullName(),
                        access.getTarget().getFullName()));
    }
}
