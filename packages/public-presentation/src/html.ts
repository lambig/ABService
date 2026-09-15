/** 描画に渡る文字列を属性・テキストのどちらでも無害化する。 */
export const escape = (value: string): string =>
  value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
export const properNoun = (value: string): string =>
  `<span translate="no" class="notranslate">${escape(value)}</span>`;
export const allowedImage = (url: string | null): url is string =>
  url !== null && /^(?:https?:\/\/|\/(?![/\\]))[^\s\\]*$/i.test(url);
