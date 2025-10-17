package com.abservice.service;

import com.abservice.dto.CircleMemberDto;
import com.abservice.dto.CreateCircleMemberDto;
import com.abservice.dto.UpdateCircleMemberDto;
import com.abservice.entity.CircleMember;
import com.abservice.entity.Role;
import com.abservice.repository.CircleMemberRepository;
import com.abservice.repository.RoleRepository;
import com.abservice.view.CircleMemberView;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class CircleMemberServiceTest {

    @InjectMock
    CircleMemberRepository circleMemberRepository;

    @InjectMock
    RoleRepository roleRepository;

    @InjectMock
    CircleMemberService circleMemberService;

    private CircleMemberView sampleMemberView;
    private CreateCircleMemberDto createDto;
    private UpdateCircleMemberDto updateDto;
    private Role sampleRole;

    @BeforeEach
    void setUp() {
        sampleMemberView = Mockito.mock(CircleMemberView.class);
        when(sampleMemberView.getId()).thenReturn(1L);
        when(sampleMemberView.getUsername()).thenReturn("testuser");
        when(sampleMemberView.getDisplayName()).thenReturn("Test User");
        when(sampleMemberView.getEmail()).thenReturn("test@example.com");
        when(sampleMemberView.getBio()).thenReturn("Test bio");
        when(sampleMemberView.getAvatarUrl()).thenReturn("https://example.com/avatar.jpg");
        when(sampleMemberView.getIsActive()).thenReturn(true);
        when(sampleMemberView.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(sampleMemberView.getUpdatedAt()).thenReturn(LocalDateTime.now());
        when(sampleMemberView.getRoleName()).thenReturn("ADMIN");
        when(sampleMemberView.getRoleDescription()).thenReturn("Administrator role");

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

        sampleRole = new Role("ADMIN", "Administrator role");
        sampleRole.setId(1L);
    }

    @Test
    void testFindAll() {
        List<CircleMemberView> memberViews = Arrays.asList(sampleMemberView);
        when(circleMemberRepository.findAll()).thenReturn(memberViews);

        List<CircleMemberDto> result = circleMemberService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("testuser", result.get(0).getUsername());
        assertEquals("Test User", result.get(0).getDisplayName());

        verify(circleMemberRepository).findAll();
    }

    @Test
    void testFindById() {
        when(circleMemberRepository.findById(1L)).thenReturn(Optional.of(sampleMemberView));

        Optional<CircleMemberDto> result = circleMemberService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        assertEquals("Test User", result.get().getDisplayName());

        verify(circleMemberRepository).findById(1L);
    }

    @Test
    void testFindByIdNotFound() {
        when(circleMemberRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<CircleMemberDto> result = circleMemberService.findById(999L);

        assertFalse(result.isPresent());

        verify(circleMemberRepository).findById(999L);
    }

    @Test
    void testFindByUsername() {
        when(circleMemberRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleMemberView));

        Optional<CircleMemberDto> result = circleMemberService.findByUsername("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());

        verify(circleMemberRepository).findByUsername("testuser");
    }

    @Test
    void testFindActiveMembers() {
        List<CircleMemberView> memberViews = Arrays.asList(sampleMemberView);
        when(circleMemberRepository.findActiveMembers()).thenReturn(memberViews);

        List<CircleMemberDto> result = circleMemberService.findActiveMembers();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());

        verify(circleMemberRepository).findActiveMembers();
    }

    @Test
    void testCreateCircleMember() {
        when(circleMemberRepository.existsByUsername("newuser")).thenReturn(false);
        when(circleMemberRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Mockito.mock(com.abservice.view.RoleView.class)));
        when(circleMemberRepository.save(any(CircleMember.class))).thenReturn(new CircleMember());
        when(circleMemberRepository.findById(anyLong())).thenReturn(Optional.of(sampleMemberView));

        CircleMemberDto result = circleMemberService.create(createDto);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());

        verify(circleMemberRepository).existsByUsername("newuser");
        verify(circleMemberRepository).existsByEmail("new@example.com");
        verify(roleRepository).findById(1L);
        verify(circleMemberRepository).save(any(CircleMember.class));
    }

    @Test
    void testCreateCircleMemberWithDuplicateUsername() {
        when(circleMemberRepository.existsByUsername("newuser")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            circleMemberService.create(createDto);
        });

        verify(circleMemberRepository).existsByUsername("newuser");
        verify(circleMemberRepository, never()).save(any(CircleMember.class));
    }

    @Test
    void testCreateCircleMemberWithDuplicateEmail() {
        when(circleMemberRepository.existsByUsername("newuser")).thenReturn(false);
        when(circleMemberRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            circleMemberService.create(createDto);
        });

        verify(circleMemberRepository).existsByUsername("newuser");
        verify(circleMemberRepository).existsByEmail("new@example.com");
        verify(circleMemberRepository, never()).save(any(CircleMember.class));
    }

    @Test
    void testUpdateCircleMember() {
        CircleMember existingMember = new CircleMember();
        existingMember.setId(1L);
        existingMember.setUsername("testuser");
        existingMember.setEmail("old@example.com");

        when(circleMemberRepository.findById(1L)).thenReturn(Optional.of(sampleMemberView));
        when(circleMemberRepository.existsByEmail("updated@example.com")).thenReturn(false);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(Mockito.mock(com.abservice.view.RoleView.class)));
        when(circleMemberRepository.save(any(CircleMember.class))).thenReturn(existingMember);

        CircleMemberDto result = circleMemberService.update(1L, updateDto);

        assertNotNull(result);

        verify(circleMemberRepository).findById(1L);
        verify(circleMemberRepository).existsByEmail("updated@example.com");
        verify(roleRepository).findById(2L);
        verify(circleMemberRepository).save(any(CircleMember.class));
    }

    @Test
    void testUpdateCircleMemberNotFound() {
        when(circleMemberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            circleMemberService.update(999L, updateDto);
        });

        verify(circleMemberRepository).findById(999L);
        verify(circleMemberRepository, never()).save(any(CircleMember.class));
    }

    @Test
    void testDeleteCircleMember() {
        when(circleMemberRepository.findById(1L)).thenReturn(Optional.of(sampleMemberView));
        doNothing().when(circleMemberRepository).deleteById(1L);

        assertDoesNotThrow(() -> {
            circleMemberService.delete(1L);
        });

        verify(circleMemberRepository).findById(1L);
        verify(circleMemberRepository).deleteById(1L);
    }

    @Test
    void testDeleteCircleMemberNotFound() {
        when(circleMemberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            circleMemberService.delete(999L);
        });

        verify(circleMemberRepository).findById(999L);
        verify(circleMemberRepository, never()).deleteById(anyLong());
    }
}
