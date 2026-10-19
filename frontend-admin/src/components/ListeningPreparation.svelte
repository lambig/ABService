<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import QRCode from 'qrcode';
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import SessionControls from '$components/SessionControls.svelte';
  import { Button } from '$components/ui/button/index.js';
  import {
    applySessionResult,
    isCurrentSession,
    storedSession,
    type AdminSession,
  } from '$lib/credentials';
  import { listAlbums, getAlbum, type ApiResult, type AdminAlbum } from '$lib/api/client';
  import { crossfade, registration } from '$lib/api/private-audio';
  import {
    resolveAuthorization,
    decideAuthorization,
    listeningDevices,
    revokeDevice,
    type Authorization,
    type Device,
  } from '$lib/api/listening-devices';
  import { listeningEntry, type ListeningEntry } from '$lib/listening-entry';
  import { albumIdIn, editAlbumPath, albumAudioPath } from '$lib/paths';

  type AvailableAlbum = Readonly<{
    album: Pick<AdminAlbum, 'albumId' | 'title' | 'publishedAt'>;
    reason: string;
  }>;
  let session = $state<AdminSession | null>(null);
  let entry = $state<ListeningEntry | null>(null);
  let loaded = $state(false);
  let busy = $state(false);
  let message = $state('');
  let code = $state('');
  let days = $state(30);
  let request = $state<Authorization | null>(null);
  let resolvedCode = $state('');
  let devices = $state<readonly Device[]>([]);
  let albums = $state<readonly AvailableAlbum[]>([]);
  let focused = $state<AvailableAlbum | null>(null);
  let page = $state(0);
  let totalPages = $state(0);
  let qr = $state('');
  let alive = $state(true);
  const stateLabel = (state: string): string =>
    (
      ({
        PENDING: '許可待ち',
        APPROVED: '許可済み・受取待ち',
        CONSUMED: '認証発行済み',
        DENIED: '拒否済み',
        EXPIRED: '期限切れ',
        NOT_FOUND: '該当なし',
        ACTIVE: '有効',
        REVOKED: '失効済み',
      }) as Readonly<Record<string, string>>
    )[state] ?? state;
  const fail = (reason: string): never => {
    throw new Error(reason);
  };
  const accept = <T,>(owner: AdminSession, result: ApiResult<T>): T => {
    const accepted = alive ? applySessionResult(owner, result, () => result) : null;
    return accepted?.kind === 'ok'
      ? accepted.value
      : fail(
          accepted?.kind === 'failed'
            ? accepted.status === 429
              ? '照合回数の上限です。5分後に新しい接続コードでやり直してください。'
              : accepted.message
            : '管理画面で再認証してください。',
        );
  };
  const run = async (action: (owner: AdminSession) => Promise<void>): Promise<void> => {
    const owner = session;
    busy = true;
    message = '';
    try {
      await action(owner ?? fail('管理画面で認証してください。'));
    } catch (error) {
      message =
        alive && owner === session
          ? error instanceof Error
            ? error.message
            : '操作を確認できません。状態を確認してください。'
          : message;
    } finally {
      busy = alive && owner === session ? false : busy;
      session =
        owner === session && owner !== null ? (isCurrentSession(owner) ? session : null) : session;
    }
  };
  const available = async (
    owner: AdminSession,
    album: AvailableAlbum['album'],
  ): Promise<AvailableAlbum> => {
    const association = accept(owner, await crossfade(owner, album.albumId));
    const audio =
      association.audioId === null
        ? null
        : accept(owner, await registration(owner, association.audioId));
    return {
      album,
      reason:
        album.publishedAt === null
          ? audio?.state === 'CONFIRMED'
            ? '下書き：作品を公開すると配布対象になります。'
            : '下書き・音源未設定：作品を公開し、確定済みFLACを関連付けてください。'
          : audio?.state === 'CONFIRMED'
            ? '配布対象：確定済みFLACが関連付けられています。'
            : '配布対象外：確定済みFLACを関連付けてください。',
    };
  };
  const refresh = async (owner: AdminSession): Promise<void> => {
    const rows = accept(owner, await listeningDevices(owner));
    const listed = accept(owner, await listAlbums(owner, page));
    const availability = await Promise.all(listed.items.map((album) => available(owner, album)));
    const id = albumIdIn(window.location.search);
    const selected =
      id === null ? null : await available(owner, accept(owner, await getAlbum(owner, id)));
    accept(owner, { kind: 'ok', value: null });
    devices = rows.devices;
    albums = availability;
    focused = selected;
    totalPages = listed.totalPages;
  };
  const unlock = (owner: AdminSession): void => {
    session = owner;
    void run(refresh);
  };
  const logout = (): void => {
    session = null;
    request = null;
    devices = [];
    albums = [];
    focused = null;
    busy = false;
    code = '';
  };
  onMount(() => {
    void listeningEntry(window.location)
      .then(async (value) => {
        entry = value;
        loaded = true;
        qr =
          value === null
            ? ''
            : await QRCode.toDataURL(value.listeningUrl, { width: 224, margin: 2 });
        const stored = storedSession();
        (value?.kind === 'direct' && stored !== null
          ? () => {
              unlock(stored);
            }
          : () => undefined)();
      })
      .catch(() => {
        message = '正式な入口を確認できません。接続を確認してください。';
      });
  });
  onDestroy(() => {
    alive = false;
  });
  const resolve = (): void => {
    request = null;
    const enteredCode = code;
    void run(async (owner) => {
      const result = accept(owner, await resolveAuthorization(owner, enteredCode));
      (code === enteredCode ? () => undefined : () => fail('コードを再照合してください。'))();
      resolvedCode = enteredCode;
      request = result;
      message =
        request.state === 'PENDING'
          ? '試聴機の画面とコード・端末名を照合してから許可してください。'
          : request.state === 'CONSUMED'
            ? `認証発行済みです。確認用ID：${request.deviceId ?? request.requestId ?? '確認できません'}。試聴機で受取を確認できなければ、一覧の同じIDの認証を失効してから接続をやり直してください。`
            : `接続状態: ${stateLabel(request.state)}。期限切れなら試聴機で新しい接続を開始してください。`;
    });
  };
  const decide = (approve: boolean): void => {
    const id = code === resolvedCode ? request?.requestId : undefined;
    void run(async (owner) => {
      request = accept(
        owner,
        await decideAuthorization(
          owner,
          id ?? fail('接続コードを照合してください。'),
          approve,
          days,
        ),
      );
      message =
        request.state === 'APPROVED'
          ? '接続を許可しました。試聴機で「アプリと音源を保存」を押してください。PCでは端末への保存完了は確認できません。'
          : `接続状態: ${stateLabel(request.state)}。試聴機の表示を確認してください。`;
    });
  };
  const revoke = (id: string): void => {
    void run(async (owner) => {
      accept(owner, await revokeDevice(owner, id));
      await refresh(owner);
      message = '新しい音源の取得に使う認証を失効しました。保存済み音源は消去されません。';
    });
  };
  const move = (next: number): void => {
    page = next;
    void run(refresh);
  };
  const copy = (): void => {
    void navigator.clipboard
      .writeText(entry?.listeningUrl ?? '')
      .then(() => {
        message = '試聴アプリのURLをコピーしました。';
      })
      .catch(() => {
        message = 'URLを選択してコピーしてください。';
      });
  };
