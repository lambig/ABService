package com.abservice.application.service.asset;

import com.abservice.application.port.PublishedAsset;
import com.abservice.application.port.PublishedAssets;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.stream.Stream;

/** 記録された実測値を順に控える記録先のテスト代替。{@link #failingOnce()} は最初の記録だけを一時障害として失敗させる。 */
final class FakePublishedAssets implements PublishedAssets {
    private List<PublishedAsset> recorded = List.of();
    private int remainingFailures;

    FakePublishedAssets() {
        this(0);
    }

    private FakePublishedAssets(int failures) {
        this.remainingFailures = failures;
    }

    static FakePublishedAssets failingOnce() {
        return new FakePublishedAssets(1);
    }

    @Override
    public Uni<Void> record(PublishedAsset asset) {
        final boolean fails = remainingFailures > 0;
        remainingFailures = Math.max(0, remainingFailures - 1);
        return fails
                ? Uni.createFrom().failure(new IllegalStateException("temporary database failure"))
                : appended(asset);
    }

    @Override
    public Uni<Boolean> isRecorded(String assetKey) {
        return Uni.createFrom().item(
                recorded.stream().anyMatch(asset -> asset.assetKey().equals(assetKey)));
    }

    private Uni<Void> appended(PublishedAsset asset) {
        recorded = Stream.concat(recorded.stream(), Stream.of(asset)).toList();
        return Uni.createFrom().voidItem();
    }

    List<PublishedAsset> recorded() {
        return recorded;
    }
}
