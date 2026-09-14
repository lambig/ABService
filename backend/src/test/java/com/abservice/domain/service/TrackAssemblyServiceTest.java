package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.service.TrackAssemblyService.TrackFields;
import com.abservice.domain.service.TrackAssemblyService.TuneFields;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackAssemblyService.resolveTracks（曲目の組み立て）のテスト")
class TrackAssemblyServiceTest {

    private static final TrackAssemblyService SERVICE = new TrackAssemblyService();

    /** 新しい行（IDを持たない） */
    private static TrackFields track(String title, List<TuneFields> tunes) {
        return new TrackFields(
                null,
                title,
                null,
                null,
                tunes);
    }

    /** 既にある行（IDを持つ） */
    private static TrackFields existingTrack(String trackId, String title) {
        return new TrackFields(
                trackId,
                title,
                null,
                null,
                null);
    }

    private static TrackFields trackBy(String title, String artistDisplayName) {
        return new TrackFields(
                null,
                title,
                artistDisplayName,
                null,
                null);
    }

    private static TuneFields tune(String tuneTitle) {
        return new TuneFields(
                tuneTitle,
                null,
                null,
                null);
    }

    private static TuneFields tuneCredited(String composer, String arranger) {
        return new TuneFields(
                "チューン1",
                composer,
                arranger,
                null);
    }

    @Test
    @DisplayName("正常な入力は成功しTrackの並びを生成する")
    void validInputSucceeds() {
        final var result = SERVICE.resolveTracks(List.of(trackBy("トラックタイトル", "アーティスト名")));

        assertThat(result).isInstanceOf(Result.Success.class);
        final var track = result.resolve().getFirst();
        assertThat(track.title().value()).isEqualTo("トラックタイトル");
        assertThat(track.artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(track.tunes()).isEmpty();
    }

    @Test
    @DisplayName("チューンの登場順は並びから振られる")
    void tuneSequencesFollowTheGivenOrder() {
        final var result = SERVICE.resolveTracks(
                List.of(
                        track(
                                "トラックタイトル",
                                List.of(
                                        tune("チューン1"),
                                        tune("チューン2")))));

        final var tunes = result.resolve().getFirst().tunes();
        assertThat(tunes).hasSize(2);
        assertThat(tunes.getFirst().seq()).isEqualTo(1);
        assertThat(tunes.getFirst().tuneTitle().value()).isEqualTo("チューン1");
        assertThat(tunes.getFirst().tuneId()).isNull();
        assertThat(tunes.getLast().seq()).isEqualTo(2);
    }

    @Test
    @DisplayName("IDを持つ行はそのIDを運び、持たない行は運ばない")
    void rowsCarryTheClaimedIdOnly() {
        final var existing = Track.Id.generate();

        final var result = SERVICE.resolveTracks(
                List.of(
                        existingTrack(existing.value(), "1曲目"),
                        track("2曲目", null)));

        /*
         * OWNERSHIP-IS-NOT-DECIDED-HERE: そのIDが対象の作品の子かどうかは、この段では決まらない。
         * ここが答えるのは「識別子として読めるか」までで、確かめるのは集約（AlbumTest 側で固定）。
         */
        assertThat(result.resolve().getFirst().trackId()).isEqualTo(existing);
        assertThat(result.resolve().getLast().trackId()).isNull();
    }

    @Test
    @DisplayName("IDが読めない行は、その行のIDの位置にエラーを返す")
    void unreadableIdFailsAtItsOwnPosition() {
        final var result = SERVICE.resolveTracks(List.of(existingTrack("not-a-uuid", "1曲目")));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.errors().stream().map(ErrorResult::field).toList())
                .containsExactly("tracks[0].trackId");
    }

    @Test
    @DisplayName("タイトルが無くても、名を持つチューンがあれば成功する（#360）")
    void untitledTrackSucceedsWhenANamedTuneIsPresent() {
        final var result = SERVICE.resolveTracks(List.of(track(null, List.of(tune("チューン1")))));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().getFirst().title()).isNull();
    }

    @Test
    @DisplayName("タイトルも名を持つチューンも無いトラックは、タイトルの位置にエラーを返す（#360）")
    void untitledTrackFailsWhenNoTuneIsNamed() {
        final var result = SERVICE.resolveTracks(List.of(track(null, List.of(tune(null)))));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.errors())
                .extracting(ErrorResult::field, ErrorResult::code)
                .containsExactly(tuple("tracks[0].title", "TRACK_NAME_UNRESOLVABLE"));
    }

    @Test
    @DisplayName("アーティスト名が未指定でも成功しnullとして扱われる")
    void blankOptionalFieldsSucceedWithNulls() {
        final var result = SERVICE.resolveTracks(List.of(trackBy("トラックタイトル", "   ")));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().getFirst().artistCredit()).isNull();
    }

    @Test
    @DisplayName("未指定・空の一覧はどちらも曲目なしとして扱われる")
    void absentListsResolveToNoTracks() {
        assertThat(SERVICE.resolveTracks(null).resolve()).isEmpty();
        assertThat(SERVICE.resolveTracks(List.of()).resolve()).isEmpty();
    }

    @Test
    @DisplayName("行そのものが無いトラックは、その行の位置の検証エラーになる")
    void missingTrackRowFails() {
        final var result = SERVICE.resolveTracks(Arrays.asList(null, track("2曲目", null)));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.errors())
                .extracting(ErrorResult::field, ErrorResult::code)
                .containsExactly(tuple("tracks[0]", "TRACK_REQUIRED"));
    }

    @Test
    @DisplayName("行そのものが無いチューン構成は、その行の位置の検証エラーになる")
    void missingTuneRowFails() {
        final var result = SERVICE.resolveTracks(
                List.of(
                        track(
                                "トラックタイトル",
                                Arrays.asList(null, tune("a".repeat(256))))));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.errors().stream().map(ErrorResult::field).toList())
                .containsExactly(
                        "tracks[0].tunes[0]",
                        "tracks[0].tunes[1].tuneTitle");
        assertThat(result.errors().stream().map(ErrorResult::code).toList())
                .containsExactly(
                        "TUNE_REQUIRED",
                        "TRACK_TUNE_TITLE_TOO_LONG");
    }

    @Test
    @DisplayName("複数の行の誤りは、行ごとの添字を保ったまま集約される")
    void errorsFromMultipleRowsAreAggregated() {
        final var result = SERVICE.resolveTracks(
                List.of(
                        trackBy("トラックタイトル", "a".repeat(256)),
                        track(null, List.of(tune(null)))));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.errors().stream().map(ErrorResult::field).toList())
                .containsExactly(
                        "tracks[0].artistDisplayName",
                        "tracks[1].title");
    }

    @Test
    @DisplayName("エラーの位置は入力の綴りで返り、作曲・編曲のクレジットを区別できる")
    void errorsCarryInputPaths() {
        final var result = SERVICE.resolveTracks(
                List.of(
                        track(
                                "トラックタイトル",
                                List.of(tuneCredited("a".repeat(256), "b".repeat(256))))));

        assertThat(result.errors().stream().map(ErrorResult::field).toList())
                .containsExactly(
                        "tracks[0].tunes[0].composerCreditOverride",
                        "tracks[0].tunes[0].arrangerCreditOverride");
    }
}
