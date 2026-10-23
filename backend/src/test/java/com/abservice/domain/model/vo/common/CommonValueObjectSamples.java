package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.DomainObject;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.service.BusinessDateTimeProvider;
import java.util.List;

/** 配置規則の正例と意図的な違反例。製品クラスを対象とするArchTestには含まれない。 */
public interface CommonValueObjectSamples {
    record SharedValue(Url value) {
    }

    record Validation(Policy<String> policy) {
    }

    record ValueContract(ValueObject<?> value) {
    }

    record DomainContract(DomainObject<?> value) {
    }

    record SpecificValue(AlbumTitle title) {
    }

    record AggregateValue(Album album) {
    }

    record ServiceValue(BusinessDateTimeProvider provider) {
    }

    record GenericSpecificValue(List<AlbumTitle> titles) {
    }
}
