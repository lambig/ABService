package com.abservice.application.service.asset;

import com.abservice.application.service.CommandService;

/**
 * アップロードURL発行の入力
 *
 * @param contentType
 *            アップロードする画像の Content-Type（受け入れ対象外なら検証エラー）
 */
public record IssueAssetUploadUrlInput(String contentType) implements CommandService.Input {
}
