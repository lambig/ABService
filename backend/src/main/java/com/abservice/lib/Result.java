package com.abservice.lib;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.Validate;
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
        @Override
        public List<ErrorResult> errors() {
            return List.of();
        }

        @Override
        public T resolve(Function<List<ErrorResult>, ? extends RuntimeException> exceptionMapper) {
            return value();
        }

        @Override
        public T orElse(T defaultValue) {
            return value();
        }

        @Override
        public T orElseGet(Function<List<ErrorResult>, T> supplier) {
            return value();
        }

        @Override
        public T orElseDo(Consumer<List<ErrorResult>> action) {
            return value();
        }

        @Override
        public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
            return Result.success(mapper.apply(value()));
        }

        @Override
        public <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
            return mapper.apply(value());
        }
    }

    /**
     * 失敗（エラー）を表します
     *
     * @param <T>
     *            成功時の値の型（この場合は使用されない）
     */
    record Failure<T>(@NonNull List<ErrorResult> errors) implements Result<T> {
        public Failure {
            Validate.notEmpty(errors, "errors must not be empty");
        }

        public Failure(ErrorResult... errors) {
            this(Arrays.asList(errors));
        }

        @Override
        public T resolve(Function<List<ErrorResult>, ? extends RuntimeException> exceptionMapper) {
            throw exceptionMapper.apply(errors());
        }

        @Override
        public T orElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        public T orElseGet(Function<List<ErrorResult>, T> supplier) {
            return supplier.apply(errors());
        }

        @Override
        public T orElseDo(Consumer<List<ErrorResult>> action) {
            action.accept(errors());
            throw new IllegalStateException("エラー: " + errors().stream().map(e -> e.field() + ": " + e.message())
                    .reduce((a, b) -> a + ", " + b).orElse("Unknown error"));
        }

        @Override
        public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
            return Result.failure(errors());
        }

        @Override
        public <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
            return Result.failure(errors());
        }
    }

    /**
     * このResultが保持するエラーのリストを返します。 成功時は空リスト、失敗時は保持するエラーのリストを返します。
     *
     * <p>
     * Success/Failure がそれぞれ自身の値を返す多態メソッドです（型判別は不要）。
     * </p>
     *
     * @return エラーのリスト（成功時は空）
     */
    List<ErrorResult> errors();

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
    T resolve(Function<List<ErrorResult>, ? extends RuntimeException> exceptionMapper);

    /**
     * 結果を解決します。 成功時は値を返し、失敗時はデフォルト値を返します。
     *
     * @param defaultValue
     *            失敗時に返すデフォルト値
     * @return 成功時の値、または失敗時のデフォルト値
     */
    T orElse(T defaultValue);

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
    T orElseGet(Function<List<ErrorResult>, T> supplier);

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
    T orElseDo(Consumer<List<ErrorResult>> action);

    /**
     * 成功時の値を変換します。 失敗時はエラーをそのまま引き継ぎ、変換関数は実行されません。
     *
     * @param mapper
     *            成功値を変換する関数
     * @return 変換後のResult
     * @param <U>
     *            変換後の値の型
     */
    <U> Result<U> map(Function<? super T, ? extends U> mapper);

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
    <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper);

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
     * Result<Album> album = Result.zip(
     *         AlbumTitle.fromInput(title),
     *         CatalogNumber.fromInput(catalogNumber),
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
    static <A, B, R> Result<R> zip(
            Result<A> a,
            Result<B> b,
            BiFunction<? super A, ? super B, ? extends R> combiner) {
        return ap(a.map(av -> bv -> combiner.apply(av, bv)), b);
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
        return ap(
                ap(
                        a.map(
                                av -> bv -> cv -> combiner.apply(
                                        av,
                                        bv,
                                        cv)),
                        b),
                c);
    }

    /**
     * 複数のResultから失敗エラーを引数順に集約してリストにまとめます。 zip のエラー集約用の内部ヘルパです。
     *
     * @param results
     *            検査対象のResult（引数順にエラーを集約）
     * @return 全失敗エラーを集約したリスト
     */
    private static List<ErrorResult> aggregateErrors(Result<?>... results) {
        return Arrays.stream(results).flatMap(result -> result.errors().stream()).toList();
    }

    /**
     * applicative apply。 関数を保持する {@code Result} を値の {@code Result} に適用します。
     * 関数側が成功なら値側に {@link #map} で適用し、失敗が絡む場合は<b>両者のエラーを引数順に集約</b>して失敗を返します。
     *
     * <p>
     * 型判別（{@code instanceof}）や値の取り出し（{@code resolve}）を用いず、{@code zip}
     * のエラー集約を多態で表現するための内部プリミティブです。
     * </p>
     *
     * @param ff
     *            関数を保持する Result
     * @param fa
     *            値を保持する Result
     * @return 適用結果（成功、または両者のエラーを集約した失敗）
     * @param <U>
     *            入力値の型
     * @param <R>
     *            適用後の値の型
     */
    private static <U, R> Result<R> ap(Result<Function<U, R>> ff, Result<U> fa) {
        return switch (ff) {
            case Success<Function<U, R>> sf -> fa.map(sf.value());
            case Failure<Function<U, R>> failure -> Result.failure(aggregateErrors(ff, fa));
        };
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
        R apply(
                A a,
                B b,
                C c);
    }
}
