package com.abservice.application.service.asset;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;

/**
 * アップロードURL発行の入力
 *
 * @param contentType
 *            アップロードする画像の Content-Type（受け入れ対象外なら検証エラー）
 */
public record IssueAssetUploadUrlInput(
        String contentType) implements CommandService.Input<IssueAssetUploadUrlInput> {
    @Override
    public Policy<IssueAssetUploadUrlInput> policy() {
        return new InputPolicy<>(IssueAssetUploadUrlInput::format);
    }

    Result<AssetImageFormat> format() {
        return AssetImageFormat.ofContentType(contentType())
                .<Result<AssetImageFormat>>map(Result::success)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "contentType",
                                        "対応していない形式です: " + contentType() + "（対応: "
                                                + AssetImageFormat.acceptedContentTypes() + "）",
                                        "UNSUPPORTED_CONTENT_TYPE")));
    }
}
