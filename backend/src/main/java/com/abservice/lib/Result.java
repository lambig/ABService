package com.abservice.lib;

import java.util.Arrays;
import java.util.List;
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
            String errorMessage = errors.stream().map(e -> e.field() + ": " + e.message())
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
    default T orElseDo(java.util.function.Consumer<List<ErrorResult>> action) {
        return switch (this) {
            case Success<T> success -> success.value();
            case Failure<T> failure -> {
                action.accept(failure.errors());
                String errorMessage = failure.errors().stream().map(e -> e.field() + ": " + e.message())
                        .reduce((a, b) -> a + ", " + b).orElse("Unknown error");
                throw new IllegalStateException("エラー: " + errorMessage);
            }
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
}
