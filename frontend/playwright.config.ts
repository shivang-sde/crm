import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';

dotenv.config({ path: '.env.test' });

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000';
const apiBaseURL = process.env.PLAYWRIGHT_API_BASE_URL || 'http://localhost:8080/api/v1';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
  ],
  timeout: 90_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 30_000,
    navigationTimeout: 30_000,
    extraHTTPHeaders: {
      'Accept-Language': 'en-US,en;q=0.9',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // webServer disabled for live run — frontend already running on :3000 (see next.log), backend on :8080
  // webServer: {
  //   command: 'npm run dev',
  //   url: `${baseURL}/sign-in`,
  //   reuseExistingServer: true,
  //   timeout: 120_000,
  //   env: { ...process.env, PORT: '3000' },
  // },
  metadata: {
    apiBaseURL,
    testEnvironment: process.env.NODE_ENV || 'development',
  },
});