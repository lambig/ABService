/** ビルド内の公開API照会を、JSONの読取り完了まで1件ずつ進める。 */
export const createJsonFetcher = (baseUrl: string, fetcher: typeof fetch = fetch) => {
  const queue = { tail: Promise.resolve() };

  return <T>(path: string): Promise<T> => {
    const result = queue.tail.then(async () => {
      const response = await fetcher(`${baseUrl}${path}`);
      const body: unknown = response.ok
        ? await response.json()
        : await Promise.reject(
            new Error(`GET ${path} が失敗しました（HTTP ${String(response.status)}）`),
          );
      return body as T;
    });
    /* 複数ページから共有するI/O待ち行列の末尾だけを更新し、取得結果や公開データは変更しない。 */
    /* eslint-disable-next-line functional/immutable-data -- 同時に呼ばれるビルド時I/Oを同じ待ち行列へ追加するため */
    queue.tail = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  };
};
