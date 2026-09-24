"use client";

import { useCallback, useRef } from "react";

export function insertAtCursor(
  text: string,
  insertion: string,
  start: number | null | undefined,
  end: number | null | undefined
): { next: string; cursor: number } {
  const s = start ?? text.length;
  const e = end ?? s;
  const safeStart = Math.max(0, Math.min(s, text.length));
  const safeEnd = Math.max(safeStart, Math.min(e, text.length));
  const next = text.slice(0, safeStart) + insertion + text.slice(safeEnd);
  return { next, cursor: safeStart + insertion.length };
}

export function useCursorInsertion(
  value: string,
  onChange: (v: string) => void
) {
  const ref = useRef<HTMLInputElement | HTMLTextAreaElement>(null);
  const selectionRef = useRef<{ start: number; end: number } | null>(null);

  const capture = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? start;
    selectionRef.current = { start, end };
  }, [value]);

  const handleSelect = useCallback(
    () => {
      const el = ref.current;
      if (!el) return;
      selectionRef.current = {
        start: el.selectionStart ?? value.length,
        end: el.selectionEnd ?? el.selectionStart ?? value.length,
      };
    },
    [value]
  );

  const insert = useCallback(
    (insertion: string) => {
      const sel = selectionRef.current;
      const start = sel?.start ?? ref.current?.selectionStart ?? value.length;
      const end = sel?.end ?? ref.current?.selectionEnd ?? start;
      const { next, cursor } = insertAtCursor(value, insertion, start, end);
      onChange(next);
      // restore focus/cursor after state propagates
      requestAnimationFrame(() => {
        const el = ref.current;
        if (!el) return;
        el.focus();
        try {
          el.setSelectionRange(cursor, cursor);
        } catch {}
        selectionRef.current = { start: cursor, end: cursor };
      });
    },
    [value, onChange]
  );

  return { ref, capture, handleSelect, insert, selectionRef };
}
