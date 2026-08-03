package com.abservice.application.query.tune;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetTuneService.toResult（結果分岐）のテスト")
class GetTuneServiceTest {

    @Test
    @DisplayName("エンティティありはFoundを返す")
    void entityYieldsFound() {
        final var entity = new TuneTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setTitle("タイトル");
        entity.setTuneKind("TRAD");

        final var result = GetTuneService.toResult(entity);

        assertThat(result).isInstanceOf(GetTuneResult.Found.class);
        assertThat(((GetTuneResult.Found) result).tune().title()).isEqualTo("タイトル");
    }

    @Test
    @DisplayName("nullはNotFoundを返す")
    void nullYieldsNotFound() {
        assertThat(GetTuneService.toResult(null)).isInstanceOf(GetTuneResult.NotFound.class);
    }
}
