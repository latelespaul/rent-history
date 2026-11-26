#!/usr/bin/env node
// Writes design/icons/<name>.svg, design/icons/sprite.svg, design/icons/style-spec.json and
// design/icons/preview.html (self-contained: 24 px, 48 px and on dark) from icons/icons.js.
// Usage: node rentrix-docs/design/tools/build-icons.mjs
import { createRequire } from 'node:module';
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const iconsDir = join(here, '..', 'icons');
const require = createRequire(import.meta.url);
const { ICONS, PORTED } = require(join(iconsDir, 'icons.js'));

const ATTRS = 'fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"';
const paths = (name) => ICONS[name].map((d) => `  <path d="${d}"/>`).join('\n');

mkdirSync(iconsDir, { recursive: true });
for (const f of readdirSync(iconsDir)) {
  if (f.endsWith('.svg')) rmSync(join(iconsDir, f));
}

const names = Object.keys(ICONS).sort();
for (const name of names) {
  const file = `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" ${ATTRS}>\n${paths(name)}\n</svg>\n`;
  writeFileSync(join(iconsDir, `${name}.svg`), file);
}

const symbols = names
  .map((name) => `  <symbol id="rx-${name}" viewBox="0 0 24 24" ${ATTRS}>\n${paths(name).replace(/^ {2}/gm, '    ')}\n  </symbol>`)
  .join('\n');
writeFileSync(
  join(iconsDir, 'sprite.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" style="display:none">\n${symbols}\n</svg>\n`,
);

const spec = {
  name: 'doorway-living-icons',
  preset: 'clean (stroke 1.75)',
  grid: 24,
  strokeWidth: 1.75,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  cornerRadius: 2,
  padding: 2,
  opticalBalance: true,
  iconCount: names.length,
  icons: names,
  portedFromLucide: PORTED,
  licences: { own: 'project licence', lucide: 'ISC, see LICENSE-lucide' },
};
writeFileSync(join(iconsDir, 'style-spec.json'), JSON.stringify(spec, null, 2) + '\n');

const inline = (name, size) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" ${ATTRS} aria-hidden="true">${ICONS[name].map((d) => `<path d="${d}"/>`).join('')}</svg>`;
const grid = (size) =>
  names.map((n) => `<figure>${inline(n, size)}<figcaption>${n}${PORTED[n] ? ' *' : ''}</figcaption></figure>`).join('\n');
writeFileSync(
  join(iconsDir, 'preview.html'),
  `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Doorway Living icons</title>
<style>
  body { margin: 0; padding: 24px 16px; font: 14px/1.4 system-ui, sans-serif; background: #fbfaf7; color: #1c1917; }
  h1 { font-size: 22px; margin: 0 0 4px; } h2 { font-size: 15px; margin: 28px 0 10px; }
  p { margin: 0; color: #57534e; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 8px; }
  figure { margin: 0; padding: 14px 4px 10px; border: 1px solid #e7e5e4; border-radius: 12px; background: #fff; text-align: center; }
  figcaption { margin-top: 8px; font: 11px ui-monospace, monospace; color: #57534e; overflow-wrap: anywhere; }
  .dark { background: #134e4a; padding: 12px; border-radius: 16px; }
  .dark figure { background: transparent; border-color: rgb(255 255 255 / 0.15); color: #fff; }
  .dark figcaption { color: #c4e4df; }
</style>
</head>
<body>
<h1>Doorway Living icons</h1>
<p>${names.length} icons · ${spec.grid} px grid · stroke ${spec.strokeWidth} · round caps and joins · padding ${spec.padding} px · currentColor. * = ported from Lucide (ISC), redrawn on this grid.</p>
<h2>24 px</h2>
<div class="grid">${grid(24)}</div>
<h2>48 px</h2>
<div class="grid">${grid(48)}</div>
<h2>On dark</h2>
<div class="grid dark">${grid(24)}</div>
</body>
</html>
`,
);

console.log(`wrote ${names.length} icons + sprite.svg + style-spec.json + preview.html to ${iconsDir}`);
