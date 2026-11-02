package com.abservice.domain.model.vo.credit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.architecture.CreditValueArchitectureTest;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.infrastructure.persistence.mapper.AlbumMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("音楽名義の依存規則の正例と違反例")
class CreditDependencyRuleTest {

    @Test
    @DisplayName("現存する名義と役割表記の検証依存を許可する")
    void acceptsExistingValues() {
        assertThatCode(
                () -> check(
                        ArtistCredit.class,
                        ArtistCreditName.class,
                        Credit.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("集約への依存を拒否する")
    void rejectsAggregateDependency() {
        assertThatThrownBy(() -> check(AggregateDependent.class)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Album");
    }

    @Test
    @DisplayName("別責務の値への依存を拒否する")
    void rejectsOtherValueDependency() {
        assertThatThrownBy(() -> check(LinkDependent.class)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Url");
    }

    @Test
    @DisplayName("永続化への依存を拒否する")
    void rejectsPersistenceDependency() {
        assertThatThrownBy(() -> check(PersistenceDependent.class)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("AlbumMapper");
    }

    private static void check(Class<?>... types) {
        new CreditValueArchitectureTest().creditValuesHaveOnlyValueDependencies(
                new ClassFileImporter().importClasses(types));
    }

    static class AggregateDependent {
        Album value(Album album) {
            return album;
        }
    }

    static class LinkDependent {
        Url value(Url url) {
            return url;
        }
    }

    static class PersistenceDependent {
        AlbumMapper value(AlbumMapper mapper) {
            return mapper;
        }
    }
}
