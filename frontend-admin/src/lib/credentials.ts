/** 鍵の置き場。アプリ名で修飾し、同じ生成元に他のものが入っても衝突させない */
const STORAGE_KEY = 'abservice.admin.api-key';
const SESSION_SECRET_KEY = 'abservice.admin.api-key.session-secret';
const AES_ALGO = 'AES-GCM';
const IV_LENGTH = 12;

const toBase64 = (bytes: Uint8Array): string => {
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
};

const fromBase64 = (base64: string): Uint8Array => {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
};

const ensureSessionSecret = (): Uint8Array => {
  const saved = sessionStorage.getItem(SESSION_SECRET_KEY);
  if (saved) {
    return fromBase64(saved);
  }
  const secret = crypto.getRandomValues(new Uint8Array(32));
  sessionStorage.setItem(SESSION_SECRET_KEY, toBase64(secret));
  return secret;
};

const getCryptoKey = async (): Promise<CryptoKey> => {
  const secret = ensureSessionSecret();
  return crypto.subtle.importKey('raw', secret, { name: AES_ALGO }, false, ['encrypt', 'decrypt']);
};

const encryptApiKey = async (plainText: string): Promise<string> => {
  const key = await getCryptoKey();
  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));
  const encoded = new TextEncoder().encode(plainText);
  const cipherBuffer = await crypto.subtle.encrypt({ name: AES_ALGO, iv }, key, encoded);
  const cipherBytes = new Uint8Array(cipherBuffer);
  const payload = new Uint8Array(iv.length + cipherBytes.length);
  payload.set(iv, 0);
  payload.set(cipherBytes, iv.length);
  return toBase64(payload);
};

const decryptApiKey = async (payloadBase64: string): Promise<string | null> => {
  try {
    const payload = fromBase64(payloadBase64);
    if (payload.length <= IV_LENGTH) {
      return null;
    }
    const iv = payload.slice(0, IV_LENGTH);
    const cipherBytes = payload.slice(IV_LENGTH);
    const key = await getCryptoKey();
    const plainBuffer = await crypto.subtle.decrypt({ name: AES_ALGO, iv }, key, cipherBytes);
    return new TextDecoder().decode(plainBuffer);
  } catch {
    return null;
  }
};

/**
 * 管理APIの鍵を、そのタブが開いている間だけ保持する。
 *
 * <p>
 * 鍵をビルドへ焼き込まない（#122）。管理画面は静的な成果物として配信されるため、埋め込むと成果物を
 * 受け取れる誰もが管理操作をできることになる。入力を受け取り、保持はブラウザ側に閉じる。
 * </p>
 *
 * <p>
 * `localStorage` ではなく `sessionStorage` に置く。共用の端末で開いたまま離れたときに、タブを
 * 閉じれば残らない状態にする。持続させる必要が出たら、鍵そのものではなく期限付きのトークンを持つ形へ
 * 移す（認証の方式は #116 が持つ）。
 * </p>
 */
export const storedApiKey = async (): Promise<string | null> => {
  const stored = sessionStorage.getItem(STORAGE_KEY);
  if (!stored) {
    return null;
  }
  return decryptApiKey(stored);
};

/** 受け付けられた鍵を覚える。 */
export const rememberApiKey = (apiKey: string): void => {
  void encryptApiKey(apiKey).then((encrypted) => {
    sessionStorage.setItem(STORAGE_KEY, encrypted);
  });
};

/** 覚えている鍵を捨てる。 */
export const forgetApiKey = (): void => {
  sessionStorage.removeItem(STORAGE_KEY);
  sessionStorage.removeItem(SESSION_SECRET_KEY);
};
