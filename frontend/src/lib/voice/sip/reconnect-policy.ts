/**
 * VOICE-07 — pure reconnect backoff policy.
 *
 * Dependency-free on purpose: deterministic and verifiable with plain
 * `tsc` + `node`, no test framework, no browser globals. The adapter owns
 * one timer driven by this policy; the host/controller/UI stay untouched.
 *
 * Policy: 1s, 2s, 4s, 8s, then 16s capped, plus bounded jitter.
 * Attempts continue until cancelled (manual disconnect / destroy /
 * auth failure) — only the DELAY is bounded, never the will to recover.
 */

export interface ReconnectPolicyOptions {
  /** First-retry delay. Default 1000ms. */
  baseDelayMs?: number;
  /** Delay ceiling. Default 16000ms. */
  maxDelayMs?: number;
  /** Jitter as a fraction of the computed delay, added on top. Default 0.25. */
  jitterRatio?: number;
  /** Injectable clock (tests). Defaults to Date.now. */
  now?: () => number;
  /** Injectable randomness in [0, 1) (tests). Defaults to Math.random. */
  random?: () => number;
  /**
   * Injectable scheduler (tests). Must invoke cb once after delayMs and
   * return a cancel function. Defaults to setTimeout/clearTimeout.
   */
  schedule?: (delayMs: number, cb: () => void) => () => void;
}

export const RECONNECT_BASE_DELAY_MS = 1000;
export const RECONNECT_MAX_DELAY_MS = 16000;
export const RECONNECT_JITTER_RATIO = 0.25;

/**
 * Delay before attempt N (1-based). Pure function of (attempt, options).
 */
export function computeReconnectDelayMs(
  attempt: number,
  options?: Pick<ReconnectPolicyOptions, 'baseDelayMs' | 'maxDelayMs' | 'jitterRatio' | 'random'>,
): number {
  const safeAttempt = Number.isFinite(attempt) ? Math.max(1, Math.floor(attempt)) : 1;
  const base = options?.baseDelayMs ?? RECONNECT_BASE_DELAY_MS;
  const max = options?.maxDelayMs ?? RECONNECT_MAX_DELAY_MS;
  const jitterRatio = options?.jitterRatio ?? RECONNECT_JITTER_RATIO;
  const backoff = Math.min(max, base * 2 ** (safeAttempt - 1));
  const random = options?.random ?? Math.random;
  const jitter = Math.floor(backoff * Math.max(0, jitterRatio) * random());
  return backoff + jitter;
}

/** Wall-clock timestamp (ISO-8601) at which the given delay elapses. */
export function retryAtIso(nowMs: number, delayMs: number): string {
  return new Date(nowMs + delayMs).toISOString();
}
