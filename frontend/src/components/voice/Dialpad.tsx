'use client';

/**
 * VOICE-06 — phone dialpad. Presentational only; appends raw characters so
 * the SIP destination is never normalized by the UI.
 */
import { Button } from '@/components/ui/button';

const KEYS: { digit: string; sub: string }[] = [
  { digit: '1', sub: '' },
  { digit: '2', sub: 'ABC' },
  { digit: '3', sub: 'DEF' },
  { digit: '4', sub: 'GHI' },
  { digit: '5', sub: 'JKL' },
  { digit: '6', sub: 'MNO' },
  { digit: '7', sub: 'PQRS' },
  { digit: '8', sub: 'TUV' },
  { digit: '9', sub: 'WXYZ' },
  { digit: '*', sub: '' },
  { digit: '0', sub: '+' },
  { digit: '#', sub: '' },
];

interface DialpadProps {
  disabled?: boolean;
  onKey: (digit: string) => void;
}

export function Dialpad({ disabled = false, onKey }: DialpadProps) {
  return (
    <div className="grid grid-cols-3 gap-1.5" role="group" aria-label="Dialpad">
      {KEYS.map((key) => (
        <Button
          key={key.digit}
          type="button"
          variant="outline"
          size="lg"
          disabled={disabled}
          onClick={() => onKey(key.digit)}
          aria-label={key.digit === '*' ? 'star' : key.digit === '#' ? 'pound' : `digit ${key.digit}`}
          className="flex h-11 flex-col items-center justify-center gap-0 py-1"
        >
          <span className="text-base leading-none font-semibold">{key.digit}</span>
          {key.sub ? <span className="text-[9px] leading-none text-muted-foreground">{key.sub}</span> : null}
        </Button>
      ))}
    </div>
  );
}
