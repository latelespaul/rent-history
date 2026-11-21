#!/usr/bin/env node
// Renders every mockup to PNG with a local headless Chromium (Playwright).
// It serves rentrix-docs/design/ on 127.0.0.1 (random port), screenshots each
// page, then closes the browser and the server. Nothing keeps running.
//
//   cd rentrix-docs/design/tools && npm install && npm run render
//
// Output: rentrix-docs/design/screenshots/<name>-desktop.png (1440 wide) and
// <name>-mobile.png (390 wide) for the pages in MOBILE.
import { createServer } from 'node:http';
import { mkdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const root = normalize(join(here, '..'));
const outDir = join(root, 'screenshots');

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
];
const MOBILE = new Set(['home', 'property']);
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
    await page.screenshot({ path: file, fullPage: true });
    console.log(`  ${kind.padEnd(7)} ${name} -> ${file.slice(root.length + 1)}`);
    await ctx.close();
  }
} finally {
  await browser.close();
  server.close();
}
