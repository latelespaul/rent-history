#!/usr/bin/env node
// Writes design/icons/<name>.svg and design/icons/sprite.svg from icons/icons.js.
// Usage: node rentrix-docs/design/tools/build-icons.mjs
import { createRequire } from 'node:module';
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const iconsDir = join(here, '..', 'icons');
const require = createRequire(import.meta.url);
const { ICONS } = require(join(iconsDir, 'icons.js'));

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

console.log(`wrote ${names.length} icons + sprite.svg to ${iconsDir}`);
