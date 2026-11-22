package com.abservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;

import com.abservice.domain.model.DomainObject;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

/** 音楽名義の値が集約・別責務の値・外側の層へ依存しないことを検査する。 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
public class CreditValueArchitectureTest {

    /** 表記の検証・結果合成・値の基底契約と同じ責務の値を内部依存として許可する。 */
    @ArchTest
    public void creditValuesHaveOnlyValueDependencies(JavaClasses imported) {
        classes().that().resideInAPackage("..domain.model.vo.credit..")
                .should().onlyDependOnClassesThat(
                        resideInAnyPackage(
                                "..domain.model.vo.credit..",
                                "com.abservice.domain.model.vo",
                                "..domain.model.policy..",
                                "com.abservice.lib..",
                                "java..",
                                "org.jspecify.annotations..",
                                "lombok..",
                                "org.apache.commons..",
                                "io.github.lambig..")
                                // ValueObjectの型消去で生成されるequivalentToのbridgeも基底契約を参照する。
                                .or(equivalentTo(DomainObject.class)))
                .as("音楽名義の値は集約・別責務の値・外側の層に依存しない").check(imported);
    }
}
