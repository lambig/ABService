package com.abservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.vo.common.CommonValueObjectSamples;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 製品側と同じ配置規則を、許容する依存と拒否する依存の両方で検証する。 */
class CommonValueObjectRuleTest {

    @ParameterizedTest
    @MethodSource("allowedSamples")
    @DisplayName("共通の値・検証Policy・値の基底契約への依存は許可する")
    void acceptsSharedValueDependencies(Class<?> sample) {
        final var classes = new ClassFileImporter().importClasses(sample);
        assertThat(
                CommonValueObjectsArchTest.COMMON_VALUES_HAVE_NO_SPECIFIC_DOMAIN_DEPENDENCIES.evaluate(classes)
                        .hasViolation())
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("rejectedSamples")
    @DisplayName("個別VO・集約・サービスへの依存は型引数内でも拒否する")
    void rejectsSpecificDomainDependencies(Class<?> sample) {
        final var classes = new ClassFileImporter().importClasses(sample);
        final var result = CommonValueObjectsArchTest.COMMON_VALUES_HAVE_NO_SPECIFIC_DOMAIN_DEPENDENCIES
                .evaluate(classes);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anySatisfy(
                detail -> assertThat(detail).contains(sample.getName()));
    }

    static Stream<Class<?>> allowedSamples() {
        return Stream.of(
                CommonValueObjectSamples.SharedValue.class,
                CommonValueObjectSamples.Validation.class,
                CommonValueObjectSamples.ValueContract.class,
                CommonValueObjectSamples.DomainContract.class);
    }

    static Stream<Class<?>> rejectedSamples() {
        return Stream.of(
                CommonValueObjectSamples.SpecificValue.class,
                CommonValueObjectSamples.AggregateValue.class,
                CommonValueObjectSamples.ServiceValue.class,
                CommonValueObjectSamples.GenericSpecificValue.class);
    }
}
