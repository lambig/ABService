package com.abservice.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.abservice.entity.Role;

import java.time.LocalDateTime;

/**
 * RoleのEntityView
 * データ転送用のビュー定義
 */
@EntityView(Role.class)
public interface RoleView {

    @IdMapping
    Long getId();

    String getName();

    String getDescription();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}


