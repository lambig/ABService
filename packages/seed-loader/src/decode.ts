/**
 * 投入ファイルの JSON を、形を確かめながら読むための小さな部品。
 *
 * <p>
 * 形の誤り（項目の欠け・綴りの違い・型の違い）は、送ってからバックエンドの検証で知るのではなく、読んだ時点で
 * どのファイルのどの項目かを示して落とす。**知らない項目は誤りとして扱う**——`publised` のような綴り違いを
 * 黙って捨てると、下書きのつもりが公開される。
 * </p>
 */

/** 投入ファイルの形の誤り。メッセージがどのファイルのどの項目かを持つ */
export class SeedFileError extends Error {}

/** 値を、その所在（ファイルと項目の経路）を添えて読む */
export type Decoder<T> = (value: unknown, at: string) => T;

/** 読みの失敗。式の中から投げるために関数にしている */
export const fail = (at: string, message: string): never => {
  throw new SeedFileError(`${at}: ${message}`);
};

const describe = (value: unknown): string =>
  value === null ? 'null' : Array.isArray(value) ? '配列' : typeof value;

/** 文字列。空白だけの文字列も誤りにする（送っても項目なしとして扱われ、書いた意図と食い違う） */
export const string: Decoder<string> = (value, at) =>
  typeof value === 'string'
    ? value.trim() === ''
      ? fail(at, '空の文字列です。項目を省くならキーごと書きません')
      : value
    : fail(at, `文字列が要ります（${describe(value)}）`);

/** 真偽値 */
export const boolean: Decoder<boolean> = (value, at) =>
  typeof value === 'boolean' ? value : fail(at, `true か false が要ります（${describe(value)}）`);

/** 整数 */
export const integer: Decoder<number> = (value, at) =>
  typeof value === 'number' && Number.isInteger(value)
    ? value
    : fail(at, `整数が要ります（${describe(value)}）`);

/** 決まった語のどれか */
export const oneOf =
  <const T extends string>(values: readonly T[]): Decoder<T> =>
  (value, at) =>
    values.find((candidate) => candidate === value) ??
    fail(at, `${values.join(' / ')} のどれかが要ります（${JSON.stringify(value)}）`);

/** 省いてよい項目。キーが無いときだけ undefined になる（null は誤り） */
export const optional =
  <T>(decoder: Decoder<T>): Decoder<T | undefined> =>
  (value, at) =>
    value === undefined ? undefined : decoder(value, at);

/** 配列。要素ごとに位置を添えて読む */
export const array =
  <T>(element: Decoder<T>): Decoder<readonly T[]> =>
  (value, at) =>
    Array.isArray(value)
      ? value.map((item: unknown, index) => element(item, `${at}[${String(index)}]`))
      : fail(at, `配列が要ります（${describe(value)}）`);

/**
 * 項目ごとの読み方。省いてよい項目は {@link optional} で包む。
 *
 * 必須の項目の読み方が undefined を返さないことは、型ではなく各 decoder の実装が守る（`string` 等は
 * 値が無ければ落とす）。
 */
export type Shape<T> = { readonly [K in keyof T]-?: Decoder<T[K] | undefined> };

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Array.isArray(value) ? false : typeof value === 'object' && value !== null;

/**
 * オブジェクト。宣言した項目だけを読み、知らない項目があれば落とす。
 *
 * <p>
 * 省いてよい項目が undefined になったときはキーごと落とす。送る側（管理APIクライアント）が
 * `undefined` のキーを JSON にしないため結果は同じだが、読んだ形を比べるテストで揺れないようにする。
 * </p>
 */
export const object =
  <T extends object>(shape: Shape<T>): Decoder<T> =>
  (value, at) => {
    const record = isRecord(value)
      ? value
      : fail(at, `オブジェクトが要ります（${describe(value)}）`);
    const decoders = shape as Record<string, Decoder<unknown>>;
    const unknown = Object.keys(record).flatMap((key) => (key in decoders ? [] : [key]));
    const entries =
      unknown.length === 0
        ? Object.entries(decoders).flatMap(([key, decode]) => {
            const decoded = decode(record[key], `${at}.${key}`);
            return decoded === undefined ? [] : [[key, decoded] as const];
          })
        : fail(at, `知らない項目があります: ${unknown.join(', ')}（綴りを確かめてください）`);
    return Object.fromEntries(entries) as T;
  };

/** JSON の文書を読む。構文の誤りは所在を添えて落とす */
export const parseJson = (text: string, at: string): unknown => {
  try {
    return JSON.parse(text) as unknown;
  } catch (cause) {
    return fail(
      at,
      `JSON として読めません（${cause instanceof Error ? cause.message : String(cause)}）`,
    );
  }
};
