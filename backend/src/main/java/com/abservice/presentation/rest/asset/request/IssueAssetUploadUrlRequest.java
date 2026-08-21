package com.abservice.presentation.rest.asset.request;

/**
 * アップロードURL発行リクエスト
 *
 * @param contentType
 *            アップロードする画像の Content-Type（{@code image/jpeg} 等）
 */
public record IssueAssetUploadUrlRequest(String contentType) {
}
