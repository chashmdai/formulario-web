import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
const require = createRequire(new URL('../../formulario-publico/front/package.json', import.meta.url));
const { chromium } = require('playwright');
const root = fileURLToPath(new URL('../../', import.meta.url));
const output = process.env.PARITY_OUTPUT || path.join(root, '.local/evidencias/paridad');
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_CHANNEL ? {channel: process.env.BROWSER_CHANNEL} : {}) });
const results = [];
try {
  for (const [size, width, height] of [['escritorio', 1440, 900], ['movil', 390, 844]]) {
    for (const [kind, url] of [['mockup', process.env.MOCKUP_URL || 'http://127.0.0.1:8181/'], ['react', process.env.REACT_URL || 'http://localhost:5173/?vista=inicio']]) {
      const context = await browser.newContext({viewport: {width, height}, deviceScaleFactor: 1});
      const page = await context.newPage();
      const errors = []; page.on('pageerror', e => errors.push(e.message));
      await page.goto(url, {waitUntil: 'networkidle', timeout: 30000});
      await page.evaluate(() => document.fonts.ready);
      for (const state of ['inicio', 'acceso', 'privacidad', 'alternativas', 'reeleccion']) {
        if (state === 'acceso') await page.locator('#start-adult').click();
        if (state === 'privacidad') await page.locator('#use-manual').click();
        if (state === 'alternativas') {
          await page.locator('input[name="privacy-decision"][value="no"]').check();
          await page.locator('#privacy-continue').click();
        }
        if (state === 'reeleccion') await page.locator('#privacy-rechoose').click();
        const png = await page.screenshot({path: path.join(output, `${kind}-${size}-${state}.png`), fullPage: true});
        const geometry = await page.evaluate(() => Object.fromEntries(['.site-header', '#landing-title', '.audience-grid', '.audience-card', '.page-footer', '#adult-access', '#privacy-access'].map(selector => {
          const element = document.querySelector(selector);
          if (!element) return [selector, null];
          const rect = element.getBoundingClientRect(), css = getComputedStyle(element);
          return [selector, {x: rect.x, y: rect.y, width: rect.width, height: rect.height, font: css.fontFamily, fontSize: css.fontSize}];
        })));
        const privacyText = await page.locator('#privacy-access').count() ? await page.locator('#privacy-access').innerText() : null;
        results.push({kind, size, state, width, height, geometry, privacyText, errors, pngSha256: createHash('sha256').update(png).digest('hex')});
        console.log(`${kind} ${size} ${state}: capturado`);
      }
      await context.close();
    }
  }
  await fs.writeFile(path.join(output, 'geometria.json'), JSON.stringify(results, null, 2));
  for (const size of ['escritorio', 'movil']) {
    const pair = results.filter(r => r.size === size && r.state === 'inicio');
    console.log(`${size}: inicio PNG idéntico=${pair[0].pngSha256 === pair[1].pngSha256}`);
  }
  if (results.some(r => r.errors.length)) process.exitCode = 1;
} finally { await browser.close(); }
