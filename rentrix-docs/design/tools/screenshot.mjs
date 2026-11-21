// Captures every mockup into screenshots/desktop (1280px) and, for the key
// screens, screenshots/mobile (390px). Full-page PNGs.
//
// Needs playwright-core and a Chromium (headless shell is enough). Neither is
// a repo dependency; install them outside the repo, for example:
//   npm i --prefix /tmp/rx-shot playwright-core
//   PW_CORE=/tmp/rx-shot/node_modules/playwright-core/index.js \
//   CHROMIUM=~/.cache/ms-playwright/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell \
//   node rentrix-docs/design/tools/screenshot.mjs
import { join, dirname } from 'node:path';
import { mkdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

const pw = await import(process.env.PW_CORE || 'playwright-core');
const { chromium } = pw.chromium ? pw : pw.default;
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const mockups = join(root, 'mockups');

// [output name, mockup file + optional #variant, also capture mobile?]
const shots = [
  ['01-home', 'home.html', true],
  ['02-search-results', 'search-results.html', true],
  ['03-property', 'property.html', true],
  ['04-landlord-profile', 'landlord-profile.html', false],
  ['05-tenant-profile', 'tenant-profile.html', true],
  ['06a-write-review-tenant-landlord', 'write-review.html#tenant-landlord', true],
  ['06b-write-review-tenant-property', 'write-review.html#tenant-property', false],
  ['06c-write-review-landlord-tenant', 'write-review.html#landlord-tenant', false],
  ['07-my-tenancies', 'my-tenancies.html', true],
  ['08-landlord-dashboard', 'landlord-dashboard.html', true],
  ['09-signup', 'signup.html', true],
  ['10-login', 'login.html', false],
  ['00-icon-set', 'icons.html', false]
];

const only = process.argv.slice(2);
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || undefined });
const sizes = [
  ['desktop', { width: 1280, height: 800 }, 1],
  ['mobile', { width: 390, height: 844 }, 2]
];
for (const [dir, viewport, scale] of sizes) {
  mkdirSync(join(root, 'screenshots', dir), { recursive: true });
  const ctx = await browser.newContext({ viewport, deviceScaleFactor: scale });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => { console.error('page error:', e.message); process.exitCode = 1; });
  for (const [name, file, mobile] of shots) {
    if (dir === 'mobile' && !mobile) continue;
    if (only.length && !only.includes(name)) continue;
    const [path, hash] = file.split('#');
    const url = pathToFileURL(join(mockups, path)).href + (hash ? '#' + hash : '');
    await page.goto('about:blank'); // a hash-only change would not reload
    await page.goto(url, { waitUntil: 'load' });
    const out = join(root, 'screenshots', dir, name + '.png');
    await page.screenshot({ path: out, fullPage: true });
    console.log(dir, name);
  }
  await ctx.close();
}
await browser.close();
