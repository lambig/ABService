package com.abservice.application.query;

/** 取得済み件数からページ数を算出し、Panacheへの追加COUNTを避ける。 */
public final class PageCounts {

    private PageCounts() {
    }

    /**
     * 0件でも1ページとする既存のPanache契約を維持する。
     *
     * @param count 取得済みの非負の総件数
     * @param size クランプ済みの正のページサイズ
     * @return 総ページ数
     */
    public static int totalPages(long count, int size) {
        return Math.toIntExact(Math.max(1L, Math.ceilDiv(count, size)));
    }
}
