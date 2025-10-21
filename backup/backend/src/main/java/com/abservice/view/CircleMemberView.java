package com.abservice.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.Mapping;
import com.abservice.entity.CircleMember;

import java.time.LocalDateTime;

/**
 * CircleMemberのEntityView
 * データ転送用のビュー定義
 */
@EntityView(CircleMember.class)
public interface CircleMemberView {

    @IdMapping
    Long getId();

    String getUsername();

    String getDisplayName();

    String getEmail();

    String getBio();

    String getAvatarUrl();

    Boolean getIsActive();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    @Mapping("role.name")
    String getRoleName();

    @Mapping("role.description")
    String getRoleDescription();
}


