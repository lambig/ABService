package com.abservice.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.model.CrossAggregateOperation;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("作品参照失効の呼出境界")
class CrossAggregateTransitionRuleTest {

    @Test
    @DisplayName("操作オブジェクト内では直接呼出しとメソッド参照を許可する")
    void permitsOperationObject() {
        assertThatCode(() -> check(AllowedOperation.class)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("操作オブジェクト外の直接呼出しを拒否する")
    void rejectsDirectCall() {
        assertThatThrownBy(() -> check(DirectCaller.class)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("loseAlbumReference").hasMessageContaining("DirectCaller");
    }

    @Test
    @DisplayName("操作オブジェクト外のメソッド参照も拒否する")
    void rejectsMethodReference() {
        assertThatThrownBy(() -> check(MethodReferenceCaller.class)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("loseAlbumReference").hasMessageContaining("MethodReferenceCaller");
    }

    private static void check(Class<?> caller) {
        new AggregateConstructionArchTest().crossAggregateTransitionsShouldOnlyBeCalledByOperationObjects(
                new ClassFileImporter().importClasses(AlbumArticle.class, caller));
    }

    @CrossAggregateOperation
    static class AllowedOperation {
        AlbumArticle direct(AlbumArticle article, BusinessDateTime now) {
            return article.loseAlbumReference(AlbumReferenceLostReason.ALBUM_DELETED, now);
        }

        BiFunction<AlbumReferenceLostReason, BusinessDateTime, AlbumArticle> reference(AlbumArticle article) {
            return article::loseAlbumReference;
        }
    }

    static class DirectCaller {
        AlbumArticle direct(AlbumArticle article, BusinessDateTime now) {
            return article.loseAlbumReference(AlbumReferenceLostReason.ALBUM_DELETED, now);
        }
    }

    static class MethodReferenceCaller {
        BiFunction<AlbumReferenceLostReason, BusinessDateTime, AlbumArticle> reference(AlbumArticle article) {
            return article::loseAlbumReference;
        }
    }
}
