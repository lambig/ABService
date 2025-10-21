package com.abservice.controller;

import com.abservice.dto.CircleMemberDto;
import com.abservice.dto.CreateCircleMemberDto;
import com.abservice.dto.UpdateCircleMemberDto;
import com.abservice.service.CircleMemberServiceInterface;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * CircleMemberControllerの統合テスト
 * テスト用のサービス実装を使用してエンドツーエンドテストを実行
 */
@QuarkusTest
class CircleMemberControllerTest {

    /**
     * テスト用のCircleMemberService実装
     */
    @ApplicationScoped
    public static class TestCircleMemberService implements CircleMemberServiceInterface {

        private final List<CircleMemberDto> members = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        public TestCircleMemberService() {
            // テスト用の初期データ
            CircleMemberDto member1 = new CircleMemberDto();
            member1.setId(1L);
            member1.setUsername("testuser1");
            member1.setDisplayName("Test User 1");
            member1.setEmail("test1@example.com");
            member1.setBio("Test bio 1");
            member1.setAvatarUrl("https://example.com/avatar1.jpg");
            member1.setIsActive(true);
            member1.setCreatedAt(LocalDateTime.now());
            member1.setUpdatedAt(LocalDateTime.now());
            member1.setRoleName("ADMIN");
            member1.setRoleDescription("Administrator role");
            members.add(member1);

            CircleMemberDto member2 = new CircleMemberDto();
            member2.setId(2L);
            member2.setUsername("testuser2");
            member2.setDisplayName("Test User 2");
            member2.setEmail("test2@example.com");
            member2.setBio("Test bio 2");
            member2.setAvatarUrl("https://example.com/avatar2.jpg");
            member2.setIsActive(false);
            member2.setCreatedAt(LocalDateTime.now());
            member2.setUpdatedAt(LocalDateTime.now());
            member2.setRoleName("USER");
            member2.setRoleDescription("User role");
            members.add(member2);
        }

        @Override
        public List<CircleMemberDto> findAll() {
            return new ArrayList<>(members);
        }

        @Override
        public List<CircleMemberDto> findActiveMembers() {
            return members.stream()
                    .filter(CircleMemberDto::getIsActive)
                    .toList();
        }

