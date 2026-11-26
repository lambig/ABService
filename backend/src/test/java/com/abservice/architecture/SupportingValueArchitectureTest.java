package com.abservice.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.abservice.domain.model.DomainObject;
import com.abservice.domain.model.vo.album.EventReleasedAt;
import com.abservice.domain.model.vo.event.EventName;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

/** 支援値の責務ごとの依存と、作品の初出記述が使う値を検査する。 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
public class SupportingValueArchitectureTest {

    /** 保管キーは値の検証だけを所有する。 */
    @ArchTest
    public void assetKeysUseOnlyTheirValueContract(JavaClasses imported) {
        classes().that().resideInAPackage("..vo.asset..")
                .should().onlyDependOnClassesThat(valueDependencies("..vo.asset.."))
                .check(imported);
    }

    /** リンク値は音源固有の制約に依存しない。 */
    @ArchTest
    public void linksUseOnlyTheirValueContract(JavaClasses imported) {
        classes().that().resideInAPackage("..vo.link..")
                .should().onlyDependOnClassesThat(valueDependencies("..vo.link.."))
                .check(imported);
    }

    /** 音源参照はリンクの構文契約を使い、音源の制約を所有する。 */
    @ArchTest
    public void audioReferencesMayUseLinks(JavaClasses imported) {
        classes().that().resideInAPackage("..vo.audio..")
                .should().onlyDependOnClassesThat(valueDependencies("..vo.audio..", "..vo.link.."))
                .check(imported);
    }

    /** 日付時刻の変換は現在時刻の取得やイベントに依存しない。 */
    @ArchTest
    public void timeValuesUseOnlyTheirValueContract(JavaClasses imported) {
        classes().that().resideInAPackage("..vo.time..")
                .should().onlyDependOnClassesThat(valueDependencies("..vo.time.."))
                .check(imported);
    }

    /** 参加状態・照合の値は日付を使い、作品の初出記述には依存しない。 */
    @ArchTest
    public void eventValuesMayUseTime(JavaClasses imported) {
        classes().that().resideInAPackage("..vo.event..")
                .should().onlyDependOnClassesThat(valueDependencies("..vo.event..", "..vo.time.."))
                .check(imported);
    }

    /** 作品の初出記述はイベント名・日付と自身の値契約を使う。 */
    @ArchTest
    public void firstReleaseDescriptionMayUseEventNameAndTime(JavaClasses imported) {
        classes().that().haveFullyQualifiedName(EventReleasedAt.class.getName())
                .should().onlyDependOnClassesThat(
                        valueDependencies("..vo.time..")
                                .or(equivalentTo(EventReleasedAt.class))
                                .or(equivalentTo(EventName.class)))
                .check(imported);
    }

    /** 値の所有先をcommonへ戻す変更を検出する。 */
    @ArchTest
    public void valuesHaveSpecificOwners(JavaClasses imported) {
        noClasses().should().resideInAPackage("..domain.model.vo.common..").check(imported);
    }

    private static DescribedPredicate<JavaClass> valueDependencies(String... owners) {
        return resideInAnyPackage(owners).or(
                resideInAnyPackage(
                        "com.abservice.domain.model.vo",
                        "..domain.model.policy..",
                        "com.abservice.lib..",
                        "java..",
                        "org.jspecify.annotations..",
                        "lombok..",
                        "org.apache.commons..",
                        "io.github.lambig.."))
                .or(equivalentTo(DomainObject.class));
    }
}
