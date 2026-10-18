import { requestJson } from './api/http';

export type ListeningEntry = Readonly<{
  kind: 'direct' | 'bridge';
  preparationUrl: string;
  listeningUrl: string;
}>;

/** Reject loopback, credentials, extra query and path tricks in bridge-provided destinations. */
export const bridgeEntry = (value: unknown): ListeningEntry | null => {
  try {
    const record = value as Record<string, unknown> | null;
    const preparation = new URL(String(record?.['preparationUrl']));
    const listening = new URL(String(record?.['listeningUrl']));
    return record?.['kind'] === 'ssm-audio' &&
      preparation.protocol === 'https:' &&
      ['localhost', '127.0.0.1', '[::1]'].every((host) => preparation.hostname !== host) &&
      preparation.href === `${preparation.origin}/admin/listening/` &&
      listening.href === `${preparation.origin}/offline-player/`
      ? { kind: 'bridge', preparationUrl: preparation.href, listeningUrl: listening.href }
      : null;
  } catch {
    return null;
  }
};

export const listeningEntry = async (location: Location): Promise<ListeningEntry | null> => {
  const direct = (): ListeningEntry => ({
    kind: 'direct',
    preparationUrl: new URL('/admin/listening/', location.origin).href,
    listeningUrl: new URL('/offline-player/', location.origin).href,
  });
  const bridge = async (): Promise<ListeningEntry | null> => {
    const result = await requestJson<unknown>('/__audio-connection', { redirect: 'error' });
    return result.kind === 'ok' ? bridgeEntry(result.value) : null;
  };
  return location.protocol === 'https:' ? direct() : bridge();
};
