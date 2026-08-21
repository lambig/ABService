package com.abservice.presentation.rest.asset;

import com.abservice.application.service.asset.ConfirmAssetUploadInput;
import com.abservice.application.service.asset.ConfirmAssetUploadOutput;
import com.abservice.application.service.asset.ConfirmAssetUploadService;
import com.abservice.application.service.asset.IssueAssetUploadUrlInput;
import com.abservice.application.service.asset.IssueAssetUploadUrlOutput;
import com.abservice.application.service.asset.IssueAssetUploadUrlService;
import com.abservice.presentation.rest.asset.request.IssueAssetUploadUrlRequest;
import com.abservice.presentation.rest.asset.response.AssetUploadUrlResponse;
import com.abservice.presentation.rest.asset.response.ConfirmAssetUploadResponse;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * アセット（画像）アップロードの Command REST リソース
 *
 * <p>
 * アップロードURLの発行（POST）と、アップロード完了後の確定（POST .../confirm）を受け付ける。実体は発行した署名付きURL
 * へクライアントから直接送られるため、本リソースはファイル本体を受け取らない。確定時に実体を検査し、サイズ超過・形式不一致の
 * 実体は保管先から削除して検証エラー（400）にする。検証・保管は アプリケーション層に委譲し、検証失敗・対象不在は
 * {@code DomainException} 経由で {@code DomainExceptionMapper} が RFC 9457 Problem
 * Details に変換する。全操作は管理者ロール（{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/assets")
@RolesAllowed(SecurityRoles.ADMIN)
public class AssetCommandResource {

    private final IssueAssetUploadUrlService issueAssetUploadUrlService;
    private final ConfirmAssetUploadService confirmAssetUploadService;

    /**
     * @param issueAssetUploadUrlService
     *            アップロードURL発行ユースケース
     * @param confirmAssetUploadService
     *            アップロード確定ユースケース
     */
    public AssetCommandResource(
            IssueAssetUploadUrlService issueAssetUploadUrlService,
            ConfirmAssetUploadService confirmAssetUploadService) {
        this.issueAssetUploadUrlService = issueAssetUploadUrlService;
        this.confirmAssetUploadService = confirmAssetUploadService;
    }

    /**
     * アップロード用の署名付きURLを発行します。
     *
     * @param request
     *            発行リクエスト
     * @return 200 とアップロード先URL、対応していない形式は 400 の Problem Details
     */
    @POST
    @Path("/upload-url")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> issueUploadUrl(IssueAssetUploadUrlRequest request) {
        return issueAssetUploadUrlService.execute(new IssueAssetUploadUrlInput(request.contentType()))
                .map(AssetCommandResource::toUploadUrlResponse);
    }

    private static Response toUploadUrlResponse(IssueAssetUploadUrlOutput output) {
        return Response.ok(
                new AssetUploadUrlResponse(
                        output.assetKey(),
                        output.uploadUrl(),
                        output.expiresAt(),
                        output.maxBytes()))
                .build();
    }

    /**
     * アップロード済みの実体を検査して確定します。
     *
     * @param assetKey
     *            発行時に払い出されたアセットキー
     * @return 200 と公開配信URL、実体が無ければ 404、検査に通らなければ 400 の Problem Details
     */
    @POST
    @Path("/{assetKey}/confirm")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> confirm(@PathParam("assetKey") String assetKey) {
        return confirmAssetUploadService.execute(new ConfirmAssetUploadInput(assetKey))
                .map(AssetCommandResource::toConfirmResponse);
    }

    private static Response toConfirmResponse(ConfirmAssetUploadOutput output) {
        return Response.ok(
                new ConfirmAssetUploadResponse(
                        output.assetKey(),
                        output.url(),
                        output.contentType(),
                        output.sizeBytes()))
                .build();
    }
}
