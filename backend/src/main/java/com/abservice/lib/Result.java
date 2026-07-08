package com.abservice.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;

/**
 * 処理結果を表すResult型
 *
 * <p>
 * 成功時は値を、失敗時はエラーのリストを保持します。 エラーが予測されうる処理全般で使用できる汎用的な型です。
 *
 * <h2>使用例</h2>
 *
 * <h3>パターン1: resolve() - 失敗時に例外をスロー</h3>
 *
 * <pre>{@code
 * // デフォルト例外
 * Album album = Album.create(title, catalogNumber).resolve();
 *
 * // カスタム例外
 * Album album = Album.create(title, catalogNumber).resolve(errors -> new ValidationException("アルバム情報が不正です", errors));
 * }</pre>
 *
 * <h3>パターン2: orElse() - 失敗時にデフォルト値を返す</h3>
 *
 * <pre>{@code
 * Album album = Album.create(title, catalogNumber).orElse(defaultAlbum);
 * }</pre>
 *
 * <h3>パターン3: orElseGet() - 失敗時に関数を実行してデフォルト値を取得</h3>
 *
 * <pre>{@code
 * Album album = Album.create(title, catalogNumber).orElseGet(errors -> {
 *     // エラーに基づいてデフォルト値を生成
 *     return Album.createDefault();
 * });
 * }</pre>
 *
 * <h3>パターン4: orElseDo() - 失敗時に副作用のある処理を実行</h3>
 *
 * <pre>{@code
 * Album album = Album.create(title, catalogNumber).orElseDo(errors -> {
 *     // ログ記録などの副作用のある処理
 *     logger.error("アルバム生成失敗: {}", errors);
 *     notificationService.send("エラーが発生しました");
 * });
 * }</pre>
 *
 * @param <T>
 *            成功時の値の型
 */
public sealed interface Result<T> {

    /**
     * 成功を表します
     *
     * @param <T>
     *            成功時の値の型
     */
    record Success<T>(@NonNull T value) implements Result<T> {
    }

    /**
     * 失敗（エラー）を表します
     *
     * @param <T>
     *            成功時の値の型（この場合は使用されない）
     */
    record Failure<T>(@NonNull List<ErrorResult> errors) implements Result<T> {
        public Failure {
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }

        public Failure(ErrorResult... errors) {
            this(Arrays.asList(errors));
        }
    }

    /**
     * 結果を解決します。 成功時は値を返し、失敗時は例外をスローします。
     *
     * @return 成功時の値
     * @throws IllegalStateException
     *             失敗時
     */
    default T resolve() {
        return resolve(errors -> {
            final String errorMessage = errors.stream().map(e -> e.field() + ": " + e.message())
                    .reduce((a, b) -> a + ", " + b).orElse("Unknown error");
            return new IllegalStateException("エラー: " + errorMessage);
        });
    }

    /**
     * 結果を解決します。 成功時は値を返し、失敗時は例外をスローします。
     *
     * @param exceptionMapper
     *            エラーリストから例外を生成する関数
     * @return 成功時の値
     * @throws Exception
     *             失敗時にexceptionMapperが生成した例外
     */
    default T resolve(Function<List<ErrorResult>, ? extends RuntimeException> exceptionMapper) {
        return switch (this) {
            case Success<T> success -> success.value();
            case Failure<T> failure -> throw exceptionMapper.apply(failure.errors());
        };
    }

    /**
     * 結果を解決します。 成功時は値を返し、失敗時はデフォルト値を返します。
     *
     * @param defaultValue
     *            失敗時に返すデフォルト値
     * @return 成功時の値、または失敗時のデフォルト値
     */
    default T orElse(T defaultValue) {
        return switch (this) {
            case Success<T> success -> success.value();
            case Failure<T> failure -> defaultValue;
        };
    }

    /**
     * 結果を解決します。 成功時は値を返し、失敗時は関数を実行してデフォルト値を取得します。
     *
     * <p>
     * デフォルト値の生成にコストがかかる場合に使用します（遅延評価）。
     *
     * @param supplier
     *            失敗時に実行してデフォルト値を生成する関数
     * @return 成功時の値、または失敗時の関数実行結果
     */
    default T orElseGet(Function<List<ErrorResult>, T> supplier) {
        return switch (this) {
            case Success<T> success -> success.value();
            case Failure<T> failure -> supplier.apply(failure.errors());
        };
    }

    /**
     * 失敗時に副作用のある処理を実行します。 成功時は値を返し、失敗時は処理を実行した後に例外をスローします。
     *
     * <p>
     * ログ記録や通知送信などの副作用を伴う処理に使用します。
     *
     * @param action
     *            失敗時に実行する処理
     * @return 成功時の値
     * @throws IllegalStateException
     *             失敗時（actionの実行後）
     */
    default T orElseDo(Consumer<List<ErrorResult>> action) {
        return switch (this) {
            case Success<T> success -> success.value();
            case Failure<T> failure -> {
                action.accept(failure.errors());
                final String errorMessage = failure.errors().stream().map(e -> e.field() + ": " + e.message())
                        .reduce((a, b) -> a + ", " + b).orElse("Unknown error");
                throw new IllegalStateException("エラー: " + errorMessage);
            }
        };
    }

    /**
     * 成功時の値を変換します。 失敗時はエラーをそのまま引き継ぎ、変換関数は実行されません。
     *
     * @param mapper
     *            成功値を変換する関数
     * @return 変換後のResult
     * @param <U>
     *            変換後の値の型
     */
    default <U> Result<U> map(Function<? super T, ? extends U> mapper) {
        return switch (this) {
            case Success<T> success -> Result.success(mapper.apply(success.value()));
            case Failure<T> failure -> Result.failure(failure.errors());
        };
    }

