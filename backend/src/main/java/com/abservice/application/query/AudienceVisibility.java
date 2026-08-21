package com.abservice.application.query;

import com.abservice.infrastructure.persistence.datasource.Visibility;

/**
 * {@link Audience} から DataSource の検索スコープ（{@link Visibility}）への変換
 *
 * <p>
 * 集約ごとの Query サービスが個別に対応表を持つと公開判定が分散するため、変換は本クラスに集約する。
 * </p>
 */
public final class AudienceVisibility {

    private AudienceVisibility() {
    }

    /**
     * 要求元に対応する検索スコープを返します。
     *
     * @param audience
     *            Query の要求元
     * @return 公開向けなら公開中のみ、管理向けなら全件を対象とするスコープ
     */
    public static Visibility of(Audience audience) {
        return switch (audience) {
            case PUBLIC -> Visibility.PUBLIC_ONLY;
            case ADMIN -> Visibility.ALL;
        };
    }
}
