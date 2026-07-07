# Result型の使用ガイド

## 概要

`Result<T>`型は、処理の成功または失敗を表現するための型です。

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
    new ErrorResult("title", "タイトルの形式が不正です")
);

// 複数のエラー
Result<Album> result = Result.failure(
    new ErrorResult("title", "タイトルは必須です"),
    new ErrorResult("catalogNumber", "カタログ番号の形式が不正です")
);

// エラーコード付き
Result<Album> result = Result.failure(
    new ErrorResult("title", "タイトルの形式が不正です", "E001")
);
```

## 結果の処理パターン

### パターン1: `resolve()` - 失敗時に例外をスロー

最もシンプルな使い方です。成功時は値を返し、失敗時は例外をスローします。

```java
// デフォルト例外（IllegalStateException）
Album album = Album.create(title, catalogNumber).resolve();

// カスタム例外
Album album = Album.create(title, catalogNumber).resolve(errors ->
    new ValidationException("アルバム情報が不正です", errors)
);
```

### パターン2: `orElse()` - 失敗時にデフォルト値を返す

失敗時に固定のデフォルト値を返します。

```java
Album album = Album.create(title, catalogNumber).orElse(defaultAlbum);
```

### パターン3: `orElseGet()` - 失敗時に関数を実行してデフォルト値を取得

デフォルト値の生成にコストがかかる場合に使用します（遅延評価）。
エラー情報に基づいて異なるデフォルト値を生成することもできます。

```java
Album album = Album.create(title, catalogNumber).orElseGet(errors -> {
    // エラーに基づいてデフォルト値を生成
    logger.warn("アルバム生成失敗: {}", errors);
    return Album.createDefault();
});
```

### パターン4: `orElseDo()` - 失敗時に副作用のある処理を実行

ログ記録や通知送信などの副作用を伴う処理に使用します。
処理実行後、例外をスローします。

```java
Album album = Album.create(title, catalogNumber).orElseDo(errors -> {
    // ログ記録などの副作用のある処理
    logger.error("アルバム生成失敗: {}", errors);
    notificationService.send("エラーが発生しました");
});
// ここで例外がスローされる
```

### パターン5: `map` / `flatMap` / `zip` - 値の変換と合成

失敗を例外やデフォルト値へ「畳み込む」前に、`Result` を保ったまま値を変換・合成するためのコンビネータです。

#### `map` - 成功値を変換する

成功時のみ変換関数を適用します。失敗時はエラーをそのまま引き継ぎ、関数は実行されません。

```java
Result<Integer> length = AlbumTitle.create(title).map(t -> t.value().length());
```

#### `flatMap` - `Result` を返す処理を連鎖する

後続処理も `Result` を返し、直前の成功値に依存して連鎖させたい場合に使用します。ネストした `Result<Result<T>>` を平坦化します。

```java
Result<Album> album = AlbumTitle.create(title)
    .flatMap(t -> Album.createWithTitle(t)); // createWithTitle は Result<Album> を返す
```

#### `zip` - 複数の検証を合成し、エラーを集約する

独立した複数の `Result`（例: 複数の Value Object 検証）を合成します。**すべて成功した場合のみ** combiner を適用し、1つでも失敗があれば**すべてのエラーを集約**して失敗を返します。2引数・3引数版があります。

```java
// 2つの検証を合成。両方失敗すれば errors には両方のエラーが含まれる
Result<Album> album = Result.zip(
    AlbumTitle.create(title),
    CatalogNumber.create(catalogNumber),
    (t, c) -> new Album(Album.Id.generate(), t, c)
);
```

## ドメインモデルでの使用例

### Value Objectの生成

```java
public record AlbumTitle(String value) {

  public static Result<AlbumTitle> create(String value) {
    if (value == null || value.isBlank()) {
      return Result.failure(
          new ErrorResult("title", "タイトルは必須です", "E001")
      );
    }

    if (value.length() > 200) {
      return Result.failure(
          new ErrorResult("title", "タイトルは200文字以内です", "E002")
      );
    }

    return Result.success(new AlbumTitle(value));
  }
}
```

### Entityの生成

複数のバリデーションエラーを集約して返す例。`zip` を使うと、各検証を独立に実行しつつエラーを自動で集約でき、`instanceof` による分岐やエラーリストの手組みが不要になります：

```java
public class Album {
  private final Album.Id id;
  private final AlbumTitle title;
  private final CatalogNumber catalogNumber;

  public static Result<Album> create(String title, String catalogNumber) {
    // 両方成功時のみ Album を生成。いずれか失敗すれば全エラーが集約される
    return Result.zip(
        AlbumTitle.create(title),
        CatalogNumber.create(catalogNumber),
        (t, c) -> new Album(Album.Id.generate(), t, c)
    );
  }
}
```

> `zip` は独立した検証（前段の成否に依存しない）の合成に使います。前段の成功値に依存して次の検証を行う場合は `flatMap` で連鎖します。

## Application層での使用例

```java
@ApplicationScoped
public class RegisterAlbumService {

  public AlbumDto registerAlbum(RegisterAlbumCommand command) {
    Result<Album> result = Album.create(
        command.title(),
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
        request.title(),
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
public static Result<AlbumTitle> create(String value) { ... }

// Bad
public static AlbumTitle create(String value) throws ValidationException { ... }
```

### 2. Application層で適切に処理

Application層では、Resultを適切な形に変換します。

```java
// resolve()でドメイン例外に変換（値検証エラーは ValidationException）
Album album = result.resolve(errors ->
    new ValidationException("アルバム情報が不正です", errors)
);
```

### 3. Resource層でHTTPレスポンスに変換

Resource層では、Resultをswitch式で分岐してHTTPレスポンスに変換します。

### 4. エラーコードの活用

エラーコードを設定することで、クライアント側での詳細なエラーハンドリングが可能になります。

```java
new ErrorResult("title", "タイトルの形式が不正です", "E002")
```

## 参考

- テストコード: `backend/src/test/java/com/abservice/lib/ResultTest.java`
