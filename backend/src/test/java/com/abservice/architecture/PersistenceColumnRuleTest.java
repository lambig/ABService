package com.abservice.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("永続化列の更新漏れ検査の正例・違反例")
class PersistenceColumnRuleTest {

    @Test
    @DisplayName("主キー・不変ID・更新不可列・基底監査列・関連を除外する")
    void acceptsWritableColumnsAndExclusions() {
        assertThatCode(
                () -> verify(
                        "save",
                        "complete",
                        Row.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("更新時だけ説明が欠落すると列名を示して失敗する")
    void rejectsOmittedDescription() {
        assertThatThrownBy(
                () -> verify(
                        "saveIncomplete",
                        "incomplete",
                        Row.class))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Row.description");
    }

    @Test
    @DisplayName("呼ばれないメソッドにsetterが存在しても更新の証拠にしない")
    void rejectsUnreachableUpdate() {
        assertThatThrownBy(
                () -> verify(
                        "saveIncomplete",
                        "complete",
                        Row.class))
                .isInstanceOf(AssertionError.class).hasMessageContaining("更新処理へ到達");
    }

    @Test
    @DisplayName("メソッド参照による委譲を追跡する")
    void acceptsMethodReference() {
        assertThatCode(
                () -> verify(
                        "saveReference",
                        "complete",
                        Row.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("循環する委譲でも停止して未更新列を検出する")
    void terminatesOnCycle() {
        assertThatThrownBy(
                () -> verify(
                        "cycle",
                        "cycle",
                        Row.class))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Row.description");
    }

    @Test
    @DisplayName("新しい更新対象列は期待値の列挙を変えずに検出する")
    void rejectsNewColumnWithoutUpdate() {
        assertThatThrownBy(
                () -> verify(
                        "saveAdded",
                        "added",
                        AddedRow.class))
                .isInstanceOf(AssertionError.class).hasMessageContaining("AddedRow.newValue");
    }

    private static void verify(
            String save,
            String update,
            Class<?> entity) {
        final var imported = new ClassFileImporter().importClasses(
                Repository.class,
                entity,
                Audit.class);
        final var repository = imported.get(Repository.class);
        PersistenceColumnCoverageTest.verify(
                repository.getMethod(save, entity),
                repository.getMethod(update, entity),
                imported.get(entity));
    }

    static class Audit {
        @Column
        private String updatedAt;
    }

    static class Row extends Audit {
        @Id
        @Column
        private Long id;
        @Column
        private String domainId;
        @Column(updatable = false)
        private String created;
        @OneToMany
        private List<Row> children;
        @Column
        private String title;
        @Column
        private String description;

        void setTitle(String value) {
            title = value;
        }

        void setDescription(String value) {
            description = value;
        }
    }

    static class AddedRow {
        @Column
        private String title;
        @Column
        private String newValue;

        void setTitle(String value) {
            title = value;
        }

        void setNewValue(String value) {
            newValue = value;
        }
    }

    static class Repository {
        void save(Row row) {
            complete(row);
        }

        void complete(Row row) {
            row.setTitle("new title");
            row.setDescription("new description");
        }

        void saveIncomplete(Row row) {
            incomplete(row);
        }

        void incomplete(Row row) {
            row.setTitle("new title");
        }

        void saveReference(Row row) {
            final Consumer<Row> update = this::complete;
            update.accept(row);
        }

        void cycle(Row row) {
            cycleBack(row);
        }

        void cycleBack(Row row) {
            cycle(row);
        }

        void saveAdded(AddedRow row) {
            added(row);
        }

        void added(AddedRow row) {
            row.setTitle("new title");
        }
    }
}
