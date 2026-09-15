import { get, writable } from 'svelte/store';

/** 復旧対象は画面の種類とリソースIDで分ける。新規作成は専用の名前空間を使う。 */
export type RecoveryIdentity = Readonly<{ editor: 'album' | 'article'; target: string | null }>;
/** 認証情報・API応答・送信状態は含めず、入力値と編集開始世代だけを渡す。 */
export type RecoveryInput<T> = Readonly<{ values: T; revision: number | null }>;
export type RecoverySnapshot<T> = RecoveryInput<T> & Readonly<{ savedAt: string }>;
export type RecoveryHandle = Readonly<{ clear: () => void }>;
const PREFIX = 'abservice.admin.recovery.v1:';
const epoch = writable(Symbol());
const unavailable = 'このタブへの入力の退避ができません。画面を離れる前に入力を控えてください。';

/** 他の用途のsessionStorageを巻き込まない識別子。 */
export const recoveryKey = (identity: RecoveryIdentity): string =>
  PREFIX + JSON.stringify([identity.editor, identity.target]);

/** 保存領域に触れない状態も通常の入力を妨げない。 */
const attempt = <T>(
  action: () => T,
): Readonly<{ value: T; error: null }> | Readonly<{ value: null; error: string }> => {
  try {
    return { value: action(), error: null };
  } catch {
    return { value: null, error: unavailable };
  }
};

/** JSONは信頼せず、復旧用の形だけを読む。 */
export const objectOf = (value: unknown): Readonly<Record<string, unknown>> | null =>
  typeof value === 'object' && value !== null ? (value as Readonly<Record<string, unknown>>) : null;

/** 既知の文字列欄だけを取り出す。不完全な業務入力の検証はここでは行わない。 */
export const stringsOf = <K extends string>(
  keys: readonly K[],
  value: unknown,
): Readonly<Record<K, string>> | null => {
  const record = objectOf(value);
  return record !== null && keys.every((key) => typeof record[key] === 'string')
    ? (Object.fromEntries(keys.map((key) => [key, record[key]])) as Readonly<Record<K, string>>)
    : null;
};

const parsed = (raw: string | null): unknown => {
  try {
    return raw === null ? null : JSON.parse(raw);
  } catch {
    return null;
  }
};
const snapshotOf = <T>(
  raw: string | null,
  identity: RecoveryIdentity,
  decode: (value: unknown) => T | null,
): RecoverySnapshot<T> | null => {
  const record = objectOf(parsed(raw));
  const values = decode(record?.values);
  const revision = record?.revision;
  return record !== null &&
    record.version === 1 &&
    record.editor === identity.editor &&
    record.target === identity.target &&
    values !== null &&
    typeof record.savedAt === 'string' &&
    Number.isFinite(Date.parse(record.savedAt)) &&
    (identity.target === null
      ? revision === null
      : typeof revision === 'number' && Number.isSafeInteger(revision) && revision >= 0)
    ? { values, revision: revision as number | null, savedAt: record.savedAt }
    : null;
};

/** 明示ログアウト時だけ全対象を破棄し、残ったタイマーによる再作成も防ぐ。 */
export const clearEditorRecoveries = (): string | null => {
  epoch.set(Symbol());
  return attempt(() => {
    const storage = sessionStorage;
    Array.from({ length: storage.length }, (_, index) => storage.key(index))
      .filter((key): key is string => key !== null && key.startsWith(PREFIX))
      .forEach((key) => {
        storage.removeItem(key);
      });
  }).error;
};

/**
 * タブ内の復旧スナップショット。通信は行わない。debounceと離脱時flushは同じ保留値を使い、
 * 保存成功・明示破棄でタイマーも取り消す。復元の選択中は既存スナップショットを書き換えない。
 */
export const createEditorRecovery = <T>(
  identity: RecoveryIdentity,
  initial: RecoveryInput<T>,
  decode: (value: unknown) => T | null,
  storageOf: () => Storage = () => sessionStorage,
  continued = false,
) => {
  const generation = get(epoch);
  const key = recoveryKey(identity);
  const loaded = attempt(() => {
    const raw = storageOf().getItem(key);
    const snapshot = snapshotOf(raw, identity, decode);
    return { snapshot, invalid: raw !== null && snapshot === null };
  });
  const retained =
    continued &&
    loaded.value?.snapshot !== null &&
    JSON.stringify(loaded.value?.snapshot?.values) === JSON.stringify(initial.values) &&
    loaded.value?.snapshot?.revision === initial.revision;
  const state = writable({
    pending: retained ? null : (loaded.value?.snapshot ?? null),
    awaitingChoice: retained
      ? false
      : loaded.value === null
        ? false
        : loaded.value.snapshot !== null
          ? true
          : loaded.value.invalid,
    error:
      loaded.error ??
      (loaded.value.invalid ? '退避データの形式が読めません。破棄して入力を続けられます。' : null),
    savedAt: null as string | null,
  });
  /* 再認証では既に画面が保持した入力へ戻るため、再度の復元選択を要求しない。 */
  const baseline = writable(retained ? '' : JSON.stringify(initial.values));
  const current = writable(initial);
  const timer = writable<ReturnType<typeof setTimeout> | undefined>(undefined);
  const active = writable(true);
  const cancel = (): void => {
    clearTimeout(get(timer));
    timer.set(undefined);
  };
  const report = (error: string | null): void => {
    state.update((value) => ({ ...value, error }));
  };
  const remove = (): void => {
    report(
      attempt(() => {
        storageOf().removeItem(key);
      }).error,
    );
  };
  const write = (): void => {
    const input = get(current);
    const savedAt = new Date().toISOString();
    const result = attempt(() => {
      storageOf().setItem(key, JSON.stringify({ version: 1, ...identity, ...input, savedAt }));
    });
    state.update((value) => ({
      ...value,
      error: result.error,
      savedAt: result.error === null ? savedAt : value.savedAt,
    }));
  };
  const flush = (): void => {
    cancel();
    const operation = get(state).awaitingChoice
      ? null
      : get(active) && get(epoch) === generation
        ? JSON.stringify(get(current).values) === get(baseline)
          ? remove
          : write
        : null;
    operation?.();
  };
  const capture = (input: RecoveryInput<T>): void => {
    current.set(input);
    cancel();
    timer.set(setTimeout(flush, 500));
  };
  const clear = (): void => {
    cancel();
    baseline.set(JSON.stringify(get(current).values));
    state.update((value) => ({ ...value, pending: null, awaitingChoice: false, savedAt: null }));
    remove();
  };
  const restore = (): RecoverySnapshot<T> | null => {
    const snapshot = get(state).pending;
    current.set(snapshot ?? get(current));
    state.update((value) => ({
      ...value,
      pending: null,
      awaitingChoice: false,
      savedAt: snapshot?.savedAt ?? null,
    }));
    return snapshot;
  };
  const dispose = (): void => {
    flush();
    active.set(false);
  };
  return { subscribe: state.subscribe, capture, clear, restore, flush, dispose };
};
