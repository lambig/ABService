package com.abservice.presentation.rest.openapi;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.presentation.rest.album.AlbumAdminQueryResource;
import com.abservice.presentation.rest.album.AlbumCommandResource;
import com.abservice.presentation.rest.album.AlbumExternalAudioCommandResource;
import com.abservice.presentation.rest.album.AlbumQueryResource;
import com.abservice.presentation.rest.album.AlbumTrackCommandResource;
import com.abservice.presentation.rest.article.ArticleAdminQueryResource;
import com.abservice.presentation.rest.article.ArticleCommandResource;
import com.abservice.presentation.rest.article.ArticleQueryResource;
import com.abservice.presentation.rest.article.ArticleTagAdminQueryResource;
import com.abservice.presentation.rest.article.ArticleTagCommandResource;
import com.abservice.presentation.rest.asset.AssetCommandResource;
import com.abservice.presentation.rest.site.SiteContentCommandResource;
import com.abservice.presentation.rest.site.SiteContentQueryResource;
import com.abservice.presentation.rest.tune.TuneCommandResource;
import com.abservice.presentation.rest.tune.TuneQueryResource;
import io.github.lambig.textescape.TextEscape;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.models.PathItem;

/**
 * リソースの宣言から、API 定義の中の操作を同定する
 *
 * <p>
 * 応答について実装が持つ事実（資源を作る・競合し得る）は、注釈で宣言してからフィルタが定義側へ写す。フィルタは
 * ビルド時に走りクラスパスを走査できないため、走査対象をここで数え上げ、宣言から経路と HTTP メソッドの組を作る。
 * </p>
 */
public final class DeclaredEndpoints {

    /**
     * API を構成する REST リソース。
     *
     * <p>
     * 数え上げから漏れたリソースの宣言は読まれず、その操作の定義だけが実装とずれる。ずれは定義を見に行くまで 分からないため、漏れは
     * {@code LayeredArchitectureTest} が {@code @Path} を持つクラスと突き合わせて落とす。
     * 宣言を持たないリソース（Query 側）も、漏れの検査を1つに保つため同じ数え上げに置く。
     * </p>
     */
    public static final List<Class<?>> RESOURCES = List.of(
            AlbumAdminQueryResource.class,
            AlbumCommandResource.class,
            AlbumExternalAudioCommandResource.class,
            AlbumQueryResource.class,
            AlbumTrackCommandResource.class,
            ArticleAdminQueryResource.class,
            ArticleCommandResource.class,
            ArticleQueryResource.class,
            ArticleTagAdminQueryResource.class,
            ArticleTagCommandResource.class,
            AssetCommandResource.class,
            SiteContentCommandResource.class,
            SiteContentQueryResource.class,
            TuneCommandResource.class,
            TuneQueryResource.class);

    private DeclaredEndpoints() {
    }

    /**
     * 指定の注釈を宣言したメソッドに対応する操作を返します。
     *
     * @param annotation
     *            リソースのメソッドへ付く宣言
     * @return 経路と HTTP メソッドの組
     */
    static Set<Endpoint> declaring(Class<? extends Annotation> annotation) {
        return RESOURCES.stream()
                .flatMap(resource -> endpointsOf(resource, annotation))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 各エンドポイントが実行するユースケースを返します。
     *
     * <p>
     * エラー応答の契約はユースケースが持つため、定義側はこの対応を通して失敗へ辿る。
     * </p>
     *
     * @return エンドポイントと、それが実行するユースケースの型
     */
    static Map<Endpoint, Class<?>> executedUseCases() {
        return RESOURCES.stream()
                .flatMap(DeclaredEndpoints::useCasesOf)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * その操作が返し得る失敗を返します。
     *
     * <p>
     * ユースケースを指していない操作（読み取りなど）は失敗を宣言しない。宣言が無いものを「失敗を返さない」と扱うため、
     * 指し忘れは定義から失敗が消えることで現れる。指し忘れ自体は {@code LayeredArchitectureTest} が落とす。
     * </p>
     *
     * @param useCases
     *            エンドポイントとユースケースの対応
     * @param endpoint
     *            対象の操作
     * @return 宣言された失敗
     */
    static List<Failure> failuresOf(Map<Endpoint, Class<?>> useCases, Endpoint endpoint) {
        return Optional.ofNullable(useCases.get(endpoint))
                .map(useCase -> useCase.getAnnotation(FailureContract.class))
                .map(FailureContract::value)
                .map(List::of)
                .orElseGet(List::of);
    }

    private static Stream<Map.Entry<Endpoint, Class<?>>> useCasesOf(Class<?> resource) {
        final String basePath = resource.getAnnotation(Path.class).value();

        return Arrays.stream(resource.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Executes.class))
                .map(
                        method -> Map.entry(
                                new Endpoint(httpMethodOf(method), pathOf(basePath, method)),
                                method.getAnnotation(Executes.class).value()));
    }

    private static Stream<Endpoint> endpointsOf(
            Class<?> resource,
            Class<? extends Annotation> annotation) {
        final String basePath = resource.getAnnotation(Path.class).value();

        return Arrays.stream(resource.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotation))
                .map(method -> new Endpoint(httpMethodOf(method), pathOf(basePath, method)));
    }

    /**
     * メソッドが受け付ける HTTP メソッドを、JAX-RS の宣言から読む。
     *
     * <p>
     * {@code @POST} などは {@code @HttpMethod} を持つ注釈であり、その値が HTTP メソッド名である。宣言の対象を
     * 特定のメソッドに限らないため、注釈の種類ではなくこのメタ注釈から導く。
     * </p>
     */
    private static PathItem.HttpMethod httpMethodOf(Method method) {
        return Arrays.stream(method.getAnnotations())
                .map(annotation -> annotation.annotationType().getAnnotation(HttpMethod.class))
                .filter(Objects::nonNull)
                .map(HttpMethod::value)
                .map(PathItem.HttpMethod::valueOf)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "応答を宣言した操作に HTTP メソッドの宣言がありません: " + method));
    }

    private static String pathOf(String basePath, Method method) {
        return TextEscape.escape("${base}${sub}")
                .where("base", basePath)
                .where(
                        "sub",
                        Optional.ofNullable(method.getAnnotation(Path.class))
                                .map(Path::value)
                                .orElse(""))
                .compile();
    }

    /**
     * API 定義の中で操作を一つに定める組。
     *
     * @param httpMethod
     *            HTTP メソッド
     * @param path
     *            経路
     */
    record Endpoint(PathItem.HttpMethod httpMethod, String path) {
    }
}
