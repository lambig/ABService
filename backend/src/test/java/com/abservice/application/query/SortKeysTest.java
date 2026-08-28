package com.abservice.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.query.album.AlbumSortKey;
import com.abservice.domain.exception.ValidationException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SortKeys（クエリパラメータ→並び順の解決）のテスト")
class SortKeysTest {

    @Test
    @DisplayName("キー未指定なら登録の新しい順（domainId降順）になる")
    void unspecifiedKeyFallsBackToDomainIdDesc() {
        final var spec = SortKeys.resolve(
                AlbumSortKey.values(),
                null,
                null,
                Audience.PUBLIC);

        assertThat(spec.property()).isEqualTo("domainId");
        assertThat(spec.direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @DisplayName("キーだけ指定すると向きはキーごとの既定になる")
    void directionFallsBackToKeyDefault() {
        final var spec = SortKeys.resolve(
                AlbumSortKey.values(),
                "releaseDate",
                null,
                Audience.PUBLIC);

        assertThat(spec.property()).isEqualTo("releaseDate");
        assertThat(spec.direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @DisplayName("向きを指定すると既定を上書きし、キーの綴りは大文字小文字を区別しない")
    void directionOverridesDefaultIgnoringCase() {
        final var spec = SortKeys.resolve(
                AlbumSortKey.values(),
                "RELEASEDATE",
                "ASC",
                Audience.PUBLIC);

        assertThat(spec.property()).isEqualTo("releaseDate");
        assertThat(spec.direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @DisplayName("キー未指定でも向きの指定は効く")
    void directionAppliesToDefaultKey() {
        final var spec = SortKeys.resolve(
                AlbumSortKey.values(),
                null,
                "asc",
                Audience.PUBLIC);

        assertThat(spec.property()).isEqualTo("domainId");
        assertThat(spec.direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @DisplayName("管理向けのキーを公開向けに指定すると400相当の検証エラーになる")
    void adminOnlyKeyIsRejectedForPublicAudience() {
        assertThatThrownBy(
                () -> SortKeys.resolve(
                        AudienceScopedSortKey.values(),
                        "adminOnly",
                        null,
                        Audience.PUBLIC))
                .isInstanceOf(ValidationException.class)
                .satisfies(
                        thrown -> assertThat(((ValidationException) thrown).errors())
                                .singleElement()
                                .satisfies(error -> assertThat(error.code()).isEqualTo("SORT_KEY_NOT_USABLE")));
    }

    @Test
    @DisplayName("管理向けなら管理専用のキーを使える")
    void adminOnlyKeyIsUsableForAdminAudience() {
        final var spec = SortKeys.resolve(
                AudienceScopedSortKey.values(),
                "adminOnly",
                null,
                Audience.ADMIN);

        assertThat(spec.property()).isEqualTo("adminOnly");
    }

    @Test
    @DisplayName("未知のキーは検証エラーになり、既定へ落とさない")
    void unknownKeyIsRejected() {
        assertThatThrownBy(
                () -> SortKeys.resolve(
                        AlbumSortKey.values(),
                        "title",
                        null,
                        Audience.PUBLIC))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("未知の向きは検証エラーになる")
    void unknownDirectionIsRejected() {
        assertThatThrownBy(
                () -> SortKeys.resolve(
                        AlbumSortKey.values(),
                        "releaseDate",
                        "sideways",
                        Audience.PUBLIC))
                .isInstanceOf(ValidationException.class)
                .satisfies(
                        thrown -> assertThat(((ValidationException) thrown).errors())
                                .singleElement()
                                .satisfies(error -> assertThat(error.code()).isEqualTo("SORT_DIRECTION_NOT_USABLE")));
    }

    /*
     * 要求元でキー集合が分かれる機構の検証には、集約が実際に持つキーではなくこの列挙を使う。集約側のキーは業務都合で
     * 増減するため（監査列のキー削除など）、機構のテストがその変更で壊れないようにする。
     */
    private enum AudienceScopedSortKey implements SortKey {

        SHARED(
                "shared",
                "shared",
                SortDirection.DESC,
                Set.of(Audience.PUBLIC, Audience.ADMIN)),

        ADMIN_ONLY(
                "adminOnly",
                "adminOnly",
                SortDirection.DESC,
                Set.of(Audience.ADMIN));

        private final String parameterValue;
        private final String property;
        private final SortDirection defaultDirection;
        private final Set<Audience> audiences;

        AudienceScopedSortKey(
                String parameterValue,
                String property,
                SortDirection defaultDirection,
                Set<Audience> audiences) {
            this.parameterValue = parameterValue;
            this.property = property;
            this.defaultDirection = defaultDirection;
            this.audiences = audiences;
        }

        @Override
        public String parameterValue() {
            return parameterValue;
        }

        @Override
        public String property() {
            return property;
        }

        @Override
        public SortDirection defaultDirection() {
            return defaultDirection;
        }

        @Override
        public Set<Audience> audiences() {
            return audiences;
        }
    }
}
