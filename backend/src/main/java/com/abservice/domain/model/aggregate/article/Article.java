package com.abservice.domain.model.aggregate.article;

import com.abservice.domain.model.CrossAggregateTransition;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 記事集約ルート
 *
 * <p>
 * ブログ記事、アルバム紹介記事、お品書き掲載記事など、「公開情報」そのものを管理する集約です。記事種別ごとに実装を分け、
 * 種別に固有の項目はその種別のクラスだけが持ちます。共通項目を種別のクラスへ nullable で置くことはしません
 * （持たない種別にとって意味のない項目が型の上に現れないようにするため）。
 * </p>
 *
 * <p>
 * 種別によらず共通する状態は {@link ArticleCore} が持ち、共通の変更操作は本インタフェースの既定実装が {@code core}
 * へ委譲したうえで自身の種別へ載せ直します。
 * </p>
 */
public sealed interface Article extends Aggregate<Article, Article.@NonNull Id>
        permits
        AlbumArticle,
        NoteArticle,
        NewsArticle,
        EventArticle,
        OtherArticle {

    /**
     * 種別によらず共通する状態を取得します。
     *
     * @return 共通状態
     */
    @NonNull
    ArticleCore core();

    /**
     * 共通状態を差し替えた同じ種別の記事を返します。
     *
     * <p>
     * 共通の変更操作を1箇所へ集約するためのフックです。種別に固有の項目は保たれます。
     * </p>
     *
     * @param newCore
     *            新しい共通状態
     * @return 共通状態を差し替えた記事
     */
    @NonNull
    Article withCore(@NonNull ArticleCore newCore);

    /**
     * 記事種別を取得します。
     *
     * @return 記事種別
     */
    @NonNull
    ArticleType articleType();

    @Override
    default Article.@NonNull Id id() {
        return core().id();
    }

    /**
     * 記事タイトルを取得します。
     *
     * @return タイトル
     */
    default @NonNull ArticleTitle title() {
        return core().title();
    }

    /**
     * 記事本文を取得します。
     *
     * @return 本文（本文なしは {@code MarkupContent.EMPTY}）
     */
    default @NonNull MarkupContent body() {
        return core().body();
    }

    /**
     * ショート紹介文を取得します。
     *
     * @return ショート紹介文（nullable）
     */
    default @Nullable String introShort() {
        return core().introShort();
    }

    /**
     * 公開日を取得します。
     *
     * @return 公開日（nullable）
     */
    default @Nullable BusinessDateTime publishedAt() {
        return core().publishedAt();
    }

    /**
     * 業務上の更新日を取得します。
     *
     * @return 更新日（nullable）
     */
    default @Nullable BusinessDateTime updatedAtBusiness() {
        return core().updatedAtBusiness();
    }

    /**
     * 公開フラグを取得します。
     *
     * @return 公開フラグ
     */
    default boolean publicFlag() {
        return core().publicFlag();
    }

    /**
     * 公開されているかどうか
     *
     * @return 公開フラグ
     */
    default boolean isPublic() {
        return core().publicFlag();
    }

    /**
     * すべてのタグを取得（不変リスト）
     *
     * @return タグのリスト
     */
    default @NonNull List<ArticleTag> getTags() {
        return core().getTags();
    }

    /**
     * 記事タイトルを変更
     *
     * @param newTitle
     *            新しい記事タイトル
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    default @NonNull Article changeTitle(@NonNull ArticleTitle newTitle, @NonNull BusinessDateTime currentDateTime) {
        return withCore(core().changeTitle(newTitle, currentDateTime));
    }

    /**
     * 記事本文を変更
     *
     * @param newBody
     *            新しい本文（nullable）
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    default @NonNull Article changeBody(@Nullable MarkupContent newBody, @NonNull BusinessDateTime currentDateTime) {
        return withCore(core().changeBody(newBody, currentDateTime));
    }

    /**
     * ショート紹介文を変更
     *
     * @param newIntroShort
     *            新しいショート紹介文（nullable）
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    default @NonNull Article changeIntroShort(@Nullable String newIntroShort,
            @NonNull BusinessDateTime currentDateTime) {
        return withCore(core().changeIntroShort(newIntroShort, currentDateTime));
    }

    /**
     * 記事を公開
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    @CrossAggregateTransition
    default @NonNull Article publish(@NonNull BusinessDateTime currentDateTime) {
        return withCore(core().publish(currentDateTime));
    }

    /**
     * 記事を非公開化
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    default @NonNull Article unpublish(@NonNull BusinessDateTime currentDateTime) {
        return withCore(core().unpublish(currentDateTime));
    }

    /**
     * タグを追加
     *
     * @param tag
     *            追加するタグ
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    default @NonNull Article addTag(@NonNull ArticleTag tag, @NonNull BusinessDateTime currentDateTime) {
        return withCore(core().addTag(tag, currentDateTime));
    }

    /**
     * タグを削除
     *
     * @param tagId
     *            削除するタグのID
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    default @NonNull Article removeTag(ArticleTag.@NonNull Id tagId, @NonNull BusinessDateTime currentDateTime) {
        return withCore(core().removeTag(tagId, currentDateTime));
    }

    /**
     * 記事種別を変更した記事を返します。
     *
     * <p>
     * 種別ごとに実装が分かれるため、種別の変更は同じ記事の別クラスへの載せ替えになります。アルバム参照は {@code ALBUM}
     * を保つ場合のみ引き継ぎ、他の種別へ移るときは落ちます（その種別は参照を持てないため）。
     * </p>
     *
     * @param newArticleType
     *            新しい記事種別
     * @param currentDateTime
     *            現在日時
     * @return 種別を変更した記事
     */
    default @NonNull Article changeArticleType(@NonNull ArticleType newArticleType,
            @NonNull BusinessDateTime currentDateTime) {
        return Article.of(
                newArticleType,
                core().touch(currentDateTime),
                AlbumArticle.from(this)
                        .map(AlbumArticle::albumReference)
                        .orElseGet(AlbumReference::none));
    }

    /**
     * 種別と状態から記事を組み立てます。
     *
     * <p>
     * アルバム参照は {@code ALBUM} 以外の種別では捨てられます（その種別は参照を持てないため）。
     * </p>
     *
     * @param articleType
     *            記事種別
     * @param core
     *            共通状態
     * @param albumReference
     *            アルバム参照
     * @return 記事
     */
    static @NonNull Article of(@Nullable ArticleType articleType, @NonNull ArticleCore core,
            @NonNull AlbumReference albumReference) {
        return switch (requireArticleType(articleType)) {
            case ALBUM -> AlbumArticle.of(core, albumReference);
            case NOTE -> NoteArticle.of(core);
            case NEWS -> NewsArticle.of(core);
            case EVENT -> EventArticle.of(core);
            case OTHER -> OtherArticle.of(core);
        };
    }

    private static @NonNull ArticleType requireArticleType(@Nullable ArticleType articleType) {
        return Policy.<ArticleType>of(
                Objects::nonNull,
                () -> new ErrorResult(
                        "articleType",
                        "Article type cannot be null",
                        "ARTICLE_TYPE_REQUIRED"))
                .verify(articleType, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    /**
     * 新規記事を生成
     *
     * @param articleType
     *            記事種別
     * @param albumId
     *            アルバムID（nullable）
     * @param title
     *            タイトル
     * @param body
     *            本文（nullable）
     * @param introShort
     *            ショート紹介文（nullable）
     * @param currentDateTime
     *            現在日時
     * @return 新規Article
     */
    static @NonNull Article create(@NonNull ArticleType articleType, Album.@Nullable Id albumId,
            @NonNull ArticleTitle title, @Nullable MarkupContent body, @Nullable String introShort,
            @NonNull BusinessDateTime currentDateTime) {
        return Article.of(
                articleType,
                ArticleCore.create(
                        title,
                        body,
                        introShort,
                        currentDateTime),
                AlbumReference.of(albumId));
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            記事ID
     * @param articleType
     *            記事種別
     * @param albumReference
     *            アルバム参照（参照なし・有効な参照・失効した参照のいずれか）
     * @param title
     *            タイトル
     * @param body
     *            本文（nullable）
     * @param introShort
     *            ショート紹介文（nullable）
     * @param publishedAt
     *            公開日（nullable）
     * @param updatedAtBusiness
     *            更新日（nullable）
     * @param publicFlag
     *            公開フラグ
     * @param tags
     *            タグリスト
     * @return 再構成されたArticle
     */
    @DomainFactory
    static @NonNull Article reconstruct(Article.@NonNull Id id, @NonNull ArticleType articleType,
            @NonNull AlbumReference albumReference, @NonNull ArticleTitle title, @Nullable MarkupContent body,
            @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @NonNull List<ArticleTag> tags) {
        return Article.of(
                articleType,
                ArticleCore.reconstruct(
                        id,
                        title,
                        body,
                        introShort,
                        publishedAt,
                        updatedAtBusiness,
                        publicFlag,
                        tags),
                albumReference);
    }

    /**
     * 記事ID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    record Id(@NonNull String value) implements EntityId<Article> {
        /** value空白違反時のエラー */
        private static final ErrorResult ID_BLANK_ERROR = new ErrorResult(
                "value",
                "Article ID cannot be blank",
                "ID_BLANK");

        public Id {
            idPolicy(value)
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        private static Policy<String> idPolicy(@Nullable String value) {
            return Policy.all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            ID_BLANK_ERROR),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult(
                                    "value",
                                    "Article ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成してArticle.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticle.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）からArticle.Idを生成します。
         *
         * <p>
         * 例外をスローせず、検証結果を {@link com.abservice.lib.Result} で返します。 信頼できる内部生成には
         * {@link #of(String)} を使用してください。
         * </p>
         *
         * @param value
         *            ID値を表す文字列
         * @return 成功時は {@code Id}、失敗時はエラー
         */
        public static Result<Id> fromInput(@Nullable String value) {
            return idPolicy(value)
                    .verify(value, Id::new);
        }
    }
}
