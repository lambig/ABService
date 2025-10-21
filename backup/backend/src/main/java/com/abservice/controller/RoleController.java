package com.abservice.controller;

import com.abservice.dto.RoleDto;
import com.abservice.service.RoleServiceInterface;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;

/**
 * Role管理用REST APIコントローラー
 */
@Path("/api/v1/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Role", description = "ロール管理API")
public class RoleController {

    @Inject
    RoleServiceInterface roleService;

    /**
     * すべてのRoleを取得
     */
    @GET
    @Operation(summary = "Role一覧取得", description = "すべてのロールを取得します")
    @APIResponse(responseCode = "200", description = "取得成功",
                 content = @Content(schema = @Schema(implementation = RoleDto.class)))
    public Response getAllRoles() {
        List<RoleDto> roles = roleService.findAll();
        return Response.ok(roles).build();
    }

    /**
     * IDでRoleを取得
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Role取得", description = "指定されたIDのロールを取得します")
    @APIResponse(responseCode = "200", description = "取得成功",
                 content = @Content(schema = @Schema(implementation = RoleDto.class)))
    @APIResponse(responseCode = "404", description = "Roleが見つかりません")
    public Response getRole(@PathParam("id") Long id) {
        Optional<RoleDto> role = roleService.findById(id);
        if (role.isPresent()) {
            return Response.ok(role.get()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Roleが見つかりません: " + id + "\"}")
                    .build();
        }
    }

    /**
     * 名前でRoleを取得
     */
    @GET
    @Path("/name/{name}")
    @Operation(summary = "Role取得（名前）", description = "指定された名前のロールを取得します")
    @APIResponse(responseCode = "200", description = "取得成功",
                 content = @Content(schema = @Schema(implementation = RoleDto.class)))
    @APIResponse(responseCode = "404", description = "Roleが見つかりません")
    public Response getRoleByName(@PathParam("name") String name) {
        Optional<RoleDto> role = roleService.findByName(name);
        if (role.isPresent()) {
            return Response.ok(role.get()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Roleが見つかりません: " + name + "\"}")
                    .build();
        }
    }

    /**
     * Roleを作成
     */
    @POST
    @Operation(summary = "Role作成", description = "新しいロールを作成します")
    @APIResponse(responseCode = "201", description = "作成成功",
                 content = @Content(schema = @Schema(implementation = RoleDto.class)))
    @APIResponse(responseCode = "400", description = "リクエストが不正です")
    public Response createRole(@Valid RoleDto roleDto) {
        try {
            RoleDto createdRole = roleService.create(roleDto);
            return Response.status(Response.Status.CREATED).entity(createdRole).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Roleの作成に失敗しました: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Roleを更新
     */
    @PUT
    @Path("/{id}")
    @Operation(summary = "Role更新", description = "指定されたIDのロールを更新します")
    @APIResponse(responseCode = "200", description = "更新成功",
                 content = @Content(schema = @Schema(implementation = RoleDto.class)))
    @APIResponse(responseCode = "400", description = "リクエストが不正です")
    @APIResponse(responseCode = "404", description = "Roleが見つかりません")
    public Response updateRole(@PathParam("id") Long id, @Valid RoleDto roleDto) {
        try {
            RoleDto updatedRole = roleService.update(id, roleDto);
            return Response.ok(updatedRole).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Roleの更新に失敗しました: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Roleを削除
     */
    @DELETE
    @Path("/{id}")
    @Operation(summary = "Role削除", description = "指定されたIDのロールを削除します")
    @APIResponse(responseCode = "204", description = "削除成功")
    @APIResponse(responseCode = "404", description = "Roleが見つかりません")
    public Response deleteRole(@PathParam("id") Long id) {
        try {
            roleService.delete(id);
            return Response.noContent().build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Roleの削除に失敗しました: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
