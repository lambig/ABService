package com.abservice.service;

import com.abservice.dto.RoleDto;
import com.abservice.entity.Role;
import com.abservice.repository.RoleRepository;
import com.abservice.view.RoleView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Role管理サービス
 */
@ApplicationScoped
public class RoleService implements RoleServiceInterface {

    @Inject
    RoleRepository roleRepository;

    /**
     * すべてのRoleを取得
     */
    public List<RoleDto> findAll() {
        return roleRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * IDでRoleを取得
     */
    public Optional<RoleDto> findById(Long id) {
        return roleRepository.findById(id)
                .map(this::convertToDto);
    }

    /**
     * 名前でRoleを取得
     */
    public Optional<RoleDto> findByName(String name) {
        return roleRepository.findByName(name)
                .map(this::convertToDto);
    }

    /**
     * Roleを作成
     */
    @Transactional
    public RoleDto create(RoleDto roleDto) {
        // 名前の重複チェック
        if (roleRepository.existsByName(roleDto.getName())) {
            throw new IllegalArgumentException("ロール名は既に使用されています: " + roleDto.getName());
        }

        // Roleエンティティを作成
        Role role = new Role(roleDto.getName(), roleDto.getDescription());

        // 保存
        Role savedRole = roleRepository.save(role);

        // DTOに変換して返す
        return findById(savedRole.getId())
                .orElseThrow(() -> new RuntimeException("作成したRoleの取得に失敗しました"));
    }

    /**
     * Roleを更新
     */
    @Transactional
    public RoleDto update(Long id, RoleDto roleDto) {
        Role role = roleRepository.findById(id)
                .map(this::getEntityFromView)
                .orElseThrow(() -> new NotFoundException("Roleが見つかりません: " + id));

        // 名前の重複チェック（自分以外）
        if (roleRepository.existsByName(roleDto.getName()) &&
            !roleDto.getName().equals(role.getName())) {
            throw new IllegalArgumentException("ロール名は既に使用されています: " + roleDto.getName());
        }

        // 更新可能なフィールドを更新
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());

        // 保存
        roleRepository.save(role);

        // DTOに変換して返す
        return findById(id)
                .orElseThrow(() -> new RuntimeException("更新したRoleの取得に失敗しました"));
    }

    /**
     * Roleを削除
     */
    @Transactional
    public void delete(Long id) {
        if (!roleRepository.findById(id).isPresent()) {
            throw new NotFoundException("Roleが見つかりません: " + id);
        }
        roleRepository.deleteById(id);
    }

    /**
     * RoleViewをRoleDtoに変換
     */
    private RoleDto convertToDto(RoleView view) {
        return new RoleDto(
                view.getId(),
                view.getName(),
                view.getDescription(),
                view.getCreatedAt(),
                view.getUpdatedAt()
        );
    }

    /**
     * RoleViewからRoleエンティティを取得
     * 実際の実装では、適切にEntityを取得する必要がある
     */
    private Role getEntityFromView(RoleView view) {
        // 簡略化のため、IDで直接取得
        // 実際の実装では、適切にEntityを取得する
        return null; // 実装が必要
    }
}
