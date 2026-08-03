package com.abservice.application.query.tune;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.TuneTableRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TuneViewMapper（Entity→Read Model 変換）のテスト")
class TuneViewMapperTest {

    @Test
    @DisplayName("全項目が Read Model に写像される")
    void toViewShouldMapAllFields() {
        // Arrange
        final var entity = new TuneTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setTitle("チューンタイトル");
        entity.setTuneKind("ARRANGEMENT");
        entity.setDefaultComposerCredit("Trad.");
        entity.setDefaultArrangerCredit("John Doe arr.");
        entity.setOriginalWorkTitle("原曲タイトル");
        entity.setOriginalWorkCredit("原曲クレジット");
        entity.setTuneType("リール");
        entity.setDefaultKey("D");
        entity.setDefaultTempo(120);

        // Act
        final var view = TuneViewMapper.toView(entity);

        // Assert
        assertThat(view.tuneId()).isEqualTo("0192f8a0-0000-7000-8000-000000000000");
        assertThat(view.title()).isEqualTo("チューンタイトル");
        assertThat(view.tuneKind()).isEqualTo("ARRANGEMENT");
        assertThat(view.defaultComposerCredit()).isEqualTo("Trad.");
        assertThat(view.defaultArrangerCredit()).isEqualTo("John Doe arr.");
        assertThat(view.originalWorkTitle()).isEqualTo("原曲タイトル");
        assertThat(view.originalWorkCredit()).isEqualTo("原曲クレジット");
        assertThat(view.tuneType()).isEqualTo("リール");
        assertThat(view.defaultKey()).isEqualTo("D");
        assertThat(view.defaultTempo()).isEqualTo(120);
    }

    @Test
    @DisplayName("nullable 項目が null のエンティティも写像できる")
    void toViewShouldMapNullableFields() {
        // Arrange
        final var entity = new TuneTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000001");
        entity.setTitle("タイトルのみ");
        entity.setTuneKind("ORIGINAL");

        // Act
        final var view = TuneViewMapper.toView(entity);

        // Assert
        assertThat(view.defaultComposerCredit()).isNull();
        assertThat(view.defaultArrangerCredit()).isNull();
        assertThat(view.originalWorkTitle()).isNull();
        assertThat(view.originalWorkCredit()).isNull();
        assertThat(view.tuneType()).isNull();
        assertThat(view.defaultKey()).isNull();
        assertThat(view.defaultTempo()).isNull();
    }
}
