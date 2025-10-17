package com.abservice.controller;

import com.abservice.dto.CircleMemberDto;
import com.abservice.dto.CreateCircleMemberDto;
import com.abservice.dto.UpdateCircleMemberDto;
import com.abservice.service.CircleMemberService;
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
 * CircleMember管理用REST APIコントローラー
 */
@Path("/api/v1/circle-members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "CircleMember", description = "サークルメンバー管理API")
public class CircleMemberController {
    
    @Inject
    CircleMemberService circleMemberService;
    
    /**
     * すべてのCircleMemberを取得
     */
    @GET
    @Operation(summary = "CircleMember一覧取得", description = "すべてのサークルメンバーを取得します")
    @APIResponse(responseCode = "200", description = "取得成功", 
                 content = @Content(schema = @Schema(implementation = CircleMemberDto.class)))
    public Response getAllCircleMembers() {
        List<CircleMemberDto> members = circleMemberService.findAll();
        return Response.ok(members).build();
    }
    
    /**
     * アクティブなCircleMemberのみを取得
     */
    @GET
    @Path("/active")
    @Operation(summary = "アクティブCircleMember一覧取得", description = "アクティブなサークルメンバーのみを取得します")
    @APIResponse(responseCode = "200", description = "取得成功", 
                 content = @Content(schema = @Schema(implementation = CircleMemberDto.class)))
    public Response getActiveCircleMembers() {
        List<CircleMemberDto> members = circleMemberService.findActiveMembers();
        return Response.ok(members).build();
    }
    
    /**
     * IDでCircleMemberを取得
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "CircleMember取得", description = "指定されたIDのサークルメンバーを取得します")
    @APIResponse(responseCode = "200", description = "取得成功", 
                 content = @Content(schema = @Schema(implementation = CircleMemberDto.class)))
    @APIResponse(responseCode = "404", description = "CircleMemberが見つかりません")
    public Response getCircleMember(@PathParam("id") Long id) {
        Optional<CircleMemberDto> member = circleMemberService.findById(id);
        if (member.isPresent()) {
            return Response.ok(member.get()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"CircleMemberが見つかりません: " + id + "\"}")
                    .build();
        }
    }
    
    /**
     * ユーザー名でCircleMemberを取得
     */
    @GET
    @Path("/username/{username}")
    @Operation(summary = "CircleMember取得（ユーザー名）", description = "指定されたユーザー名のサークルメンバーを取得します")
    @APIResponse(responseCode = "200", description = "取得成功", 
                 content = @Content(schema = @Schema(implementation = CircleMemberDto.class)))
    @APIResponse(responseCode = "404", description = "CircleMemberが見つかりません")
    public Response getCircleMemberByUsername(@PathParam("username") String username) {
        Optional<CircleMemberDto> member = circleMemberService.findByUsername(username);
        if (member.isPresent()) {
            return Response.ok(member.get()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"CircleMemberが見つかりません: " + username + "\"}")
                    .build();
        }
    }
    
    /**
     * CircleMemberを作成
     */
    @POST
    @Operation(summary = "CircleMember作成", description = "新しいサークルメンバーを作成します")
    @APIResponse(responseCode = "201", description = "作成成功", 
                 content = @Content(schema = @Schema(implementation = CircleMemberDto.class)))
    @APIResponse(responseCode = "400", description = "リクエストが不正です")
    public Response createCircleMember(@Valid CreateCircleMemberDto createDto) {
        try {
            CircleMemberDto createdMember = circleMemberService.create(createDto);
            return Response.status(Response.Status.CREATED).entity(createdMember).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"CircleMemberの作成に失敗しました: " + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * CircleMemberを更新
     */
    @PUT
    @Path("/{id}")
    @Operation(summary = "CircleMember更新", description = "指定されたIDのサークルメンバーを更新します")
    @APIResponse(responseCode = "200", description = "更新成功", 
                 content = @Content(schema = @Schema(implementation = CircleMemberDto.class)))
    @APIResponse(responseCode = "400", description = "リクエストが不正です")
    @APIResponse(responseCode = "404", description = "CircleMemberが見つかりません")
    public Response updateCircleMember(@PathParam("id") Long id, @Valid UpdateCircleMemberDto updateDto) {
        try {
            CircleMemberDto updatedMember = circleMemberService.update(id, updateDto);
            return Response.ok(updatedMember).build();
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
                    .entity("{\"error\": \"CircleMemberの更新に失敗しました: " + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * CircleMemberを削除
     */
    @DELETE
    @Path("/{id}")
    @Operation(summary = "CircleMember削除", description = "指定されたIDのサークルメンバーを削除します")
    @APIResponse(responseCode = "204", description = "削除成功")
    @APIResponse(responseCode = "404", description = "CircleMemberが見つかりません")
    public Response deleteCircleMember(@PathParam("id") Long id) {
        try {
            circleMemberService.delete(id);
            return Response.noContent().build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"CircleMemberの削除に失敗しました: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
