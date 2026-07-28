package com.abservice.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.abservice.domain.model.AggregateFactory;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
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
                Optional.of(anyMatches)
                        .filter(matches -> !matches)
                        .ifPresent(
                                unused -> events.add(
                                        SimpleConditionEvent.violated(
                                                stubClass,
                                                "Stub " + stubClass.getName() + " parameter types "
                                                        + stubParameterTypes + " do not match any constructor of "
                                                        + enclosing.getName())));
            }
        };
    }
}
