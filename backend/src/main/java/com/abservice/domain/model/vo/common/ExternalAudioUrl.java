package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源URLの値オブジェクト
 *
 * <p>
 * 外部サービスにホストされた音源を埋め込むためのURLを表す値オブジェクトです。音源実体は自前配信せず外部サービスに委ねるため、
 * ドメインが持つのは埋め込み元URLだけです。
 * </p>
 * <ul>
 * <li>URL自体の形式検証は {@link Url} に委ねます</li>
 * <li>スキームは {@code https} に限定されます</li>
 * <li>ホストは埋め込み可能なサービス（{@link #ALLOWED_HOSTS}）に限定されます</li>
 * </ul>
 *
 * <p>
 * ホストを許可リストで縛るのは、埋め込みできないURLが保存される事故を防ぐためです。許可ホストを増やすときは、配信側の
 * Content-Security-Policy（{@code frame-src}）の許可リストと揃えて変更します。
 * </p>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            埋め込み元URL（non-null）
 */
public record ExternalAudioUrl(@NonNull Url value) implements ValueObject<ExternalAudioUrl> {

    /** 埋め込みを許可する外部サービスのホスト */
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "soundcloud.com",
            "www.soundcloud.com",
            "m.soundcloud.com",
            "on.soundcloud.com");

    /**
     * {@code https} のURLからホストを取り出すフォーマット
     *
     * <p>
     * ドメイン層は例外による分岐を持たないため（{@code java.net.URI} は不正な入力で例外を投げる）、スキームとホストの
     * 取り出しはパターンマッチで行います。ポート指定・認証情報付きのURLは埋め込み元として想定しないため、ここで弾かれます。
     * </p>
     */
    private static final Pattern HTTPS_URL_PATTERN = Pattern.compile("^https://([A-Za-z0-9._-]+)(/[^\\s]*)?$");

    /**
     * コンストラクタ
     *
     * @param value
     *            埋め込み元URL（non-null）
     * @throws IllegalArgumentException
     *             URLが未指定の場合、{@code https} でない場合、または許可されないホストの場合
     */
    public ExternalAudioUrl {
        externalAudioUrlPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            埋め込み元URLを表す文字列
     * @return ExternalAudioUrlインスタンス
     */
    public static ExternalAudioUrl of(String value) {
        return new ExternalAudioUrl(Url.of(value));
    }

    /**
     * 外部入力（文字列）から外部音源URLを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。URL自体の形式違反は {@link Url}
     * の検証結果を、スキーム・ホストの違反は本VOのエラーとして返します。信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            埋め込み元URLを表す文字列
     * @return 成功時は {@code ExternalAudioUrl}、失敗時はエラー
     */
    public static Result<ExternalAudioUrl> fromInput(@Nullable String value) {
        return Url.fromInput(value)
                .flatMap(url -> externalAudioUrlPolicy().verify(url, ExternalAudioUrl::new));
    }

    private static Policy<Url> externalAudioUrlPolicy() {
        return Policy.all(
                Policy.of(
                        Objects::nonNull,
                        () -> new ErrorResult(
                                "externalAudioUrl",
                                "External audio URL cannot be null",
                                "EXTERNAL_AUDIO_URL_REQUIRED")),
                Policy.of(
                        ExternalAudioUrl::isHttpsUrl,
                        () -> new ErrorResult(
                                "externalAudioUrl",
                                "External audio URL must be an https URL without port or credentials",
                                "EXTERNAL_AUDIO_URL_NOT_HTTPS")),
                Policy.of(
                        ExternalAudioUrl::hasAllowedHost,
                        () -> new ErrorResult(
                                "externalAudioUrl",
                                "External audio URL host must be one of " + ALLOWED_HOSTS,
                                "EXTERNAL_AUDIO_URL_HOST_NOT_ALLOWED")));
    }

    private static boolean isHttpsUrl(@Nullable Url url) {
        return extractHost(url).isPresent();
    }

    private static boolean hasAllowedHost(@Nullable Url url) {
        return extractHost(url)
                .filter(ALLOWED_HOSTS::contains)
                .isPresent();
    }

    private static Optional<String> extractHost(@Nullable Url url) {
        return Optional.ofNullable(url)
                .map(Url::value)
                .map(HTTPS_URL_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1));
    }

    @Override
    public boolean equivalentTo(ExternalAudioUrl other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
