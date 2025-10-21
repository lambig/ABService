package com.abservice.repository;

import com.abservice.entity.CircleMember;
import com.abservice.view.CircleMemberView;
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
 * CircleMemberのBlaze-Persistenceリポジトリ
 */
@ApplicationScoped
public class CircleMemberRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    EntityViewManager entityViewManager;

    @Inject
    CriteriaBuilderFactory criteriaBuilderFactory;

    /**
     * すべてのCircleMemberを取得
     */
    public List<CircleMemberView> findAll() {
        CriteriaBuilder<CircleMember> cb = criteriaBuilderFactory.create(entityManager, CircleMember.class);
        EntityViewSetting<CircleMemberView, CriteriaBuilder<CircleMemberView>> setting =
            EntityViewSetting.create(CircleMemberView.class);
        return entityViewManager.applySetting(setting, cb).getResultList();
    }

    /**
     * IDでCircleMemberを取得
     */
    public Optional<CircleMemberView> findById(Long id) {
        CriteriaBuilder<CircleMember> cb = criteriaBuilderFactory.create(entityManager, CircleMember.class);
        cb.where("id").eq(id);
        EntityViewSetting<CircleMemberView, CriteriaBuilder<CircleMemberView>> setting =
            EntityViewSetting.create(CircleMemberView.class);
        List<CircleMemberView> result = entityViewManager.applySetting(setting, cb).getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * ユーザー名でCircleMemberを取得
     */
    public Optional<CircleMemberView> findByUsername(String username) {
        CriteriaBuilder<CircleMember> cb = criteriaBuilderFactory.create(entityManager, CircleMember.class);
        cb.where("username").eq(username);
        EntityViewSetting<CircleMemberView, CriteriaBuilder<CircleMemberView>> setting =
            EntityViewSetting.create(CircleMemberView.class);
        List<CircleMemberView> result = entityViewManager.applySetting(setting, cb).getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * アクティブなCircleMemberのみを取得
     */
    public List<CircleMemberView> findActiveMembers() {
        CriteriaBuilder<CircleMember> cb = criteriaBuilderFactory.create(entityManager, CircleMember.class);
        cb.where("isActive").eq(true);
        EntityViewSetting<CircleMemberView, CriteriaBuilder<CircleMemberView>> setting =
            EntityViewSetting.create(CircleMemberView.class);
        return entityViewManager.applySetting(setting, cb).getResultList();
    }

    /**
     * CircleMemberを保存
     */
    @Transactional
    public CircleMember save(CircleMember circleMember) {
        if (circleMember.getId() == null) {
            entityManager.persist(circleMember);
        } else {
            entityManager.merge(circleMember);
        }
        return circleMember;
    }

    /**
     * CircleMemberを削除
     */
    @Transactional
    public void deleteById(Long id) {
        CircleMember circleMember = entityManager.find(CircleMember.class, id);
        if (circleMember != null) {
            entityManager.remove(circleMember);
        }
    }

    /**
     * ユーザー名の存在チェック
     */
    public boolean existsByUsername(String username) {
        CriteriaBuilder<CircleMember> cb = criteriaBuilderFactory.create(entityManager, CircleMember.class);
        cb.where("username").eq(username);
        return cb.getResultList().size() > 0;
    }

    /**
     * メールアドレスの存在チェック
     */
    public boolean existsByEmail(String email) {
        CriteriaBuilder<CircleMember> cb = criteriaBuilderFactory.create(entityManager, CircleMember.class);
        cb.where("email").eq(email);
        return cb.getResultList().size() > 0;
    }
}


