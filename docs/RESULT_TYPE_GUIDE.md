# Result型の使用ガイド

## 概要

`Result<T>`型は、処理の成功または失敗を表現するための型です。

成功時は値を、失敗時はエラーのリストを保持します。エラーが予測されうる処理全般で使用できる汎用的な型です。

## パッケージ

- `com.abservice.lib.Result<T>` - 処理結果を表すsealed interface
- `com.abservice.lib.ErrorResult` - エラー情報を表すrecord
- `com.abservice.domain.model.policy.Policy<T>` - 値の検証を表現し、成功時のみ`constructor`を適用した`Result`を返すポリシー抽象。VOの`fromInput`実装の基盤

## 基本的な使い方

### 成功を表すResultの生成

```java
Result<ArticleTitle> result = Result.success(articleTitle);
```

### 失敗を表すResultの生成

```java
// 単一のエラー
Result<ArticleTitle> result = Result.failure(
    new ErrorResult("title", "タイトルの形式が不正です")
);

// 複数のエラー
Result<ArticleTitle> result = Result.failure(
    new ErrorResult("title", "タイトルは必須です"),
    new ErrorResult("title", "タイトルは500文字以内です")
);

// エラーコード付き
Result<ArticleTitle> result = Result.failure(
    new ErrorResult("title", "タイトルの形式が不正です", "ARTICLE_TITLE_REQUIRED")
);
```

## 結果の処理パターン

以降のパターンでは、外部入力からの検証生成を行う実在のVOファクトリ `ArticleTitle.fromInput(String): Result<ArticleTitle>`（`backend/src/main/java/com/abservice/domain/model/vo/article/ArticleTitle.java`）を例に用います。

### パターン1: `resolve()` - 失敗時に例外をスロー

最もシンプルな使い方です。成功時は値を返し、失敗時は例外をスローします。

```java
// デフォルト例外（IllegalStateException）
ArticleTitle title = ArticleTitle.fromInput(input.title()).resolve();

// カスタム例外（実際にCreateArticleServiceで使われている形）
Article article = validate(input).resolve(ValidationException::new);
```

### パターン2: `orElse()` - 失敗時にデフォルト値を返す

失敗時に固定のデフォルト値を返します。

```java
ArticleTitle title = ArticleTitle.fromInput(input.title()).orElse(ArticleTitle.of("無題"));
```

### パターン3: `orElseGet()` - 失敗時に関数を実行してデフォルト値を取得

デフォルト値の生成にコストがかかる場合に使用します（遅延評価）。
エラー情報に基づいて異なるデフォルト値を生成することもできます。

```java
ArticleTitle title = ArticleTitle.fromInput(input.title()).orElseGet(errors -> {
    // エラーに基づいてデフォルト値を生成
    logger.warn("タイトル検証失敗: {}", errors);
    return ArticleTitle.of("無題");
});
```

### パターン4: `orElseDo()` - 失敗時に副作用のある処理を実行

ログ記録や通知送信などの副作用を伴う処理に使用します。
処理実行後、例外をスローします。

```java
ArticleTitle title = ArticleTitle.fromInput(input.title()).orElseDo(errors -> {
    // ログ記録などの副作用のある処理
    logger.error("タイトル検証失敗: {}", errors);
    notificationService.send("エラーが発生しました");
});
// ここで例外がスローされる
```

### パターン5: `map` / `flatMap` / `zip` - 値の変換と合成

失敗を例外やデフォルト値へ「畳み込む」前に、`Result` を保ったまま値を変換・合成するためのコンビネータです。

#### `map` - 成功値を変換する

成功時のみ変換関数を適用します。失敗時はエラーをそのまま引き継ぎ、関数は実行されません。

```java
Result<Integer> length = ArticleTitle.fromInput(input.title()).map(t -> t.value().length());
```

#### `flatMap` - `Result` を返す処理を連鎖する

後続処理も `Result` を返し、直前の成功値に依存して連鎖させたい場合に使用します。ネストした `Result<Result<T>>` を平坦化します。
`ArticleType.fromInput`（`backend/src/main/java/com/abservice/domain/model/vo/article/ArticleType.java`）が実例です。1段目で非空を検証し、その成功値に対して2段目で既知の列挙子名かを検証しています。

```java
public static Result<ArticleType> fromInput(@Nullable String value) {
    return Policy.<String>of(
            StringUtils::isNotBlank,
            () -> new ErrorResult("articleType", "記事種別は必須です", "ARTICLE_TYPE_REQUIRED"))
            .verify(value, Function.identity())
            .flatMap(
                    v -> Policy.of(
                            ArticleType::isKnownName,
                            () -> new ErrorResult("articleType", "不正な記事種別です: " + v, "ARTICLE_TYPE_INVALID"))
                            .verify(v, valid -> valueOf(valid.trim())));
}
```

#### `zip` - 複数の検証を合成し、エラーを集約する

独立した複数の `Result`（例: 複数の Value Object 検証）を合成します。**すべて成功した場合のみ** combiner を適用し、1つでも失敗があれば**すべてのエラーを集約**して失敗を返します。2引数・3引数版があります。

```java
// 2つの検証を合成。両方失敗すれば errors には両方のエラーが含まれる
Result<Article> article = Result.zip(
    ArticleTitle.fromInput(title),
    ArticleType.fromInput(type),
    (t, ty) -> Article.create(ty, null, t, MarkupContent.EMPTY, null)
);
```

## ドメインモデルでの使用例

### Value Objectの生成

`ArticleTitle`（`backend/src/main/java/com/abservice/domain/model/vo/article/ArticleTitle.java`）の実例です。信頼できる内部生成には例外throwの`of()`を、外部入力からの生成にはResultを返す`fromInput()`を用いる、2系統のファクトリを持ちます。

