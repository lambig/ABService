package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * トラックの名の値オブジェクト
 *
 * <p>
 * トラックが画面や応答で名乗る名を表します。入力された {@link TrackTitle} を持つトラックはそれを、持たないトラックは
 * <b>自身のチューン名を繋いだもの</b>を名にします（#360）。どちらであるかを呼び出し側が分岐しなくて済むよう、名を
 * 答えるのはこの値オブジェクトだけにします。
 * </p>
 *
 * <p>
 * 繋ぎの区切りは受け取ります。既定値をここへ書かないのは、区切りが運用で変えうる設定値であり、値オブジェクトが
 * 「どう繋ぐか」ではなく「何を繋ぐか」を担うためです。
 * </p>
 *
 * <p>
 * <b>名を持たないトラックは、名を持つチューンを少なくとも1つ持ちます。</b>どちらも無いトラックは名を答えられず、
 * 生成できません。名の無いチューン（MC・環境音など）しか持たないトラックがこれに当たるため、必要な条件は
 * 「チューンを持つこと」ではなく「名を持つチューンを持つこと」になります。
 * </p>
 *
 * @param value
 *            トラックの名
 */
public record TrackName(String value) implements ValueObject<TrackName> {

    /**
     * コンストラクタ
     *
     * @param value
     *            トラックの名
     * @throws IllegalArgumentException
     *             名が空白の場合
     */
    public TrackName {
        namePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * 入力されたタイトルとチューン名の並びから名を解決します。
     *
     * @param title
     *            入力されたトラックタイトル（nullable。無ければチューン名から組み立てる）
     * @param tuneTitles
     *            チューン名の並び（登場順。名を持たないチューンは null）
     * @param separator
     *            チューン名を繋ぐ区切り
     * @return トラックの名
     * @throws IllegalArgumentException
     *             タイトルも、名を持つチューンも無い場合
     */
    public static TrackName of(
            @Nullable String title,
            List<@Nullable String> tuneTitles,
            String separator) {
        return new TrackName(
                resolve(
                        title,
                        tuneTitles,
                        separator));
    }

    /**
     * 名を答えられるかどうか。
     *
     * <p>
     * トラックの不変条件（名かチューン名のどちらかは持つ）を、トラック側が自分の言葉で検査するために使います。
     * 解決してみて空白になるかどうかで見ます——区切りは空白かどうかを変えないため、ここでは何を渡してもよい。
     * </p>
     *
     * @param title
     *            入力されたトラックタイトル（nullable）
     * @param tuneTitles
     *            チューン名の並び（名を持たないチューンは null）
     * @return 名を答えられる場合は true
     */
    public static boolean isResolvable(@Nullable String title, List<@Nullable String> tuneTitles) {
        return StringUtils.isNotBlank(
                resolve(
                        title,
                        tuneTitles,
                        ""));
    }

    private static String resolve(
            @Nullable String title,
            List<@Nullable String> tuneTitles,
            String separator) {
        return Optional.ofNullable(title)
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> joined(tuneTitles, separator));
    }

    private static String joined(List<@Nullable String> tuneTitles, String separator) {
        return named(tuneTitles)
                .collect(Collectors.joining(separator));
    }

    private static Stream<String> named(List<@Nullable String> tuneTitles) {
        return tuneTitles.stream().flatMap(TrackName::asNamed);
    }

    /** 名を持つチューンだけを1件の流れにし、持たないもの（MC・環境音など）は落とす */
    private static Stream<String> asNamed(@Nullable String tuneTitle) {
        return Optional.ofNullable(tuneTitle)
                .filter(StringUtils::isNotBlank)
                .stream();
    }

    private static Policy<String> namePolicy() {
        return Policy.of(
                StringUtils::isNotBlank,
                () -> new ErrorResult(
                        "trackName",
                        "Track name cannot be resolved: neither a track title nor a named tune is present",
                        "TRACK_NAME_UNRESOLVABLE"));
    }

    @Override
    public boolean equivalentTo(TrackName other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
