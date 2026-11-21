#!/usr/bin/env node
// Renders every mockup to PNG with a local headless Chromium (Playwright).
// It serves rentrix-docs/design/ on 127.0.0.1 (random port), screenshots each
// page, then closes the browser and the server. Nothing keeps running.
//
//   cd rentrix-docs/design/tools && npm install && npm run render
//
// Output: rentrix-docs/design/screenshots/<name>-desktop.png (1440 wide) and
// <name>-mobile.png (390 wide) for the pages in MOBILE. With --copy-v2 it also
// copies the design-v2 renders (V2 below) to rentrix-docs/screenshots/design-v2/.
import { createServer } from 'node:http';
import { copyFileSync, mkdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const root = normalize(join(here, '..'));
const outDir = join(root, 'screenshots');
const v2Dir = join(root, '..', 'screenshots', 'design-v2');

const PAGES = [
  ['home', 'mockups/home.html'],
  ['search', 'mockups/search.html'],
  ['property', 'mockups/property.html'],
  ['landlord-profile', 'mockups/landlord-profile.html'],
  ['tenant-profile', 'mockups/tenant-profile.html'],
  ['write-review', 'mockups/write-review.html'],
  ['my-tenancies', 'mockups/my-tenancies.html'],
  ['landlord-dashboard', 'mockups/landlord-dashboard.html'],
  ['login', 'mockups/login.html'],
  ['onboarding', 'mockups/onboarding.html'],
  ['icons', 'icons.html'],
  // design v2: data-driven cities, verification with approvals, admin console
  ['home-many-cities', 'mockups/home-many-cities.html'],
  ['city-picker', 'mockups/city-picker.html'],
  ['verify-profile', 'mockups/verify-profile.html'],
  ['verify-linkedin', 'mockups/verify-linkedin.html'],
  ['verify-id', 'mockups/verify-id.html'],
  ['verify-agreement', 'mockups/verify-agreement.html'],
  ['verify-pending', 'mockups/verify-pending.html'],
  ['verify-rejected', 'mockups/verify-rejected.html'],
  ['verify-approved', 'mockups/verify-approved.html'],
  ['write-review-pending', 'mockups/write-review-pending.html'],
  ['admin-dashboard', 'mockups/admin-dashboard.html'],
  ['admin-approvals', 'mockups/admin-approvals.html'],
  ['admin-records', 'mockups/admin-records.html'],
  ['admin-record', 'mockups/admin-record.html'],
  ['admin-cities', 'mockups/admin-cities.html'],
];
const MOBILE = new Set(['home', 'property', 'home-many-cities', 'verify-id', 'verify-rejected']);
// New or changed in design v2 (copied to rentrix-docs/screenshots/design-v2/ with --copy-v2).
const V2 = new Set(['home', 'search', 'onboarding', ...PAGES.slice(PAGES.findIndex(([n]) => n === 'home-many-cities')).map(([n]) => n)]);
const copyV2 = process.argv.includes('--copy-v2');
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png' };

const server = createServer((req, res) => {
  const path = normalize(join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname)));
  if (!path.startsWith(root) || !existsSync(path) || statSync(path).isDirectory()) {
    res.writeHead(404).end('not found');
    return;
  }
  res.writeHead(200, { 'content-type': TYPES[extname(path)] || 'application/octet-stream' });
  res.end(readFileSync(path));
});

await new Promise((ok) => server.listen(0, '127.0.0.1', ok));
const base = `http://127.0.0.1:${server.address().port}`;
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({ headless: true });
try {
  const shots = [];
  for (const [name, path] of PAGES) {
    shots.push([name, path, 'desktop', { width: 1440, height: 900 }, 1]);
    if (MOBILE.has(name)) shots.push([name, path, 'mobile', { width: 390, height: 844 }, 2]);
  }
  for (const [name, path, kind, viewport, scale] of shots) {
    const ctx = await browser.newContext({ viewport, deviceScaleFactor: scale });
    const page = await ctx.newPage();
    await page.goto(`${base}/${path}`, { waitUntil: 'networkidle' });
    // Sticky bars repeat in full-page shots; pin them for the capture.
    await page.addStyleTag({ content: '.topbar,.sticky{position:static!important}' });
    const file = join(outDir, `${name}-${kind}.png`);
    // Generous timeout: on a busy machine a full-page capture can take 30 s+.
    await page.screenshot({ path: file, fullPage: true, timeout: 180000 });
    console.log(`  ${kind.padEnd(7)} ${name} -> ${file.slice(root.length + 1)}`);
    if (copyV2 && V2.has(name)) {
      mkdirSync(v2Dir, { recursive: true });
      copyFileSync(file, join(v2Dir, `${name}-${kind}.png`));
    }
    await ctx.close();
  }
} finally {
  await browser.close();
  server.close();
}
