package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事削除コマンドの出力DTO
 *
 * <p>
 * 削除はべき等（対象記事の存在有無を問わず成功）なため、返す情報を持ちません。
 * </p>
 */
public record DeleteArticleOutput() implements CommandService.Output {
}
