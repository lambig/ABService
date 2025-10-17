package com.abservice.service;

import com.abservice.dto.CircleMemberDto;
import com.abservice.dto.CreateCircleMemberDto;
import com.abservice.dto.UpdateCircleMemberDto;
import com.abservice.entity.CircleMember;
import com.abservice.entity.Role;
import com.abservice.repository.CircleMemberRepository;
import com.abservice.repository.RoleRepository;
import com.abservice.view.CircleMemberView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CircleMember管理サービス
 */
@ApplicationScoped
public class CircleMemberService {
    
    @Inject
    CircleMemberRepository circleMemberRepository;
    
    @Inject
    RoleRepository roleRepository;
    
    /**
     * すべてのCircleMemberを取得
     */
    public List<CircleMemberDto> findAll() {
        return circleMemberRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * IDでCircleMemberを取得
     */
    public Optional<CircleMemberDto> findById(Long id) {
        return circleMemberRepository.findById(id)
                .map(this::convertToDto);
    }
    
    /**
     * ユーザー名でCircleMemberを取得
     */
    public Optional<CircleMemberDto> findByUsername(String username) {
        return circleMemberRepository.findByUsername(username)
                .map(this::convertToDto);
    }
    
    /**
     * アクティブなCircleMemberのみを取得
     */
    public List<CircleMemberDto> findActiveMembers() {
        return circleMemberRepository.findActiveMembers()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * CircleMemberを作成
     */
    @Transactional
    public CircleMemberDto create(CreateCircleMemberDto createDto) {
        // ユーザー名の重複チェック
        if (circleMemberRepository.existsByUsername(createDto.getUsername())) {
            throw new IllegalArgumentException("ユーザー名は既に使用されています: " + createDto.getUsername());
        }
        
        // メールアドレスの重複チェック
        if (createDto.getEmail() != null && circleMemberRepository.existsByEmail(createDto.getEmail())) {
            throw new IllegalArgumentException("メールアドレスは既に使用されています: " + createDto.getEmail());
        }
        
        // ロールの存在チェック
        Role role = roleRepository.findById(createDto.getRoleId())
                .map(roleView -> {
                    // EntityViewからEntityを取得する必要がある
                    // ここでは簡略化のため、IDで直接取得
                    return null; // 実際の実装では適切にEntityを取得
                })
                .orElseThrow(() -> new NotFoundException("ロールが見つかりません: " + createDto.getRoleId()));
        
        // CircleMemberエンティティを作成
        CircleMember circleMember = new CircleMember();
        circleMember.setUsername(createDto.getUsername());
        circleMember.setDisplayName(createDto.getDisplayName());
        circleMember.setEmail(createDto.getEmail());
        circleMember.setBio(createDto.getBio());
        circleMember.setAvatarUrl(createDto.getAvatarUrl());
        circleMember.setIsActive(true);
        circleMember.setRole(role);
        
        // 保存
        CircleMember savedMember = circleMemberRepository.save(circleMember);
        
        // DTOに変換して返す
        return findById(savedMember.getId())
                .orElseThrow(() -> new RuntimeException("作成したCircleMemberの取得に失敗しました"));
    }
    
    /**
     * CircleMemberを更新
     */
    @Transactional
    public CircleMemberDto update(Long id, UpdateCircleMemberDto updateDto) {
        CircleMember circleMember = circleMemberRepository.findById(id)
                .map(this::getEntityFromView)
                .orElseThrow(() -> new NotFoundException("CircleMemberが見つかりません: " + id));
        
        // 更新可能なフィールドを更新
        if (updateDto.getDisplayName() != null) {
            circleMember.setDisplayName(updateDto.getDisplayName());
        }
        if (updateDto.getEmail() != null) {
            // メールアドレスの重複チェック（自分以外）
            if (circleMemberRepository.existsByEmail(updateDto.getEmail()) && 
                !updateDto.getEmail().equals(circleMember.getEmail())) {
                throw new IllegalArgumentException("メールアドレスは既に使用されています: " + updateDto.getEmail());
            }
            circleMember.setEmail(updateDto.getEmail());
        }
        if (updateDto.getBio() != null) {
            circleMember.setBio(updateDto.getBio());
        }
        if (updateDto.getAvatarUrl() != null) {
            circleMember.setAvatarUrl(updateDto.getAvatarUrl());
        }
        if (updateDto.getIsActive() != null) {
            circleMember.setIsActive(updateDto.getIsActive());
        }
        if (updateDto.getRoleId() != null) {
            Role role = roleRepository.findById(updateDto.getRoleId())
                    .map(this::getRoleEntityFromView)
                    .orElseThrow(() -> new NotFoundException("ロールが見つかりません: " + updateDto.getRoleId()));
            circleMember.setRole(role);
        }
        
        // 保存
        circleMemberRepository.save(circleMember);
        
        // DTOに変換して返す
        return findById(id)
                .orElseThrow(() -> new RuntimeException("更新したCircleMemberの取得に失敗しました"));
    }
    
    /**
     * CircleMemberを削除
     */
    @Transactional
    public void delete(Long id) {
        if (!circleMemberRepository.findById(id).isPresent()) {
            throw new NotFoundException("CircleMemberが見つかりません: " + id);
        }
        circleMemberRepository.deleteById(id);
    }
    
    /**
     * CircleMemberViewをCircleMemberDtoに変換
     */
    private CircleMemberDto convertToDto(CircleMemberView view) {
        return new CircleMemberDto(
                view.getId(),
                view.getUsername(),
                view.getDisplayName(),
                view.getEmail(),
                view.getBio(),
                view.getAvatarUrl(),
                view.getIsActive(),
                view.getCreatedAt(),
                view.getUpdatedAt(),
                view.getRoleName(),
                view.getRoleDescription()
        );
    }
    
    /**
     * CircleMemberViewからCircleMemberエンティティを取得
     * 実際の実装では、適切にEntityを取得する必要がある
     */
    private CircleMember getEntityFromView(CircleMemberView view) {
        // 簡略化のため、IDで直接取得
        // 実際の実装では、適切にEntityを取得する
        return null; // 実装が必要
    }
    
    /**
     * RoleViewからRoleエンティティを取得
     * 実際の実装では、適切にEntityを取得する必要がある
     */
    private Role getRoleEntityFromView(com.abservice.view.RoleView view) {
        // 簡略化のため、IDで直接取得
        // 実際の実装では、適切にEntityを取得する
        return null; // 実装が必要
    }
}
