import { render, screen, waitFor } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import MarkupInput from './MarkupInput.svelte';

const props = {
  id: 'body',
  value: '前対象後',
  markdown: true,
  disabled: false,
  invalid: false,
  onEdit: (): void => undefined,
};

describe('共通のMarkdown入力欄', () => {
  it('選択を記法で囲み、通常入力と同じ通知を使い、選択を入力欄へ戻す', async () => {
    const onEdit = vi.fn((value: string) => {
      void rerender({ value });
    });
    const { rerender } = render(MarkupInput, { ...props, onEdit });
    const input = screen.getByRole<HTMLTextAreaElement>('textbox');
    input.focus();
    input.setSelectionRange(1, 3);
    await userEvent.click(screen.getByRole('button', { name: '太字' }));
    await waitFor(() => {
      expect(input.value).toBe('前**対象**後');
      expect(document.activeElement).toBe(input);
      expect([input.selectionStart, input.selectionEnd]).toEqual([3, 5]);
    });
    expect(onEdit).toHaveBeenCalledExactlyOnceWith('前**対象**後');
    await userEvent.keyboard('追記');
    expect(input.value).toBe('前**追記**後');
    expect(onEdit).toHaveBeenLastCalledWith('前**追記**後');
  });

  it('カーソル位置に挿入した仮の文字をそのまま置換できる', async () => {
    const { rerender } = render(MarkupInput, {
      ...props,
      value: '',
      onEdit: (value: string) => {
        void rerender({ value });
      },
    });
    await userEvent.click(screen.getByRole('button', { name: '斜体' }));
    const input = screen.getByRole<HTMLTextAreaElement>('textbox');
    await waitFor(() => {
      expect(input.selectionEnd).toBe(3);
    });
    await userEvent.keyboard('音');
    expect(input.value).toBe('*音*');
  });

  it('プレーンテキストへの切替は記法や入力を消さず、補助だけを隠す', async () => {
    const onEdit = vi.fn();
    const { rerender } = render(MarkupInput, {
      ...props,
      value: '**既存**',
      onEdit,
      invalid: true,
    });
    await rerender({ markdown: false });
    const input = screen.getByRole<HTMLTextAreaElement>('textbox');
    expect(input.value).toBe('**既存**');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(screen.queryByRole('group')).toBeNull();
    expect(onEdit).not.toHaveBeenCalled();
    await userEvent.type(input, '追記');
    expect(onEdit).toHaveBeenCalled();
  });

  it('保存中は入力支援も通常入力も操作できない', async () => {
    const onEdit = vi.fn();
    render(MarkupInput, { ...props, disabled: true, onEdit });
    await userEvent.click(screen.getByRole('button', { name: '太字' }));
    expect(screen.getAllByRole('button').every((button) => button.hasAttribute('disabled'))).toBe(
      true,
    );
    expect(screen.getByRole('textbox').hasAttribute('disabled')).toBe(true);
    expect(onEdit).not.toHaveBeenCalled();
  });
});
