# Result型の使用ガイド

## 概要

`Result<T>`型は、処理の成功または失敗を表現するための型です。Kotlinの参考プロジェクト (`ABService`) から移植されました。

成功時は値を、失敗時はエラーのリストを保持します。エラーが予測されうる処理全般で使用できる汎用的な型です。

## パッケージ

- `com.abservice.lib.Result<T>` - 処理結果を表すsealed interface
- `com.abservice.lib.ErrorResult` - エラー情報を表すrecord

## 基本的な使い方

### 成功を表すResultの生成

```java
Result<Album> result = Result.success(album);
```

### 失敗を表すResultの生成

```java
// 単一のエラー
Result<Album> result = Result.failure(
    new ErrorResult("catalogNumber", "メールアドレスの形式が不正です")
);

// 複数のエラー
Result<Album> result = Result.failure(
    new ErrorResult("name", "名前は必須です"),
    new ErrorResult("catalogNumber", "メールアドレスの形式が不正です")
);

// エラーコード付き
Result<Album> result = Result.failure(
    new ErrorResult("catalogNumber", "メールアドレスの形式が不正です", "E001")
);
```

## 結果の処理パターン

### パターン1: `resolve()` - 失敗時に例外をスロー

最もシンプルな使い方です。成功時は値を返し、失敗時は例外をスローします。

```java
// デフォルト例外（IllegalStateException）
Album album = Album.create(name, catalogNumber).resolve();

// カスタム例外
Album album = Album.create(name, catalogNumber).resolve(errors ->
    new ValidationException("アルバム情報が不正です", errors)
);
```

### パターン2: `orElse()` - 失敗時にデフォルト値を返す

失敗時に固定のデフォルト値を返します。

```java
Album album = Album.create(name, catalogNumber).orElse(defaultAlbum);
```

### パターン3: `orElseGet()` - 失敗時に関数を実行してデフォルト値を取得

デフォルト値の生成にコストがかかる場合に使用します（遅延評価）。
エラー情報に基づいて異なるデフォルト値を生成することもできます。

```java
Album album = Album.create(name, catalogNumber).orElseGet(errors -> {
    // エラーに基づいてデフォルト値を生成
    logger.warn("アルバム生成失敗: {}", errors);
    return Album.createDefault();
});
```

### パターン4: `orElseDo()` - 失敗時に副作用のある処理を実行

ログ記録や通知送信などの副作用を伴う処理に使用します。
処理実行後、例外をスローします。

```java
Album album = Album.create(name, catalogNumber).orElseDo(errors -> {
    // ログ記録などの副作用のある処理
    logger.error("アルバム生成失敗: {}", errors);
    notificationService.send("エラーが発生しました");
});
// ここで例外がスローされる
```

## ドメインモデルでの使用例

### Value Objectの生成

```java
public record CatalogNumber(String value) {

  public static Result<CatalogNumber> create(String value) {
    if (value == null || value.isBlank()) {
      return Result.failure(
          new ErrorResult("catalogNumber", "メールアドレスは必須です", "E001")
      );
    }

    if (!value.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
      return Result.failure(
          new ErrorResult("catalogNumber", "メールアドレスの形式が不正です", "E002")
      );
    }

    return Result.success(new CatalogNumber(value));
  }
}
```

### Entityの生成

複数のバリデーションエラーを集約して返す例：

```java
public class Album {
  private final AlbumId id;
  private final AlbumTitle name;
  private final CatalogNumber catalogNumber;

  public static Result<Album> create(String name, String catalogNumber) {
    Result<AlbumTitle> nameResult = AlbumTitle.create(name);
    Result<CatalogNumber> catalogNumberResult = CatalogNumber.create(catalogNumber);

    // 両方のバリデーションを実行し、エラーを集約
    List<ErrorResult> errors = new ArrayList<>();
    if (nameResult instanceof Result.Failure<AlbumTitle> failure) {
      errors.addAll(failure.errors());
    }
    if (catalogNumberResult instanceof Result.Failure<CatalogNumber> failure) {
      errors.addAll(failure.errors());
    }

    if (!errors.isEmpty()) {
      return Result.failure(errors);
    }

    return Result.success(new Album(
        AlbumId.generate(),
        ((Result.Success<AlbumTitle>) nameResult).value(),
        ((Result.Success<CatalogNumber>) catalogNumberResult).value()
    ));
  }
}
```

## Application層での使用例

```java
@ApplicationScoped
public class AlbumService {

  public AlbumDto registerAlbum(RegisterAlbumCommand command) {
    Result<Album> result = Album.create(
        command.name(),
        command.catalogNumber()
    );

    // パターン1: カスタム例外でエラーハンドリング
    Album album = result.resolve(errors ->
        new ValidationException("アルバム情報が不正です", errors)
    );

    albumRepository.save(album);
    return AlbumDto.from(album);
  }
}
```

## Resource層（REST API）での使用例

```java
@Path("/albums")
public class AlbumResource {

  @POST
  public Response registerAlbum(RegisterAlbumRequest request) {
    Result<Album> result = Album.create(
        request.name(),
        request.catalogNumber()
    );

    // Resultの型に応じて適切なレスポンスを返す
    return switch (result) {
      case Result.Success<Album> success -> {
        Album album = success.value();
        albumRepository.save(album);
        yield Response.ok(AlbumDto.from(album)).build();
      }
      case Result.Failure<Album> failure -> {
        var errors = failure.errors().stream()
            .map(e -> new ErrorDto(e.field(), e.message(), e.code()))
            .toList();
        yield Response.status(Response.Status.BAD_REQUEST)
            .entity(new ErrorResponse(errors))
            .build();
      }
    };
  }
}
```

## ベストプラクティス

### 1. ドメイン層ではResultを返す

ドメインモデルの生成やビジネスロジックでは、例外をスローせずにResultを返すことを推奨します。

```java
// Good
public static Result<CatalogNumber> create(String value) { ... }

// Bad
public static CatalogNumber create(String value) throws ValidationException { ... }
```

### 2. Application層で適切に処理

Application層では、Resultを適切な形に変換します。

```java
// resolve()でドメイン例外に変換
Album album = result.resolve(errors ->
    new DomainException("アルバム情報が不正です", errors)
);
```

### 3. Resource層でHTTPレスポンスに変換

Resource層では、Resultをswitch式で分岐してHTTPレスポンスに変換します。

### 4. エラーコードの活用

エラーコードを設定することで、クライアント側での詳細なエラーハンドリングが可能になります。

```java
new ErrorResult("catalogNumber", "メールアドレスの形式が不正です", "E002")
```

## 参考

- オリジナルのKotlin実装: `ABService/src/main/kotlin/com/internal/album/lib/Result.kt`
- テストコード: `backend/src/test/java/com/abservice/lib/ResultTest.java`
