package com.abservice.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.abservice.domain.model.DomainObject;
import com.abservice.domain.model.vo.ValueObject;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 共通VOは個別の業務モデルやサービスを知らず、値と検証だけで成立する。 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
final class CommonValueObjectsArchTest {

    private CommonValueObjectsArchTest() {
    }

    @ArchTest
    static final ArchRule COMMON_VALUES_HAVE_NO_SPECIFIC_DOMAIN_DEPENDENCIES = classes()
            .that().resideInAPackage("com.abservice.domain.model.vo.common..")
            .should().onlyDependOnClassesThat(
                    resideOutsideOfPackage("com.abservice.domain..")
                            .or(
                                    resideInAnyPackage(
                                            "com.abservice.domain.model.vo.common..",
                                            "com.abservice.domain.model.policy.."))
                            .or(equivalentTo(ValueObject.class))
                            .or(equivalentTo(DomainObject.class)))
            .as("共通VOのドメイン依存は共通VO・値の基底契約・検証Policyに限る");
}
