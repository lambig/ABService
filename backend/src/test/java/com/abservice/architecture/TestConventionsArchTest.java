package com.abservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

/**
 * テスト規約のアーキテクチャ制約テスト
 *
 * <p>
 * テストコードの規約を ArchUnit で強制する。テストクラス自体を検査対象にするため、 テストを除外する
 * {@link LayeredArchitectureTest} とは別クラスとし、{@code importOptions}
 * を指定せずテストクラスを取り込む。
 * </p>
 *
 * <p>
 * 対応ドキュメント: {@code docs/CODING_GUIDELINES.md} §1（静的解析ガバナンス）。
 * </p>
 */
@AnalyzeClasses(packages = "com.abservice")
class TestConventionsArchTest {

    /**
     * {@code @Test} / {@code @ParameterizedTest} メソッドには {@code @DisplayName} を付与する。
     *
     * <p>
     * テストレポートの可読性を担保するため、日本語の表示名を必須とする。ArchUnit 自身のルールは {@code @ArchTest} で定義され
     * {@code @Test} ではないため、本ルールの対象外となる。
     * </p>
     */
    @ArchTest
    void testMethodsShouldHaveDisplayName(JavaClasses classes) {
        methods().that().areAnnotatedWith("org.junit.jupiter.api.Test").or()
                .areAnnotatedWith("org.junit.jupiter.params.ParameterizedTest").should()
                .beAnnotatedWith("org.junit.jupiter.api.DisplayName")
                .as("@Test / @ParameterizedTest メソッドには @DisplayName を付与する").check(classes);
    }
}
