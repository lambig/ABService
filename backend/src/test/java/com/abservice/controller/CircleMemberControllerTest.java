package com.abservice.controller;

import com.abservice.dto.CircleMemberDto;
import com.abservice.dto.CreateCircleMemberDto;
import com.abservice.dto.UpdateCircleMemberDto;
import com.abservice.service.CircleMemberService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class CircleMemberControllerTest {

    @InjectMock
    CircleMemberService circleMemberService;

    private CircleMemberDto sampleMember;
    private CreateCircleMemberDto createDto;
    private UpdateCircleMemberDto updateDto;

    @BeforeEach
    void setUp() {
        sampleMember = new CircleMemberDto(
                1L,
                "testuser",
                "Test User",
                "test@example.com",
                "Test bio",
                "https://example.com/avatar.jpg",
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "ADMIN",
                "Administrator role"
        );

        createDto = new CreateCircleMemberDto(
                "newuser",
                "New User",
                "new@example.com",
                "New user bio",
                "https://example.com/new-avatar.jpg",
                1L
        );

        updateDto = new UpdateCircleMemberDto(
                "Updated User",
                "updated@example.com",
                "Updated bio",
                "https://example.com/updated-avatar.jpg",
                true,
                2L
        );
    }

    @Test
    void testGetAllCircleMembers() {
        List<CircleMemberDto> members = Arrays.asList(sampleMember);
        when(circleMemberService.findAll()).thenReturn(members);

        given()
                .when()
                .get("/api/v1/circle-members")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].username", equalTo("testuser"))
                .body("[0].displayName", equalTo("Test User"));

        verify(circleMemberService).findAll();
    }

    @Test
    void testGetActiveCircleMembers() {
        List<CircleMemberDto> members = Arrays.asList(sampleMember);
        when(circleMemberService.findActiveMembers()).thenReturn(members);

        given()
                .when()
                .get("/api/v1/circle-members/active")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].isActive", equalTo(true));

        verify(circleMemberService).findActiveMembers();
    }

    @Test
    void testGetCircleMemberById() {
        when(circleMemberService.findById(1L)).thenReturn(Optional.of(sampleMember));

        given()
                .when()
                .get("/api/v1/circle-members/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(1))
                .body("username", equalTo("testuser"));

        verify(circleMemberService).findById(1L);
    }

    @Test
    void testGetCircleMemberByIdNotFound() {
        when(circleMemberService.findById(999L)).thenReturn(Optional.empty());

        given()
                .when()
                .get("/api/v1/circle-members/999")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("CircleMemberが見つかりません"));

        verify(circleMemberService).findById(999L);
    }

    @Test
    void testGetCircleMemberByUsername() {
        when(circleMemberService.findByUsername("testuser")).thenReturn(Optional.of(sampleMember));

        given()
                .when()
                .get("/api/v1/circle-members/username/testuser")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("username", equalTo("testuser"));

        verify(circleMemberService).findByUsername("testuser");
    }

    @Test
    void testCreateCircleMember() {
        when(circleMemberService.create(any(CreateCircleMemberDto.class))).thenReturn(sampleMember);

        given()
                .contentType(ContentType.JSON)
                .body(createDto)
                .when()
                .post("/api/v1/circle-members")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("username", equalTo("testuser"));

        verify(circleMemberService).create(any(CreateCircleMemberDto.class));
    }

    @Test
    void testCreateCircleMemberWithInvalidData() {
        CreateCircleMemberDto invalidDto = new CreateCircleMemberDto();
        // Missing required fields

        when(circleMemberService.create(any(CreateCircleMemberDto.class)))
                .thenThrow(new IllegalArgumentException("ユーザー名は必須です"));

        given()
                .contentType(ContentType.JSON)
                .body(invalidDto)
                .when()
                .post("/api/v1/circle-members")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("ユーザー名は必須です"));

        verify(circleMemberService).create(any(CreateCircleMemberDto.class));
    }

    @Test
    void testUpdateCircleMember() {
        when(circleMemberService.update(eq(1L), any(UpdateCircleMemberDto.class)))
                .thenReturn(sampleMember);

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/api/v1/circle-members/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(1));

        verify(circleMemberService).update(eq(1L), any(UpdateCircleMemberDto.class));
    }

    @Test
    void testUpdateCircleMemberNotFound() {
        when(circleMemberService.update(eq(999L), any(UpdateCircleMemberDto.class)))
                .thenThrow(new jakarta.ws.rs.NotFoundException("CircleMemberが見つかりません: 999"));

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/api/v1/circle-members/999")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("CircleMemberが見つかりません"));

        verify(circleMemberService).update(eq(999L), any(UpdateCircleMemberDto.class));
    }

    @Test
    void testDeleteCircleMember() {
        doNothing().when(circleMemberService).delete(1L);

        given()
                .when()
                .delete("/api/v1/circle-members/1")
                .then()
                .statusCode(204);

        verify(circleMemberService).delete(1L);
    }

    @Test
    void testDeleteCircleMemberNotFound() {
        doThrow(new jakarta.ws.rs.NotFoundException("CircleMemberが見つかりません: 999"))
                .when(circleMemberService).delete(999L);

        given()
                .when()
                .delete("/api/v1/circle-members/999")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("CircleMemberが見つかりません"));

        verify(circleMemberService).delete(999L);
    }
}
