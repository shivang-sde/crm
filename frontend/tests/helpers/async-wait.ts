import { Page, Locator, expect } from '@playwright/test';

export const POLL_INTERVAL_MS = parseInt(process.env.POLL_INTERVAL_MS || '2000', 10);
export const POLL_TIMEOUT_MS = parseInt(process.env.POLL_TIMEOUT_MS || '30000', 10);

export async function waitForCondition<T>(
  queryFn: () => Promise<T>,
  predicate: (value: T) => boolean,
  options: { timeoutMs?: number; intervalMs?: number; description?: string } = {}
): Promise<T> {
  const { timeoutMs = POLL_TIMEOUT_MS, intervalMs = POLL_INTERVAL_MS, description = 'condition' } = options;
  const startTime = Date.now();
  let lastValue: T | undefined;
  let lastError: Error | undefined;

  while (Date.now() - startTime < timeoutMs) {
    try {
      lastValue = await queryFn();
      if (predicate(lastValue)) {
        return lastValue;
      }
    } catch (error) {
      lastError = error as Error;
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }

  const errorMsg = `Timed out waiting for ${description} after ${timeoutMs}ms. ` +
    `Last observed value: ${JSON.stringify(lastValue)}` +
    (lastError ? `. Last error: ${lastError.message}` : '');
  throw new Error(errorMsg);
}

export async function waitForApiResponse<T>(
  apiCall: () => Promise<{ data: T[]; meta?: any }>,
  predicate: (items: T[]) => T | undefined,
  options: { timeoutMs?: number; intervalMs?: number; description?: string } = {}
): Promise<T> {
  return waitForCondition(
    async () => {
      const result = await apiCall();
      const matched = predicate(result.data);
      if (matched !== undefined) return matched;
      throw new Error('Predicate returned undefined');
    },
    () => true,
    options
  );
}

export async function waitForElement(
  locator: Locator,
  options: { timeoutMs?: number; state?: 'attached' | 'visible' | 'hidden' } = {}
): Promise<void> {
  const { timeoutMs = 10000, state = 'visible' } = options;
  switch (state) {
    case 'visible':
      await expect(locator).toBeVisible({ timeout: timeoutMs });
      break;
    case 'hidden':
      await expect(locator).toBeHidden({ timeout: timeoutMs });
      break;
    case 'attached':
      await expect(locator).toBeAttached({ timeout: timeoutMs });
      break;
  }
}

export async function waitForUrl(page: Page, urlPattern: RegExp | string, timeoutMs = 10000): Promise<void> {
  await page.waitForURL(urlPattern, { timeout: timeoutMs });
}

export async function waitForToast(page: Page, message: string, timeoutMs = 10000): Promise<void> {
  await expect(page.getByText(message)).toBeVisible({ timeout: timeoutMs });
}

export async function waitForNetworkIdle(page: Page, timeoutMs = 5000): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: timeoutMs });
}

export class AsyncWaiter {
  constructor(
    private apiCall: () => Promise<any>,
    private description: string
  ) {}

  async waitForStatus(
    expectedStatus: string,
    options: { timeoutMs?: number; intervalMs?: number } = {}
  ): Promise<any> {
    return waitForCondition(
      this.apiCall,
      (result) => result?.status === expectedStatus,
      { ...options, description: `${this.description} status=${expectedStatus}` }
    );
  }

  async waitForCount(
    expectedCount: number,
    options: { timeoutMs?: number; intervalMs?: number } = {}
  ): Promise<any[]> {
    return waitForCondition(
      this.apiCall,
      (result) => Array.isArray(result) && result.length >= expectedCount
        ? result
        : (result?.data?.length >= expectedCount ? result.data : undefined),
      { ...options, description: `${this.description} count>=${expectedCount}` }
    );
  }

  async waitForExists(
    options: { timeoutMs?: number; intervalMs?: number } = {}
  ): Promise<any> {
    return waitForCondition(
      this.apiCall,
      (result) => result !== null && result !== undefined && (Array.isArray(result) ? result.length > 0 : true),
      { ...options, description: `${this.description} exists` }
    );
  }
}

export function createAsyncWaiter(apiCall: () => Promise<any>, description: string): AsyncWaiter {
  return new AsyncWaiter(apiCall, description);
}