```java
public record ArticleTitle(@NonNull String value) implements ValueObject<ArticleTitle> {

    public ArticleTitle {
        titlePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    // 内部生成用（不正時は例外）
    public static @NonNull ArticleTitle of(@NonNull String value) {
        return new ArticleTitle(value);
    }

    // 外部入力用（不正時はResult.failure）
    public static Result<ArticleTitle> fromInput(@Nullable String value) {
        return titlePolicy().verify(value, ArticleTitle::new);
    }

    private static Policy<String> titlePolicy() {
        return Policy.all(
                Policy.of(StringUtils::isNotBlank,
                        () -> new ErrorResult("title", "記事タイトルは必須です", "ARTICLE_TITLE_REQUIRED")),
                Policy.of((String v) -> StringUtils.length(v) <= 500,
                        () -> new ErrorResult("title", "記事タイトルは500文字以内です", "ARTICLE_TITLE_TOO_LONG")));
    }
}
```

### Entityの生成

複数のバリデーションエラーを集約して返す例。`zip` を使うと、各検証を独立に実行しつつエラーを自動で集約でき、`instanceof` による分岐やエラーリストの手組みが不要になります。`CreateArticleService.validate`（`backend/src/main/java/com/abservice/application/service/article/CreateArticleService.java`）の実例です。

```java
static Result<Article> validate(CreateArticleInput input) {
    return Result.zip(
            ArticleTitle.fromInput(input.title()),
            ArticleType.fromInput(input.articleType()),
            resolveBody(input.body(), input.bodyFormat()),
            (title, type, body) -> Article.create(
                    type,
                    null,
                    title,
                    body,
                    input.introShort()));
}
```

> `zip` は独立した検証（前段の成否に依存しない）の合成に使います。前段の成功値に依存して次の検証を行う場合は `flatMap` で連鎖します（上記`ArticleType.fromInput`参照）。

## Application層での使用例

`CreateArticleService`（`backend/src/main/java/com/abservice/application/service/article/CreateArticleService.java`）の実例です。値検証は各VOの`fromInput`（`Result`返却）に委譲し、本サービスは`Result.zip`で集約して`Article`を組み立て、`resolve`で`ValidationException`（`DomainException`のサブクラス）へ変換するオーケストレーションに徹します。

```java
@WithTransaction
@Override
public Uni<CreateArticleOutput> execute(CreateArticleInput input) {
    return Uni.createFrom()
            .item(() -> validate(input).resolve(ValidationException::new))
            .flatMap(articleRepository::save)
            .map(CreateArticleService::toOutput);
}
```

`Result`が保持する検証エラーは、この`resolve(ValidationException::new)`の時点で例外へ畳み込まれます。**`Result`自体はApplication層より先（Presentation層）には渡りません**。

## Presentation層との関係（Resultはここまで到達しない）

Presentation層（`@Path`のResourceクラス）はApplication層のサービスを呼び出すだけで、`Result`を直接扱うことも、成功/失敗で分岐することもありません。検証失敗は`DomainException`として例外の形でReactive chainを伝播し、`DomainExceptionMapper`（JAX-RS `ExceptionMapper<DomainException>`、`backend/src/main/java/com/abservice/presentation/rest/exception/DomainExceptionMapper.java`）が一括してRFC 9457 Problem Details（`application/problem+json`）へ変換します。

`ArticleCommandResource`（`backend/src/main/java/com/abservice/presentation/rest/article/ArticleCommandResource.java`）の実例:

```java
@POST
public Uni<Response> create(CreateArticleRequest request) {
    return createArticleService.execute(toInput(request))
            .map(ArticleCommandResource::toCreated);
}
```

成功時の変換（`toCreated`）だけを書けばよく、失敗時の分岐は一切登場しません。`ValidationException`→400、`EntityNotFoundException`→404、`BusinessRuleViolationException`→409、その他の`DomainException`→500への変換は、`DomainExceptionMapper`側に一元化されています。

## ベストプラクティス

### 1. ドメイン層ではResultを返す

ドメインモデルの外部入力からの生成やビジネスロジックでは、例外をスローせずにResultを返すことを推奨します。

```java
// Good
public static Result<ArticleTitle> fromInput(@Nullable String value) { ... }

// Bad
public static ArticleTitle fromInput(String value) throws ValidationException { ... }
```

信頼できる内部生成（永続化層からの再構成等）には、例外throwの`of()`を別途用意します。

### 2. Application層でDomainExceptionへ変換する

Application層では、`resolve(ValidationException::new)`のように`Result`を`DomainException`へ変換します。以降はPresentation層まで通常の例外として伝播させ、`Result`をPresentation層まで持ち越しません。

### 3. Presentation層はDomainExceptionMapperに委ねる

Presentation層でResultをswitchで分岐するコードは書きません。HTTPへの変換は`DomainExceptionMapper`（RFC 9457 Problem Details）に一元化します。

### 4. エラーコードの活用

エラーコードを設定することで、クライアント側での詳細なエラーハンドリングが可能になります。

```java
new ErrorResult("title", "記事タイトルは必須です", "ARTICLE_TITLE_REQUIRED")
```

## 参考

- テストコード: `backend/src/test/java/com/abservice/lib/ResultTest.java`
- VOの2系統生成の実例: `backend/src/main/java/com/abservice/domain/model/vo/article/ArticleTitle.java` / `ArticleType.java` / `MarkupContent.java`
- Application層の実例: `backend/src/main/java/com/abservice/application/service/article/CreateArticleService.java`
- Presentation層とDomainExceptionMapperの実例: `backend/src/main/java/com/abservice/presentation/rest/article/ArticleCommandResource.java` / `backend/src/main/java/com/abservice/presentation/rest/exception/DomainExceptionMapper.java`
