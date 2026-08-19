package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * アルバムの公開情報を表す値オブジェクト（Null Objectパターン）
 *
 * <p>
 * {@link com.abservice.domain.model.aggregate.album.Album}の公開状態そのものを表現します。
 * {@code Album}側のフィールドは常に非nullの{@code Publication}を保持し（{@code null}を公開状態の意味に
 * 使いません）、未公開は{@link Draft}、公開中は{@link Published}という2つの具体型で区別します。
 * 呼び出し側は{@link #isPublished()}／{@link #publishedAt()}で安全に問い合わせるだけでよく、
 * 「nullを取得してしまう・nullチェックを忘れる」余地自体がありません。
 * </p>
 */
public sealed interface Publication extends ValueObject<Publication> permits Draft, Published {

    /**
     * 公開中かどうか
     *
     * @return 公開中の場合true
     */
    boolean isPublished();

    /**
     * 公開日時を取得
     *
     * @return 公開中の場合は公開日時、未公開の場合は空
     */
    @NonNull
    Optional<BusinessDateTime> publishedAt();

    /**
     * 下書き（未公開）状態を取得
     *
     * @return Draftインスタンス
     */
    static @NonNull Publication draft() {
        return new Draft();
    }

    /**
     * 公開中状態を生成
     *
     * @param publishedAt
     *            公開日時（non-null）
     * @return Publishedインスタンス
     */
    static @NonNull Publication published(@NonNull BusinessDateTime publishedAt) {
        return new Published(publishedAt);
    }
}
