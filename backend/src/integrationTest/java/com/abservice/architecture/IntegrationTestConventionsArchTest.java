package com.abservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.abservice.test.CleanDatabase;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Arrays;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 統合テストの規約のアーキテクチャ制約テスト
 *
 * <p>
 * 統合テストは1つのデータベースを共有する。各テストの前に空へ戻す仕掛け（{@link CleanDatabase}）を付け忘れたクラスがあると、そのクラスだけが
 * 他のテストの残したデータを含む母集団を見る（#252）。付け忘れを人の注意に委ねず、ここで落とす。
 * </p>
 */
@AnalyzeClasses(packages = "com.abservice")
class IntegrationTestConventionsArchTest {

    /**
     * {@code @QuarkusTest} のクラスは {@code @ExtendWith(CleanDatabase.class)} を付ける。
     */
    @ArchTest
    void quarkusTestsShouldResetDatabase(JavaClasses classes) {
        classes().that().areAnnotatedWith(QuarkusTest.class)
                .should(resetDatabaseBeforeEachTest())
                .as("@QuarkusTest のクラスは @ExtendWith(CleanDatabase.class) を付ける")
                .check(classes);
    }

    private static ArchCondition<JavaClass> resetDatabaseBeforeEachTest() {
        return new ArchCondition<>("@ExtendWith(CleanDatabase.class) を持つ") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                events.add(
                        new SimpleConditionEvent(
                                item,
                                EXTENDED_WITH_CLEAN_DATABASE.test(item),
                                "%s に @ExtendWith(CleanDatabase.class) がありません".formatted(item.getName())));
            }
        };
    }

    /** {@code @ExtendWith} の値に {@link CleanDatabase} を含むか */
    private static final DescribedPredicate<JavaClass> EXTENDED_WITH_CLEAN_DATABASE = new DescribedPredicate<>(
            "@ExtendWith(CleanDatabase.class) を持つ") {
        @Override
        public boolean test(JavaClass item) {
            return item.tryGetAnnotationOfType(ExtendWith.class)
                    .map(annotation -> Arrays.asList(annotation.value()).contains(CleanDatabase.class))
                    .orElse(false);
        }
    };
}
