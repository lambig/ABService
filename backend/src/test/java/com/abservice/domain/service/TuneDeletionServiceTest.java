package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.service.TuneDeletionService.TuneDeletion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("チューン削除の操作オブジェクト（規則の単体評価）のテスト")
class TuneDeletionServiceTest {

    @Test
    @DisplayName("トラックから参照されていないチューンは削除できる")
    void unreferencedTuneIsDeletable() {
        final var deletion = new TuneDeletion(tuneId(), false);

        assertThat(deletion.asValidated().errors()).isEmpty();
        assertThat(deletion.deletableId()).isEqualTo(deletion.tuneId());
    }

    @Test
    @DisplayName("トラックから参照されているチューンは削除できない")
    void referencedTuneIsNotDeletable() {
        final var deletion = new TuneDeletion(tuneId(), true);

        assertThat(deletion.asValidated().errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("TUNE_REFERENCED_BY_TRACK"));
        assertThatThrownBy(deletion::deletableId)
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private static Tune.Id tuneId() {
        return Tune.Id.generate();
    }
}
