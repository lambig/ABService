package com.abservice.application.service.asset;

import com.abservice.application.port.PublishedAsset;
import com.abservice.application.port.PublishedAssets;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.stream.Stream;

/** 記録された実測値を順に控える記録先のテスト代替。 */
final class FakePublishedAssets implements PublishedAssets {
    private List<PublishedAsset> recorded = List.of();

    @Override
    public Uni<Void> record(PublishedAsset asset) {
        recorded = Stream.concat(recorded.stream(), Stream.of(asset)).toList();
        return Uni.createFrom().voidItem();
    }

    List<PublishedAsset> recorded() {
        return recorded;
    }
}