    /**
     * 成功時に {@code Result} を返す関数を適用し、結果を平坦化します。 失敗時はエラーをそのまま引き継ぎ、変換関数は実行されません。
     *
     * <p>
     * 後続処理もバリデーション（{@code Result} 返却）で、直前の成功値に依存して連鎖させたい場合に使用します。
     *
     * @param mapper
     *            成功値から {@code Result} を生成する関数
     * @return 適用後のResult
     * @param <U>
     *            変換後の値の型
     */
    default <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
        return switch (this) {
            case Success<T> success -> mapper.apply(success.value());
            case Failure<T> failure -> Result.failure(failure.errors());
        };
    }

    /**
     * 成功のResultを生成します
     *
     * @param value
     *            成功時の値
     * @return Success Result
     * @param <T>
     *            値の型
     */
    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * 失敗のResultを生成します
     *
     * @param errors
     *            エラーのリスト
     * @return Failure Result
     * @param <T>
     *            値の型
     */
    static <T> Result<T> failure(List<ErrorResult> errors) {
        return new Failure<>(errors);
    }

    /**
     * 失敗のResultを生成します
     *
     * @param errors
     *            エラーの可変長引数
     * @return Failure Result
     * @param <T>
     *            値の型
     */
    static <T> Result<T> failure(ErrorResult... errors) {
        return new Failure<>(errors);
    }

    /**
     * 2つのResultを合成します。 両方が成功の場合は combiner を適用して成功を返し、
     * いずれか（または両方）が失敗の場合は<b>すべてのエラーを集約</b>して失敗を返します。
     *
     * <p>
     * 複数のValue Object検証を独立に実行し、エラーをまとめて1つのResultにする用途に使用します。
     *
     * <pre>{@code
     * Result<Album> album = Result.zip(AlbumTitle.fromInput(title), CatalogNumber.fromInput(catalogNumber),
     *         (t, c) -> Album.reconstruct(Album.Id.generate(), t, c));
     * }</pre>
     *
     * @param a
     *            1つ目のResult
     * @param b
     *            2つ目のResult
     * @param combiner
     *            両成功値から結果を生成する関数
     * @return 合成結果（成功、または全エラーを集約した失敗）
     * @param <A>
     *            1つ目の値の型
     * @param <B>
     *            2つ目の値の型
     * @param <R>
     *            合成後の値の型
     */
    static <A, B, R> Result<R> zip(Result<A> a, Result<B> b, BiFunction<? super A, ? super B, ? extends R> combiner) {
        return a instanceof Success<A> sa && b instanceof Success<B> sb
                ? Result.success(combiner.apply(sa.value(), sb.value()))
                : Result.failure(aggregateErrors(a, b));
    }

    /**
     * 3つのResultを合成します。 すべて成功の場合は combiner を適用して成功を返し、
     * 1つでも失敗があれば<b>すべてのエラーを集約</b>して失敗を返します。
     *
     * @param a
     *            1つ目のResult
     * @param b
     *            2つ目のResult
     * @param c
     *            3つ目のResult
     * @param combiner
     *            全成功値から結果を生成する関数
     * @return 合成結果（成功、または全エラーを集約した失敗）
     * @param <A>
     *            1つ目の値の型
     * @param <B>
     *            2つ目の値の型
     * @param <C>
     *            3つ目の値の型
     * @param <R>
     *            合成後の値の型
     */
    static <A, B, C, R> Result<R> zip(Result<A> a, Result<B> b, Result<C> c,
            TriFunction<? super A, ? super B, ? super C, ? extends R> combiner) {
        return a instanceof Success<A> sa && b instanceof Success<B> sb && c instanceof Success<C> sc
                ? Result.success(combiner.apply(sa.value(), sb.value(), sc.value()))
                : Result.failure(aggregateErrors(a, b, c));
    }

    /**
     * Result が失敗の場合、そのエラーを sink に追加します。 zip のエラー集約用の内部ヘルパです。
     *
     * @param result
     *            検査対象のResult
     * @param sink
     *            エラーを追加するリスト
     */
    private static void collectErrors(Result<?> result, List<ErrorResult> sink) {
        if (result instanceof Failure<?> failure) {
            sink.addAll(failure.errors());
        }
    }

    /**
     * 複数のResultから失敗エラーを引数順に集約してリストにまとめます。 zip のエラー集約用の内部ヘルパです。
     *
     * @param results
     *            検査対象のResult（引数順にエラーを集約）
     * @return 全失敗エラーを集約したリスト
     */
    private static List<ErrorResult> aggregateErrors(Result<?>... results) {
        final List<ErrorResult> errors = new ArrayList<>();
        for (final Result<?> result : results) {
            collectErrors(result, errors);
        }
        return errors;
    }

    /**
     * 3引数版の関数インターフェース（{@link #zip(Result, Result, Result, TriFunction)} 用）。
     *
     * @param <A>
     *            1つ目の引数の型
     * @param <B>
     *            2つ目の引数の型
     * @param <C>
     *            3つ目の引数の型
     * @param <R>
     *            戻り値の型
     */
    @FunctionalInterface
    interface TriFunction<A, B, C, R> {
        /**
         * 3つの引数を受け取り結果を生成します。
         *
         * @param a
         *            1つ目の引数
         * @param b
         *            2つ目の引数
         * @param c
         *            3つ目の引数
         * @return 生成結果
         */
        R apply(A a, B b, C c);
    }
}
