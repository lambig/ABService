<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import SessionControls from '$components/SessionControls.svelte';
  import { Button } from '$components/ui/button/index.js';
  import { getAlbum, type ApiResult } from '$lib/api/client';
  import {
    applySessionResult,
    isCurrentSession,
    storedSession,
    type AdminSession,
  } from '$lib/credentials';
  import { ALBUM_LIST_PATH, albumIdIn } from '$lib/paths';
  import {
    connectionReady,
    confirmAudio,
    crossfade,
    fingerprint,
    matches,
    pendingAudio,
    registration,
    uploadAudio,
    type Crossfade,
    type PendingAudio,
    type Registration,
  } from '$lib/api/private-audio';

  let session = $state<AdminSession | null>(null);
  let albumId = $state('');
  let title = $state('');
  let connected = $state(false);
  let busy = $state(false);
  let message = $state<string | null>(null);
  let file = $state<File | null>(null);
  let pending = $state<PendingAudio | null>(null);
  let observed = $state<Registration | null>(null);
  let current = $state<Crossfade | null>(null);
  let controller = $state<AbortController | null>(null);
  let sending = $state(false);
  const disconnected = $derived(connected ? false : true);
  const storageKey = (): string => `abservice.admin.audio.${albumId}`;

  const accept = <T,>(owner: AdminSession, result: ApiResult<T>): T => {
    const accepted = applySessionResult(owner, result, () => result);
    return accepted?.kind === 'ok'
      ? accepted.value
      : fail(
          accepted?.kind === 'failed'
            ? accepted.message
            : '認証が必要です。再認証して状態を確認してください。',
        );
  };
  const fail = (reason: string): never => {
    throw new Error(reason);
  };
  const requireState = (condition: boolean, reason: string): void =>
    condition ? undefined : fail(reason);
  const run = async (action: (owner: AdminSession) => Promise<void>): Promise<void> => {
    const owner = session;
    busy = true;
    message = null;
    controller = new AbortController();
    try {
      await (owner === null ? Promise.reject(new Error('認証してください。')) : action(owner));
    } catch (error) {
      message =
        owner === session
          ? error instanceof Error
            ? error.message
            : '操作を完了できませんでした。状態を確認してください。'
          : message;
    } finally {
      busy = owner === session ? false : busy;
      sending = owner === session ? false : sending;
      session =
        owner === session && owner !== null ? (isCurrentSession(owner) ? session : null) : session;
    }
  };
  const remember = (value: PendingAudio): void => {
    // RESUME-BEFORE-SEND: 再読込で予約を失わないよう、送信前にIDと照合値だけを保持する。
    sessionStorage.setItem(storageKey(), JSON.stringify(value));
    pending = value;
  };
  const restored = (): PendingAudio | null => {
    try {
      const value: unknown = JSON.parse(sessionStorage.getItem(storageKey()) ?? 'null');
      return pendingAudio(value) ? value : null;
    } catch {
      return null;
    }
  };
  const refresh = async (owner: AdminSession): Promise<void> => {
    current = accept(owner, await crossfade(owner, albumId));
    observed = pending === null ? null : accept(owner, await registration(owner, pending.audioId));
  };
  const unlock = (owner: AdminSession): void => {
    session = owner;
    void run(async (active) => {
      connected = await connectionReady();
      const album = accept(active, await getAlbum(active, albumId));
      title = album.title;
      await refresh(active);
    });
  };
  onMount(() => {
    albumId = albumIdIn(window.location.search) ?? '';
    pending = restored();
    const stored = storedSession();
    (stored === null
      ? () => undefined
      : () => {
          unlock(stored);
        })();
  });
  onDestroy(() => controller?.abort());
  const logout = (): void => {
    controller?.abort();
    session = null;
    busy = false;
    observed = null;
    current = null;
    message = '登録の記録はこのタブに保持しています。再認証後に状態を確認できます。';
  };
  const start = (): void => {
    const selected = file;
    void run(async (owner) => {
      const input = selected ?? fail('FLACを選択してください。');
      const signal = controller?.signal ?? fail('操作を開始できません。');
      message = 'ファイルを照合しています。';
      sending = true;
      const hash = await fingerprint(input, signal);
      const reserved = accept(
        owner,
        await registration(owner, undefined, { method: 'POST', signal }),
      );
      requireState(
        reserved.state === 'PENDING' && Date.parse(reserved.expiresAt) > Date.now(),
        '登録を開始できる状態ではありません。',
      );
      remember({ ...hash, audioId: reserved.audioId });
      observed = reserved;
      message = 'FLACを送信・検査しています。この画面と接続を開いたままお待ちください。';
      const result = await uploadAudio(owner, reserved.audioId, input, signal);
      // READ-AFTER-LOSS: 応答が失われても確定済みの場合があるため、再送せず照会する。
      sending = isCurrentSession(owner) ? false : sending;
      await refresh(owner);
      message =
        result.kind === 'ok'
          ? '送信後の登録状態を確認しました。'
          : '送信の応答を確認できませんでした。以下は再照会した現在の状態です。';
    });
  };
  const recover = (): void => {
    const expected = pending;
    void run(async (owner) => {
      const input = expected ?? fail('登録の記録がありません。');
      const row = accept(owner, await registration(owner, input.audioId));
      requireState(
        matches(row, input) && ['INSPECTED', 'ABANDONED'].includes(row.state),
        '復旧できる状態、または原本の一致を確認できません。',
      );
      const result = await confirmAudio(owner, input.audioId);
      await refresh(owner);
      message =
        result.kind === 'ok'
          ? '確定の復旧結果を確認しました。'
          : '確定の応答を確認できませんでした。以下は再照会した現在の状態です。';
    });
  };
  const associate = (): void => {
    const expected = pending;
    const baseline = current;
    void run(async (owner) => {
      const input = expected ?? fail('登録の記録がありません。');
      const revision = baseline?.revision ?? fail('作品の現在の設定を確認してください。');
      const row = accept(owner, await registration(owner, input.audioId));
      requireState(
        row.state === 'CONFIRMED' && matches(row, input),
        '確定済み原本の一致を確認できません。',
      );
      const result = await crossfade(owner, albumId, {
        audioId: input.audioId,
        expectedRevision: revision,
      });
      await refresh(owner);
      message =
        result.kind === 'ok' && current?.audioId === input.audioId
          ? 'この作品の試聴用FLACを設定し、保存結果を確認しました。'
          : '設定の応答が失敗したか、保存結果を確認できませんでした。現在の設定を確認してください。別の変更が入った場合は、比較してから再操作してください。';
    });
  };
  const reset = (): void => {
    sessionStorage.removeItem(storageKey());
    pending = null;
    observed = null;
    file = null;
    message = '次の登録は別の予約で行います。前の音源や作品の設定は削除されません。';
  };
  const equal = $derived(observed !== null && pending !== null && matches(observed, pending));
  const canAssociate = $derived(equal && observed?.state === 'CONFIRMED' && current !== null);
  const canRecover = $derived(equal && ['INSPECTED', 'ABANDONED'].includes(observed?.state ?? ''));