</script>

<SessionControls onLogout={logout} />
<section class="space-y-6" aria-label="試聴機の準備">
  <h1 class="text-2xl font-bold">試聴機を準備する</h1>
  <p>管理操作はこのPCで行います。試聴機では試聴アプリを開き、接続後に音源を保存します。</p>
  {#if message !== ''}<p role="status">{message}</p>{/if}
  {#if loaded && entry === null}
    <p role="alert">正式なHTTPS入口を確認できません。接続補助と管理画面の版を確認してください。</p>
  {:else if entry?.kind === 'bridge'}
    <p>音源登録用の接続から、正式な管理画面へ移動します。移動先で通常の管理認証が必要です。</p>
    <a
      class="underline"
      href={entry.preparationUrl +
        (albumIdIn(window.location.search) === null
          ? ''
          : '?albumId=' + encodeURIComponent(albumIdIn(window.location.search) ?? ''))}
      >正式な管理画面で試聴機を準備する</a
    >
  {:else if entry?.kind === 'direct'}
    {#if session === null}
      <ApiKeyForm message={null} onSubmit={unlock} />
    {:else}
      <section class="space-y-3" aria-label="配布対象">
        <h2 class="text-xl font-semibold">配布対象を確認する</h2>
        <p>
          公開済みで確定済みFLACを関連付けた全作品を配布します。ここで端末別に選曲する操作はありません。
        </p>
        {#if focused !== null}<p>登録した作品：{focused.album.title} — {focused.reason}</p>{/if}
        <ul class="space-y-3">
          {#each albums as row (row.album.albumId)}
            <li>
              <strong>{row.album.title}</strong> — {row.reason}
              <a class="underline" href={editAlbumPath(row.album.albumId)}>作品を編集</a>
              <a class="underline" href={albumAudioPath(row.album.albumId)}>FLACを設定</a>
            </li>
          {/each}
        </ul>
        <Button
          disabled={[busy, page === 0].some(Boolean)}
          onclick={() => {
            move(page - 1);
          }}>前のページ</Button
        >
        <Button
          disabled={[busy, page + 1 >= totalPages].some(Boolean)}
          onclick={() => {
            move(page + 1);
          }}>次のページ</Button
        >
      </section>
      <section class="space-y-3" aria-label="試聴アプリの入口">
        <h2 class="text-xl font-semibold">別の試聴機で開く</h2>
        <p>
          試聴機のブラウザーで下のURLを入力するか、QRを読み取ってください。管理キーは入力しません。
        </p>
        <p class="break-all">{entry.listeningUrl}</p>
        <Button onclick={copy}>URLをコピー</Button>
        {#if qr !== ''}<img
            src={qr}
            alt="試聴アプリのURLだけを含むQR"
            width="224"
            height="224"
          />{/if}
        <a
          class="block underline"
          href={entry.listeningUrl}
          target="_blank"
          rel="noopener noreferrer">この端末で開く（このPCに保存します）</a
        >
      </section>
      <section class="space-y-3" aria-label="試聴機の接続許可">
        <h2 class="text-xl font-semibold">接続コードを確認して許可する</h2>
        <p>試聴機で「管理画面と接続」を押し、表示されたコードを入力してください。</p>
        <label class="block"
          >接続コード <input
            class="border p-2"
            bind:value={code}
            disabled={busy}
            oninput={() => {
              request = null;
              resolvedCode = '';
              message = '変更したコードを照合してください。';
            }}
            maxlength="32"
            autocomplete="off"
            spellcheck="false"
          /></label
        >
        <Button disabled={busy} onclick={resolve}>コードを照合</Button>
        {#if request?.state === 'PENDING'}
          <p>照合済みコード：{resolvedCode}</p>
          <p>端末名：{request.label} ／ 接続期限：{request.expiresAt}</p>
          <p>自分が操作している試聴機のコードと一致することを確認してください。</p>
          <label class="block"
            >音源取得の認証期間（日） <input
              type="number"
              min="1"
              max="90"
              bind:value={days}
            /></label
          >
          <Button
            disabled={[busy, days % 1 !== 0, days < 1, days > 90].some(Boolean)}
            onclick={() => {
              decide(true);
            }}>この試聴機を許可</Button
          >
          <Button
            disabled={busy}
            onclick={() => {
              decide(false);
            }}>拒否</Button
          >
        {/if}
      </section>
      <section class="space-y-3" aria-label="端末の認証一覧">
        <h2 class="text-xl font-semibold">取得用の認証を確認・失効する</h2>
        <Button disabled={busy} onclick={() => void run(refresh)}>一覧を更新</Button>
        <p>
          認証の発行は保存完了を意味しません。応答を受け取れなかった場合は対象IDを照合して失効し、試聴機で新しい接続を開始してください。
        </p>
        <ul class="space-y-3">
          {#each devices as device (device.deviceId)}
            <li>
              {device.label} ／ {stateLabel(device.state)} ／ 期限 {device.expiresAt}
              <p class="break-all">確認用ID：{device.deviceId}</p>
              <Button
                disabled={[busy, device.state !== 'ACTIVE'].some(Boolean)}
                onclick={() => {
                  revoke(device.deviceId);
                }}>この認証を失効</Button
              >
            </li>
          {/each}
        </ul>
      </section>
      <p>
        試聴機で保存後、全ての試聴タブを閉じて開き直してください。対象作品・保存状態を確認し、必要な解析を保存してから、通信なしで再生できることを確認します。更新時も同じ手順です。
      </p>
    {/if}
  {/if}
</section>
