package com.abservice.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static java.util.function.Predicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.CrossAggregateTransition;
import com.abservice.domain.service.DomainService;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;

/**
 * Aggregate/Entity の Always Valid 強制（#101）。
 *
 * <p>
 * private な全項目コンストラクタは自身では検証しないため、{@link AggregateFactory} が付与されたメソッド以外から
 * 呼び出されると Policy 検証を迂回できてしまう。この迂回経路が存在しないことを ArchUnit で強制する。
 * </p>
 *
 * <p>
 * 各クラスは検証対象フィールドを {@code Stub} という制約を持たないネストしたrecordに集約し、 {@code factory} メソッドが
 * Policy 検証を経てから {@code Stub} の {@code asXxx()}（ {@link AggregateFactory}
 * 付き）を呼び出す。{@code Stub} は手書きのため実コンストラクタと 引数が乖離しうるが、その乖離も別ルールで機械的に検出する。
 * </p>
 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
class AggregateConstructionArchTest {

    @ArchTest
    void privateConstructorsShouldOnlyBeCalledByAggregateFactory(JavaClasses classes) {
        constructors().that().arePrivate()
                .and().areDeclaredInClassesThat().resideInAnyPackage(
                        "com.abservice.domain.model.aggregate..",
                        "com.abservice.domain.model.entity..")
                .and().areDeclaredInClassesThat().haveNameNotMatching(".*Stub")
                .should().onlyBeCalled().byCodeUnitsThat(annotatedWith(AggregateFactory.class))
                .as(
                        "Aggregate/Entity の private な全項目コンストラクタは @AggregateFactory 付きメソッドからのみ呼び出せる"
                                + "（ネストされた Stub 自身は制約を持たないdumbな入れ物のため対象外）")
                .check(classes);
    }

    @ArchTest
    void crossAggregateTransitionsShouldOnlyBeCalledByDomainServices(JavaClasses classes) {
        methods().that().areAnnotatedWith(CrossAggregateTransition.class)
                .should(onlyBeReachedFromDomainServices())
                .as(
                        "参照先集約の状態に依存する遷移（@CrossAggregateTransition）は、参照先を引いて規則を適用する"
                                + "ドメインサービスからのみ呼び出せる（順序の知識を呼び出し側に持たせない、#176）")
                .check(classes);
    }

    /*
     * METHOD-REFERENCE: onlyBeCalled() はメソッド参照（Foo::bar）を呼び出しとして数えないため、参照経由で
     * 迂回できてしまう。呼び出しと参照の両方を含む getAccessesToSelf() を origin として検査する。
     */
    private static ArchCondition<JavaMethod> onlyBeReachedFromDomainServices() {
        return new ArchCondition<>("only be reached from domain services") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getAccessesToSelf().stream()
                        .filter(not(AggregateConstructionArchTest::originatesInDomainService))
                        .forEach(access -> events.add(violation(method, access)));
            }
        };
    }

    private static boolean originatesInDomainService(JavaAccess<?> access) {
        return access.getOriginOwner().isAssignableTo(DomainService.class);
    }

    private static ConditionEvent violation(JavaMethod method, JavaAccess<?> access) {
        return SimpleConditionEvent.violated(
                method,
                "%s は %s から呼ばれている（ドメインサービス以外）".formatted(
                        method.getFullName(),
                        access.getOriginOwner().getName()));
    }

    @ArchTest
    void stubShouldMatchEnclosingConstructor(JavaClasses classes) {
        classes().that().haveSimpleName("Stub")
                .and().resideInAnyPackage(
                        "com.abservice.domain.model.aggregate..",
                        "com.abservice.domain.model.entity..")
                .should().beRecords()
                .andShould(matchEnclosingConstructorParameters())
                .as(
                        "ネストされた Stub の引数は、囲むクラスの全項目コンストラクタと型・順序が一致していなければならない"
                                + "（手書きの Stub が実コンストラクタから乖離することを防ぐ、#101）")
                .check(classes);
    }

    private static ArchCondition<JavaClass> matchEnclosingConstructorParameters() {
        return new ArchCondition<>("match the enclosing class's constructor parameters") {
            @Override
            public void check(JavaClass stubClass, ConditionEvents events) {
                final JavaClass enclosing = stubClass.getEnclosingClass().orElseThrow();
                final JavaConstructor stubConstructor = stubClass.getConstructors().stream().findFirst().orElseThrow();
                final var stubParameterTypes = stubConstructor.getRawParameterTypes();
                final boolean anyMatches = enclosing.getConstructors().stream()
                        .anyMatch(c -> c.getRawParameterTypes().equals(stubParameterTypes));
                final ConditionEvent event = anyMatches
                        ? null
                        : SimpleConditionEvent.violated(
                                stubClass,
                                "Stub " + stubClass.getName() + " parameter types "
                                        + stubParameterTypes + " do not match any constructor of "
                                        + enclosing.getName());
                Optional.ofNullable(event).ifPresent(events::add);
            }
        };
    }
}
