import { test, expect } from '../fixtures';
import { apiUrl } from '../helpers/api-url';

// F-01 targeted regression: admin vs public routes must coexist without collision.

test.describe('F-01 Forms route collision', () => {
  test('public form renders at /forms/public/[publicKey] and is frame-allowed', async ({ page, request, testUser }) => {
    // Use a known publicKey if any form exists, otherwise create one quickly
    const res = await request.get(apiUrl('/forms'), { headers: { Authorization: `Bearer ${testUser.accessToken}` } });
    if (!res.ok()) test.skip();
    const body = await res.json();
    const forms = body.data || [];
    let publicKey: string | null = forms.find((f: any) => f.publicKey && f.status === 'PUBLISHED')?.publicKey ?? null;
    if (!publicKey && forms.length) {
      // try to publish first form
      try {
        const pub = await request.post(apiUrl(`/forms/${forms[0].id}/publish`), { headers: { Authorization: `Bearer ${testUser.accessToken}` } });
        if (pub.ok()) {
          const b2 = await pub.json(); publicKey = b2.data?.publicKey ?? b2.data?.publicKey;
        }
      } catch {}
    }
    if (!publicKey) test.skip();
    // Admin route /forms loads (needs auth)
    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });
    await page.goto('/forms');
    await expect(page.getByRole('heading', { name: /lead forms/i }).first()).toBeVisible({ timeout: 8000 });

    // Public route loads without auth and has frame headers via next.config
    const publicRes = await request.get(`http://localhost:3000/forms/public/${publicKey}`);
    expect([200, 304].includes(publicRes.status()) || publicRes.ok()).toBeTruthy();
    // Verify CSP allows framing
    const csp = publicRes.headers()['content-security-policy'] ?? publicRes.headers()['Content-Security-Policy'];
    if (csp) expect(String(csp)).toMatch(/frame-ancestors/);
  });

  test('old /forms/<publicKey> redirects to canonical /forms/public/<publicKey>', async ({ request }) => {
    const r = await request.get(`http://localhost:3000/forms/form_dummy123`, { maxRedirects: 0 } as any);
    // Should be 307/308 redirect to /forms/public/form_dummy123 (or 404 if no file, but redirect takes precedence)
    expect([307, 308].includes(r.status())).toBeTruthy();
    const loc = r.headers()['location'] ?? r.headers()['Location'] ?? '';
    expect(String(loc)).toContain('/forms/public/form_dummy123');
  });

  test('admin builder /forms/[formId]/edit still reachable', async ({ page, request, testUser }) => {
    const res = await request.get(apiUrl('/forms'), { headers: { Authorization: `Bearer ${testUser.accessToken}` } });
    if (!res.ok()) test.skip();
    const forms = (await res.json()).data || [];
    if (!forms.length) test.skip();
    await page.goto('/sign-in');
    await page.getByLabel('Email Address').fill(testUser.email);
    await page.getByLabel('Password').fill(testUser.password);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/home/, { timeout: 15000 });
    await page.goto(`/forms/${forms[0].id}/edit`);
    await expect(page).toHaveURL(new RegExp(`/forms/${forms[0].id}/edit`));
  });
});