</script>

<SessionControls onLogout={logout} />
<section class="max-w-2xl space-y-5" aria-label="試聴用FLACの登録">
  <h1 class="text-2xl font-bold">試聴用FLACの登録</h1>
  <a class="text-sm underline" href={ALBUM_LIST_PATH}>作品一覧へ戻る</a>
  {#if message !== null}<p role="status" class="text-sm">{message}</p>{/if}
  {#if albumId === ''}
    <p role="alert">作品一覧から対象の作品を選んでください。</p>
  {:else if session === null}
    <ApiKeyForm message={null} onSubmit={unlock} />
  {:else}
    <h2 class="text-lg font-semibold">{title}</h2>
    {#if disconnected}
      <p role="alert">音源登録用の接続から、この管理画面を開いてください。</p>
    {/if}
    <p class="text-muted-foreground text-sm">
      作成済みのFLAC（256MiB以下）を登録します。音源は非公開で保存され、作品の公開試聴URLは変わりません。
    </p>
    <p class="text-sm break-all">
      現在の試聴用FLAC: {current === null ? '未確認' : (current.audioId ?? '未設定')}
    </p>
    {#if pending === null}
      <label class="block space-y-2"
        ><span>作成済みFLAC</span>
        <input
          type="file"
          accept=".flac,audio/flac"
          disabled={[busy, disconnected].some(Boolean)}
          onchange={(event) => {
            file = event.currentTarget.files?.item(0) ?? null;
          }}
        />
      </label>
      <Button disabled={[busy, disconnected, file === null].some(Boolean)} onclick={start}
        >FLACを登録する</Button
      >
    {:else}
      <dl class="space-y-2 text-sm break-all">
        <dt>登録ID</dt>
        <dd>{pending.audioId}</dd>
        <dt>登録状態</dt>
        <dd>{observed?.state ?? '未確認'}</dd>
        <dt>原本の照合</dt>
        <dd>{equal ? '選択したファイルと一致' : '確定した原本の一致は未確認'}</dd>
      </dl>
      {#if observed?.state === 'PENDING'}
        <p class="text-sm">
          未確定です。通信が切れた後も処理が続いている可能性があります。同じ登録へ再送せず、状態を確認してください。
        </p>
      {/if}
      <div class="flex flex-wrap gap-3">
        <Button variant="outline" disabled={busy} onclick={() => void run(refresh)}
          >登録状態と作品の設定を確認</Button
        >
        {#if canRecover}<Button disabled={[busy, disconnected].some(Boolean)} onclick={recover}
            >保存済み原本の確定を復旧</Button
          >{/if}
        {#if canAssociate}
          <Button
            disabled={[busy, disconnected, current?.audioId === pending.audioId].some(Boolean)}
            onclick={associate}
          >
            {current?.audioId === null
              ? 'この作品の試聴用FLACに設定'
              : '現在の試聴用FLACをこの音源に変更'}
          </Button>
        {/if}
        <Button variant="outline" disabled={busy} onclick={reset}>別のFLACを登録する</Button>
      </div>
    {/if}
    {#if busy}
      <p role="status">処理中です。</p>
      {#if sending}<Button variant="outline" onclick={() => controller?.abort()}
          >送信の待機を中断</Button
        >{/if}
    {/if}
  {/if}
</section>
