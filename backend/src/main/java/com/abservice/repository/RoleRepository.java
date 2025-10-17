package com.abservice.repository;

import com.abservice.entity.Role;
import com.abservice.view.RoleView;
import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * RoleのBlaze-Persistenceリポジトリ
 */
@ApplicationScoped
public class RoleRepository {
    
    @Inject
    EntityManager entityManager;
    
    @Inject
    EntityViewManager entityViewManager;
    
    @Inject
    CriteriaBuilderFactory criteriaBuilderFactory;
    
    /**
     * すべてのRoleを取得
     */
    public List<RoleView> findAll() {
        CriteriaBuilder<Role> cb = criteriaBuilderFactory.create(entityManager, Role.class);
        EntityViewSetting<RoleView, CriteriaBuilder<RoleView>> setting = 
            EntityViewSetting.create(RoleView.class);
        return entityViewManager.applySetting(setting, cb).getResultList();
    }
    
    /**
     * IDでRoleを取得
     */
    public Optional<RoleView> findById(Long id) {
        CriteriaBuilder<Role> cb = criteriaBuilderFactory.create(entityManager, Role.class);
        cb.where("id").eq(id);
        EntityViewSetting<RoleView, CriteriaBuilder<RoleView>> setting = 
            EntityViewSetting.create(RoleView.class);
        List<RoleView> result = entityViewManager.applySetting(setting, cb).getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
    
    /**
     * 名前でRoleを取得
     */
    public Optional<RoleView> findByName(String name) {
        CriteriaBuilder<Role> cb = criteriaBuilderFactory.create(entityManager, Role.class);
        cb.where("name").eq(name);
        EntityViewSetting<RoleView, CriteriaBuilder<RoleView>> setting = 
            EntityViewSetting.create(RoleView.class);
        List<RoleView> result = entityViewManager.applySetting(setting, cb).getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
    
    /**
     * Roleを保存
     */
    @Transactional
    public Role save(Role role) {
        if (role.getId() == null) {
            entityManager.persist(role);
        } else {
            entityManager.merge(role);
        }
        return role;
    }
    
    /**
     * Roleを削除
     */
    @Transactional
    public void deleteById(Long id) {
        Role role = entityManager.find(Role.class, id);
        if (role != null) {
            entityManager.remove(role);
        }
    }
    
    /**
     * 名前の存在チェック
     */
    public boolean existsByName(String name) {
        CriteriaBuilder<Role> cb = criteriaBuilderFactory.create(entityManager, Role.class);
        cb.where("name").eq(name);
        return cb.getResultList().size() > 0;
    }
}