        @Override
        public Optional<CircleMemberDto> findById(Long id) {
            return members.stream()
                    .filter(member -> member.getId().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<CircleMemberDto> findByUsername(String username) {
            return members.stream()
                    .filter(member -> member.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public CircleMemberDto create(CreateCircleMemberDto createDto) {
            // ユーザー名の重複チェック
            if (members.stream().anyMatch(member -> member.getUsername().equals(createDto.getUsername()))) {
                throw new IllegalArgumentException("ユーザー名は既に使用されています: " + createDto.getUsername());
            }

            // メールアドレスの重複チェック
            if (createDto.getEmail() != null &&
                members.stream().anyMatch(member -> createDto.getEmail().equals(member.getEmail()))) {
                throw new IllegalArgumentException("メールアドレスは既に使用されています: " + createDto.getEmail());
            }

            CircleMemberDto newMember = new CircleMemberDto();
            newMember.setId(idGenerator.getAndIncrement());
            newMember.setUsername(createDto.getUsername());
            newMember.setDisplayName(createDto.getDisplayName());
            newMember.setEmail(createDto.getEmail());
            newMember.setBio(createDto.getBio());
            newMember.setAvatarUrl(createDto.getAvatarUrl());
            newMember.setIsActive(createDto.getIsActive() != null ? createDto.getIsActive() : true);
            newMember.setCreatedAt(LocalDateTime.now());
            newMember.setUpdatedAt(LocalDateTime.now());
            newMember.setRoleName("USER"); // デフォルトロール
            newMember.setRoleDescription("User role");

            members.add(newMember);
            return newMember;
        }

        @Override
        public CircleMemberDto update(Long id, UpdateCircleMemberDto updateDto) {
            CircleMemberDto existingMember = findById(id)
                    .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("CircleMemberが見つかりません: " + id));

            // メールアドレスの重複チェック（自分以外）
            if (updateDto.getEmail() != null &&
                members.stream().anyMatch(member ->
                    !member.getId().equals(id) && updateDto.getEmail().equals(member.getEmail()))) {
                throw new IllegalArgumentException("メールアドレスは既に使用されています: " + updateDto.getEmail());
            }

            if (updateDto.getDisplayName() != null) {
                existingMember.setDisplayName(updateDto.getDisplayName());
            }
            if (updateDto.getEmail() != null) {
                existingMember.setEmail(updateDto.getEmail());
            }
            if (updateDto.getBio() != null) {
                existingMember.setBio(updateDto.getBio());
            }
            if (updateDto.getAvatarUrl() != null) {
                existingMember.setAvatarUrl(updateDto.getAvatarUrl());
            }
            if (updateDto.getIsActive() != null) {
                existingMember.setIsActive(updateDto.getIsActive());
            }
            existingMember.setUpdatedAt(LocalDateTime.now());

            return existingMember;
        }

        @Override
        public void delete(Long id) {
            boolean removed = members.removeIf(member -> member.getId().equals(id));
            if (!removed) {
                throw new jakarta.ws.rs.NotFoundException("CircleMemberが見つかりません: " + id);
            }
        }
    }

    /**
     * テスト用のサービス実装を注入
     */
    @Produces
    @ApplicationScoped
    public CircleMemberServiceInterface circleMemberService() {
        return new TestCircleMemberService();
    }

    private CircleMemberDto sampleMember;
    private CreateCircleMemberDto createDto;
    private UpdateCircleMemberDto updateDto;

    @BeforeEach
    void setUp() {
        // サンプルデータの準備
        sampleMember = new CircleMemberDto();
        sampleMember.setId(1L);
        sampleMember.setUsername("testuser");
        sampleMember.setDisplayName("Test User");
        sampleMember.setEmail("test@example.com");
        sampleMember.setBio("Test bio");
        sampleMember.setAvatarUrl("https://example.com/avatar.jpg");
        sampleMember.setIsActive(true);
        sampleMember.setCreatedAt(LocalDateTime.now());
        sampleMember.setUpdatedAt(LocalDateTime.now());
        sampleMember.setRoleName("ADMIN");
        sampleMember.setRoleDescription("Administrator role");

        createDto = new CreateCircleMemberDto();
        createDto.setUsername("newuser");
        createDto.setDisplayName("New User");
        createDto.setEmail("new@example.com");
        createDto.setBio("New user bio");
        createDto.setAvatarUrl("https://example.com/new-avatar.jpg");
        createDto.setIsActive(true);

        updateDto = new UpdateCircleMemberDto();
        updateDto.setDisplayName("Updated User");
        updateDto.setEmail("updated@example.com");
        updateDto.setBio("Updated bio");
        updateDto.setAvatarUrl("https://example.com/updated-avatar.jpg");
        updateDto.setIsActive(false);
    }

    @Test
    void testGetAllCircleMembers() {
        given()
                .when()
                .get("/api/circle-members")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0))
                .body("[0].username", notNullValue())
                .body("[0].displayName", notNullValue());
    }

    @Test
    void testGetActiveCircleMembers() {
        given()
                .when()
                .get("/api/circle-members/active")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0))
                .body("[0].isActive", equalTo(true));
    }

    @Test
    void testGetCircleMemberById() {
        given()
                .when()
                .get("/api/circle-members/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(1))
                .body("username", notNullValue())
                .body("displayName", notNullValue());
    }

    @Test
    void testGetCircleMemberByIdNotFound() {
        given()
                .when()
                .get("/api/circle-members/999")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("CircleMemberが見つかりません"));
    }

    @Test
    void testGetCircleMemberByUsername() {
        given()
                .when()
                .get("/api/circle-members/username/testuser1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("username", equalTo("testuser1"))
                .body("displayName", notNullValue());
    }

    @Test
    void testCreateCircleMember() {
        given()
                .contentType(ContentType.JSON)
                .body(createDto)
                .when()
                .post("/api/circle-members")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("username", equalTo("newuser"))
                .body("displayName", equalTo("New User"))
                .body("email", equalTo("new@example.com"))
                .body("id", notNullValue());
    }

    @Test
    void testCreateCircleMemberWithInvalidData() {
        CreateCircleMemberDto invalidDto = new CreateCircleMemberDto();
        // Missing required fields

        given()
                .contentType(ContentType.JSON)
                .body(invalidDto)
                .when()
                .post("/api/circle-members")
                .then()
                .statusCode(400);
    }

    @Test
    void testUpdateCircleMember() {
        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/api/circle-members/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("displayName", equalTo("Updated User"))
                .body("email", equalTo("updated@example.com"))
                .body("isActive", equalTo(false));
    }

    @Test
    void testUpdateCircleMemberNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/api/circle-members/999")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("CircleMemberが見つかりません"));
    }

    @Test
    void testDeleteCircleMember() {
        given()
                .when()
                .delete("/api/circle-members/1")
                .then()
                .statusCode(204);
    }

    @Test
    void testDeleteCircleMemberNotFound() {
        given()
                .when()
                .delete("/api/circle-members/999")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("CircleMemberが見つかりません"));
    }
}
