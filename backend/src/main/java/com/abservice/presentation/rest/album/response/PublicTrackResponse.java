package com.abservice.presentation.rest.album.response;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 公開向けトラック1件（REST の公開出力契約）
 *
 * <p>
 * 公開サイトが曲目に出すのはトラック番号・タイトル・アーティスト名義と、セット内のチューン構成だけ。アーティストソートキーは
 * 並べ替えのための値で公開サイトは表示にも並びにも使わないため、項目名自体を持たない。トラックIDも同様で、編集対象を
 * 同定するための値であり、アルバム内の識別はトラック番号で足りる。
 * </p>
 *
 * @param trackNo
 *            アルバム内のトラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            トラック個別のアーティスト表示名（nullable。null はアルバムの名義を継承）
 * @param tunes
 *            チューン構成の一覧。登場順の昇順
 */
public record PublicTrackResponse(
        int trackNo,
        String title,
        @Nullable String artistDisplayName,
        List<TrackTuneResponse> tunes) {
}
