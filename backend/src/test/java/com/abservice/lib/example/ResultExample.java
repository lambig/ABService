package com.abservice.lib.example;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Result型の使用例を示すサンプルクラス
 *
 * <p>
 * このクラスは、ドキュメント目的のサンプルコードです。
 */
public final class ResultExample {

    private ResultExample() {
        // ユーティリティクラスのためインスタンス化を禁止
    }

    /** カタログ番号を表すValue Object */
    public record CatalogNumber(String value) {

        public static Result<CatalogNumber> create(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return Result.failure(new ErrorResult("catalogNumber", "カタログ番号は必須です", "C001"));
            }

            if (!value.matches("^[A-Z]{2,4}-[0-9]{3,5}$")) {
                return Result.failure(new ErrorResult("catalogNumber", "カタログ番号の形式が不正です", "C002"));
            }

            return Result.success(new CatalogNumber(value));
        }
    }

    /** アルバムタイトルを表すValue Object */
    public record AlbumTitle(String value) {

        public static Result<AlbumTitle> create(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return Result.failure(new ErrorResult("title", "タイトルは必須です", "T001"));
            }

            if (value.length() > 100) {
                return Result.failure(new ErrorResult("title", "タイトルは100文字以内で入力してください", "T002"));
            }

            return Result.success(new AlbumTitle(value));
        }
    }

    /** アルバムを表すEntity */
    public record Album(AlbumTitle title, CatalogNumber catalogNumber) {

        /**
         * アルバムを生成します
         *
         * @param title
         *            タイトル
         * @param catalogNumber
         *            カタログ番号
         * @return 生成結果
         */
        public static Result<Album> create(@Nullable String title, @Nullable String catalogNumber) {
            final Result<AlbumTitle> titleResult = AlbumTitle.create(title);
            final Result<CatalogNumber> catalogResult = CatalogNumber.create(catalogNumber);

            // 両方のバリデーションを実行し、エラーを集約
            final List<ErrorResult> errors = new ArrayList<>();
            if (titleResult instanceof Result.Failure<AlbumTitle> failure) {
                errors.addAll(failure.errors());
            }
            if (catalogResult instanceof Result.Failure<CatalogNumber> failure) {
                errors.addAll(failure.errors());
            }

            if (!errors.isEmpty()) {
                return Result.failure(errors);
            }

            return Result.success(new Album(((Result.Success<AlbumTitle>) titleResult).value(),
                    ((Result.Success<CatalogNumber>) catalogResult).value()));
        }
    }

    /** 使用例を示すメインメソッド */
    public static void main(String[] args) {
        System.out.println("=== Result型の使用例 ===\n");

        // 例1: 成功ケース
        example1Success();

        // 例2: 失敗ケース - resolve()
        example2FailureWithResolve();

        // 例3: 失敗ケース - orElse()
        example3FailureWithOrElse();

        // 例4: 失敗ケース - orElseGet()
        example4FailureWithOrElseGet();

        // 例5: 失敗ケース - orElseDo()
        example5FailureWithOrElseDo();

        // 例6: 複数のエラー
        example6MultipleErrors();

        // 例7: switch式でのパターンマッチング
        example7SwitchExpression();
    }

    private static void example1Success() {
        System.out.println("【例1: 成功ケース】");
        final Result<Album> result = Album.create("BEST ALBUM", "ABC-1234");

        final Album album = result.resolve();
        System.out.println("アルバムを生成しました: " + album);
        System.out.println();
    }

    private static void example2FailureWithResolve() {
        System.out.println("【例2: 失敗ケース - resolve()】");
        final Result<CatalogNumber> result = CatalogNumber.create("");

        try {
            final CatalogNumber catalogNumber = result.resolve();
            System.out.println("カタログ番号: " + catalogNumber);
        } catch (IllegalStateException e) {
            System.out.println("例外がスローされました: " + e.getMessage());
        }
        System.out.println();
    }

    private static void example3FailureWithOrElse() {
        System.out.println("【例3: 失敗ケース - orElse()】");
        final Result<CatalogNumber> result = CatalogNumber.create("");

        final CatalogNumber catalogNumber = result.orElse(new CatalogNumber("ZZZ-0000"));
        System.out.println("カタログ番号: " + catalogNumber);
        System.out.println();
    }

    private static void example4FailureWithOrElseGet() {
        System.out.println("【例4: 失敗ケース - orElseGet()】");
        final Result<CatalogNumber> result = CatalogNumber.create("");

        final CatalogNumber catalogNumber = result.orElseGet(errors -> {
            System.out.println("エラーが発生しました: " + errors);
            return new CatalogNumber("ZZZ-9999");
        });
        System.out.println("カタログ番号: " + catalogNumber);
        System.out.println();
    }

    private static void example5FailureWithOrElseDo() {
        System.out.println("【例5: 失敗ケース - orElseDo()】");
        final Result<CatalogNumber> result = CatalogNumber.create("");

        try {
            final CatalogNumber catalogNumber = result.orElseDo(errors -> {
                System.out.println("副作用のある処理を実行: エラーをログ記録");
                System.out.println("エラー内容: " + errors);
            });
            System.out.println("カタログ番号: " + catalogNumber);
        } catch (IllegalStateException e) {
            System.out.println("処理実行後に例外がスローされました: " + e.getMessage());
        }
        System.out.println();
    }

    private static void example6MultipleErrors() {
        System.out.println("【例6: 複数のエラー】");
        final Result<Album> result = Album.create("", "invalid");

        if (result instanceof Result.Failure<Album> failure) {
            System.out.println("バリデーションエラーが発生しました:");
            for (final ErrorResult error : failure.errors()) {
                System.out.println("  - " + error);
            }
        }
        System.out.println();
    }

    private static void example7SwitchExpression() {
        System.out.println("【例7: switch式でのパターンマッチング】");
        final Result<Album> result = Album.create("BEST ALBUM", "ABC-1234");

        final String message = switch (result) {
            case Result.Success<Album> success -> "アルバムを生成しました: " + success.value().title().value();
            case Result.Failure<Album> failure -> "エラー: "
                    + failure.errors().stream().map(ErrorResult::toString).reduce((a, b) -> a + ", " + b).orElse("");
        };

        System.out.println(message);
        System.out.println();
    }
}
