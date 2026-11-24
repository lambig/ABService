/** 入力支援が読む現在の文字列と選択範囲。検証・保存の状態は持たない。 */
export type TextSelection = Readonly<{ value: string; start: number; end: number }>;

/** 採用後の入力と選択範囲。保存は呼び出し元の通常経路へ残す。 */
export type TextSuggestion = TextSelection;

/** ローカルで記法を提案する薄い入力支援源。 */
export type MarkdownAssistance = Readonly<{
  id: string;
  label: string;
  suggest: (selection: TextSelection) => TextSuggestion;
}>;

const wrap = (marker: string, selection: TextSelection): TextSuggestion => {
  const selected = selection.value.slice(selection.start, selection.end);
  const content = selected === '' ? '文字' : selected;
  const start = selection.start + marker.length;
  return {
    value: `${selection.value.slice(0, selection.start)}${marker}${content}${marker}${selection.value.slice(selection.end)}`,
    start,
    end: start + content.length,
  };
};

/** 選択内のバッククォートより長い区切りを使い、コードの途中で閉じない。 */
const code = (selection: TextSelection): TextSuggestion => {
  const selected = selection.value.slice(selection.start, selection.end);
  const width = Math.max(0, ...(selected.match(/`+/g) ?? []).map((run) => run.length)) + 1;
  const marker = '`'.repeat(width);
  const wrapped = wrap(marker, selection);
  return selected.includes('`')
    ? {
        value: `${selection.value.slice(0, selection.start)}${marker} ${selected} ${marker}${selection.value.slice(selection.end)}`,
        start: wrapped.start + 1,
        end: wrapped.end + 1,
      }
    : wrapped;
};

/** 記法の挿入はボタン選択時のみ行う。通信・候補取得・業務検証を要さない。 */
export const MARKDOWN_ASSISTANCE: readonly MarkdownAssistance[] = [
  { id: 'bold', label: '太字', suggest: (selection) => wrap('**', selection) },
  { id: 'italic', label: '斜体', suggest: (selection) => wrap('*', selection) },
  { id: 'code', label: 'コード', suggest: code },
];
