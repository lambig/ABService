package com.abservice.application.service.article;

/**
 * 記事の入力について、値オブジェクトのローカルな field を API の入力パスへ写す（DECISIONS 29）
 *
 * <p>
 * 値オブジェクトは自分にとっての field（{@code MarkupContent} なら {@code content} /
 * {@code format}）を返します。同じ値オブジェクトが記事本文にもアルバムの概要説明にも現れるため、どのパスへ置いたかを
 * 知っている入力の組み立て側で写します。作成と更新が同じパスを使うため、両サービスから引ける位置に置いています。
 * </p>
 */
final class ArticleInputPaths {

    private ArticleInputPaths() {
    }

    /**
     * 本文の入力パスへ写す。
     *
     * @param field
     *            値オブジェクトが返した field
     * @return 形式のエラーは {@code bodyFormat}、それ以外は {@code body}
     */
    static String body(String field) {
        return "format".equals(field)
                ? "bodyFormat"
                : "body";
    }
}
