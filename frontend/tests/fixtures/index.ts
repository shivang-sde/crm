import { test as authTest } from './auth.fixture';
import { test as tenantTest } from './tenant.fixture';
import { mergeTests } from '@playwright/test';

export const test = mergeTests(authTest, tenantTest);
export { expect } from '@playwright/test';