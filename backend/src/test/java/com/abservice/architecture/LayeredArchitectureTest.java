package com.abservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

/**
 * アーキテクチャ制約テスト（フェーズA: 基本ルール）
 *
 * <p>
 * album プロダクトの arch カスタムルールに相当するアーキテクチャ制約を ArchUnit で再現する。
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
 * 対応ドキュメント: {@code docs/STATUS_AND_ROADMAP.md} §7。
 * </p>
 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String PRESENTATION = "..presentation..";

    /**
     * レイヤー依存方向: ドメイン層は他のどのレイヤーにも依存してはならない。
     */
    @ArchTest
    void domainShouldNotDependOnOuterLayers(JavaClasses classes) {
        noClasses().that().resideInAPackage(DOMAIN).should().dependOnClassesThat()
                .resideInAnyPackage(APPLICATION, INFRASTRUCTURE, PRESENTATION)
                .as("ドメイン層は application / infrastructure / presentation に依存してはならない").check(classes);
    }

    /**
     * レイヤー依存方向: アプリケーション層は infrastructure / presentation に依存してはならない。
     */
    @ArchTest
    void applicationShouldNotDependOnInfraOrPresentation(JavaClasses classes) {
        noClasses().that().resideInAPackage(APPLICATION).should().dependOnClassesThat()
                .resideInAnyPackage(INFRASTRUCTURE, PRESENTATION)
                .as("アプリケーション層は infrastructure / presentation に依存してはならない").check(classes);
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
}
