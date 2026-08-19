package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.common.BusinessDateTime;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * 未公開（下書き）状態を表す{@link Publication}
 *
 * <p>
 * 不変条件を持たないコンポーネントなしrecordのため、コンストラクタは宣言しない（暗黙の正準コンストラクタを使用する）。
 * コンポーネントを持たないため、生成されたインスタンスは常に等価です。
 * </p>
 */
public record Draft() implements Publication {

    @Override
    public boolean isPublished() {
        return false;
    }

    @Override
    public @NonNull Optional<BusinessDateTime> publishedAt() {
        return Optional.empty();
    }

    @Override
    public boolean equivalentTo(Publication other) {
        return other instanceof Draft;
    }
}
