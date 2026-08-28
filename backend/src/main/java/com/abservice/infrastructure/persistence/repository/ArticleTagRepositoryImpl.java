package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.repository.article.ArticleTagRepository;
import com.abservice.infrastructure.persistence.datasource.ArticleTagDataSource;
import com.abservice.infrastructure.persistence.mapper.ArticleMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * ArticleTagRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class ArticleTagRepositoryImpl implements ArticleTagRepository {

    private final ArticleTagDataSource dataSource;

    public ArticleTagRepositoryImpl(ArticleTagDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<ArticleTag> findByName(String name) {
        return dataSource.findByName(name)
                .map(
                        entity -> Optional.ofNullable(entity)
                                .map(ArticleMapper::toTag)
                                .orElse(null));
    }
}
