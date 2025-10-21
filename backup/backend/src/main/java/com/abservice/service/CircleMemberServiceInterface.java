package com.abservice.service;

import com.abservice.dto.CircleMemberDto;
import com.abservice.dto.CreateCircleMemberDto;
import com.abservice.dto.UpdateCircleMemberDto;

import java.util.List;
import java.util.Optional;

/**
 * CircleMemberサービスのインターフェース
 */
public interface CircleMemberServiceInterface {

    /**
     * 全てのCircleMemberを取得
     */
    List<CircleMemberDto> findAll();

    /**
     * アクティブなCircleMemberを取得
     */
    List<CircleMemberDto> findActiveMembers();

    /**
     * IDでCircleMemberを取得
     */
    Optional<CircleMemberDto> findById(Long id);

    /**
     * ユーザー名でCircleMemberを取得
     */
    Optional<CircleMemberDto> findByUsername(String username);

    /**
     * CircleMemberを作成
     */
    CircleMemberDto create(CreateCircleMemberDto createDto);

    /**
     * CircleMemberを更新
     */
    CircleMemberDto update(Long id, UpdateCircleMemberDto updateDto);

    /**
     * CircleMemberを削除
     */
    void delete(Long id);
}


