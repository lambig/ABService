package com.abservice.architecture;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import com.abservice.infrastructure.persistence.repository.ArticleRepositoryImpl;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 既存行を更新する経路で、書換対象の列のsetterが呼ばれることを検査する。 主キー・不変のdomainId・更新不可列・基底の監査列・関連は対象外。
 * 呼出し先の有無だけを扱い、値・受信側の正しさや実際のflushは実DBの回帰で確認する。
 */
@AnalyzeClasses(packages = "com.abservice", importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceColumnCoverageTest {

    @ArchTest
    void albumUpdatesCoverWritableColumns(JavaClasses imported) {
        final var repository = imported.get(AlbumRepositoryImpl.class);
        verify(
                repository.getMethod("save", Album.class),
                repository.getMethod(
                        "updateExisting",
                        AlbumTableRecord.class,
                        AlbumTableRecord.class,
                        Album.class),
                imported.get(AlbumTableRecord.class));
    }

    @ArchTest
    void articleUpdatesCoverWritableColumns(JavaClasses imported) {
        final var repository = imported.get(ArticleRepositoryImpl.class);
        verify(
                repository.getMethod("save", Article.class),
                repository.getMethod(
                        "copyArticleScalarFields",
                        ArticleTableRecord.class,
                        ArticleTableRecord.class),
                imported.get(ArticleTableRecord.class));
    }

    static void verify(
            JavaMethod save,
            JavaMethod update,
            JavaClass entity) {
        assertThat(reachable(save, Set.of()))
                .as("%sから既存行の更新処理へ到達する", save.getFullName())
                .contains(update);
        final var columns = entity.getFields().stream()
                .filter(field -> field.isAnnotatedWith(Column.class))
                .filter(not(field -> field.isAnnotatedWith(Id.class)))
                .filter(field -> field.getAnnotationOfType(Column.class).updatable())
                .filter(not(field -> field.getName().equals("domainId")))
                .toList();
        assertThat(columns).as("検査対象の列が存在する").isNotEmpty();
        final var called = reachable(update, Set.of()).stream()
                .flatMap(PersistenceColumnCoverageTest::accesses)
                .map(access -> access.getTarget().resolveMember())
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        final var missing = columns.stream().filter(not(field -> called.contains(setter(field))))
                .map(JavaField::getFullName).sorted().toList();
        assertThat(missing)
                .as("%sからsetterが呼ばれない列。更新処理または列の不変性を確認する", update.getFullName())
                .isEmpty();
    }

    private static JavaMethod setter(JavaField field) {
        final var name = field.getName();
        return field.getOwner().getMethod(
                "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1),
                field.getRawType().getName());
    }

    private static List<JavaMethod> reachable(JavaMethod method, Set<JavaMethod> path) {
        return path.contains(method)
                ? List.of()
                : Stream.concat(
                        Stream.of(method),
                        accesses(method)
                                .filter(access -> access.getTargetOwner().equals(method.getOwner()))
                                .map(access -> access.getTarget().resolveMember())
                                .flatMap(Optional::stream)
                                .filter(JavaMethod.class::isInstance)
                                .map(JavaMethod.class::cast)
                                .flatMap(
                                        next -> reachable(
                                                next,
                                                Stream.concat(path.stream(), Stream.of(method))
                                                        .collect(Collectors.toUnmodifiableSet()))
                                                .stream()))
                        .distinct().toList();
    }

    private static Stream<JavaAccess<?>> accesses(JavaMethod method) {
        return Stream.concat(method.getMethodCallsFromSelf().stream(), method.getMethodReferencesFromSelf().stream());
    }
}
