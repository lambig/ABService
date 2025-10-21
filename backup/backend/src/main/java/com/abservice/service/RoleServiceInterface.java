package com.abservice.service;

import com.abservice.dto.RoleDto;

import java.util.List;
import java.util.Optional;

/**
 * Roleサービスのインターフェース
 */
public interface RoleServiceInterface {

    /**
     * 全てのRoleを取得
     */
    List<RoleDto> findAll();

    /**
     * IDでRoleを取得
     */
    Optional<RoleDto> findById(Long id);

    /**
     * 名前でRoleを取得
     */
    Optional<RoleDto> findByName(String name);

    /**
     * Roleを作成
     */
    RoleDto create(RoleDto roleDto);

    /**
     * Roleを更新
     */
    RoleDto update(Long id, RoleDto roleDto);

    /**
     * Roleを削除
     */
    void delete(Long id);
}
