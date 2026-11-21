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
// With --copy-v3 it copies the design-v3 renders (V3 below) to
// rentrix-docs/screenshots/design-v3/. With --only-v3 it renders only the V3
// pages, so unchanged screenshots are not rewritten.
import { createServer } from 'node:http';
import { copyFileSync, mkdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const root = normalize(join(here, '..'));
const outDir = join(root, 'screenshots');
const v2Dir = join(root, '..', 'screenshots', 'design-v2');
const v3Dir = join(root, '..', 'screenshots', 'design-v3');

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
  // design v3: identity verification (any one of Aadhaar, PAN, phone OTP), location hierarchy, roll-ups
  ['verify-phone', 'mockups/verify-phone.html'],
  ['verify-aadhaar', 'mockups/verify-aadhaar.html'],
  ['browse-areas', 'mockups/browse-areas.html'],
  ['browse-societies', 'mockups/browse-societies.html'],
  ['society', 'mockups/society.html'],
];
const MOBILE = new Set(['home', 'property', 'home-many-cities', 'verify-id', 'verify-rejected', 'verify-phone', 'society']);
// New or changed in design v2 (copied to rentrix-docs/screenshots/design-v2/ with --copy-v2).
const V2 = new Set(['home', 'search', 'onboarding', 'home-many-cities', 'city-picker', 'verify-profile', 'verify-linkedin', 'verify-id',
  'verify-agreement', 'verify-pending', 'verify-rejected', 'verify-approved', 'write-review-pending', 'admin-dashboard',
  'admin-approvals', 'admin-records', 'admin-record', 'admin-cities']);
const copyV2 = process.argv.includes('--copy-v2');
// New or changed in design v3 (copied to rentrix-docs/screenshots/design-v3/ with --copy-v3).
const V3 = new Set(['search', 'property', 'landlord-profile', 'tenant-profile', 'verify-profile', 'verify-id', 'verify-linkedin',
  'verify-agreement', 'verify-pending', 'verify-rejected', 'verify-approved', 'admin-approvals', 'admin-record',
  'verify-phone', 'verify-aadhaar', 'browse-areas', 'browse-societies', 'society']);
const copyV3 = process.argv.includes('--copy-v3');
const onlyV3 = process.argv.includes('--only-v3');
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
    if (onlyV3 && !V3.has(name)) continue;
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
    if (copyV3 && V3.has(name)) {
      mkdirSync(v3Dir, { recursive: true });
      copyFileSync(file, join(v3Dir, `${name}-${kind}.png`));
    }
    await ctx.close();
  }
} finally {
  await browser.close();
  server.close();
}
