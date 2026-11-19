// Poster size must visibly change posters at every screen size (web 0.3.13).
// Regression: applyPosterSize set a fixed --poster-w (140/180/220px) while Medium used the
// responsive --card-w (180/150/128px). On tablets (640-1023px) Small sat within 10px of
// Medium, and on phones (<=639px) Small was LARGER than Medium, so changing the setting
// looked like it did nothing. The size now lives on a data attribute and styles.css scales
// Small/Large with the responsive Medium at each breakpoint, keeping the 140/180 and
// 220/180 desktop ratios.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(path.join(__dirname, '..', 'public', 'app.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '..', 'public', 'styles.css'), 'utf8');

// Exercise the real applyPosterSize from app.js with a stubbed document.
const m = src.match(/const POSTER_SIZES[\s\S]*?function applyPosterSize\(\)[\s\S]*?\n}\n/);
if (!m) throw new Error('applyPosterSize not found in app.js');
const el = { dataset: {} };
globalThis.document = { documentElement: el };
globalThis.state = { settings: null };
eval(m[0] + '\nglobalThis.applyPosterSize = applyPosterSize;');

test('applyPosterSize reflects the setting on the root data attribute', () => {
  globalThis.state.settings = { posterSize: 'Small' };
  applyPosterSize();
  assert.strictEqual(el.dataset.posterSize, 'Small');
  globalThis.state.settings = { posterSize: 'Large' };
  applyPosterSize();
  assert.strictEqual(el.dataset.posterSize, 'Large');
  globalThis.state.settings = { posterSize: 'Medium' };
  applyPosterSize();
  assert.strictEqual(el.dataset.posterSize, 'Medium');
});

test('applyPosterSize defaults to Medium and rejects junk values', () => {
  globalThis.state.settings = null;
  applyPosterSize();
  assert.strictEqual(el.dataset.posterSize, 'Medium');
  globalThis.state.settings = { posterSize: 'Huge' };
  applyPosterSize();
  assert.strictEqual(el.dataset.posterSize, 'Medium');
});

test('the px widths come from CSS per breakpoint, not inline styles', () => {
  assert.ok(!src.includes("style.setProperty('--poster-w'"), 'app.js must not set --poster-w inline');
  assert.ok(!src.includes('POSTER_WIDTHS'), 'fixed px table must be gone from app.js');
  assert.ok(src.includes('document.documentElement.dataset.posterSize'), 'applyPosterSize must set the data attribute');
});

function widths(block) {
  const card = block.match(/--card-w: (\d+)px/);
  const small = block.match(/:root\[data-poster-size="Small"\] \{ --poster-w: (\d+)px; \}/);
  const large = block.match(/:root\[data-poster-size="Large"\] \{ --poster-w: (\d+)px; \}/);
  return {
    medium: card ? Number(card[1]) : null,
    small: small ? Number(small[1]) : null,
    large: large ? Number(large[1]) : null,
  };
}

test('Small < Medium < Large at every breakpoint, ratios preserved', () => {
  // Base rules: --card-w lives in the first :root block; Small/Large rules follow .grid.
  const baseRoot = css.match(/:root \{[\s\S]*?\n\}/)[0];
  const base = { medium: widths(baseRoot).medium, small: widths(css).small, large: widths(css).large };
  const blocks = [...css.matchAll(/@media \(max-width: (1023|639)px\) \{([\s\S]*?)\n\}/g)];
  const byWidth = {};
  for (const b of blocks) {
    const w = widths(b[0]);
    const cur = byWidth[b[1]] || (byWidth[b[1]] = {});
    for (const k of ['small', 'medium', 'large']) if (w[k] != null) cur[k] = w[k];
  }
  const tablet = { medium: byWidth['1023'].medium, small: byWidth['1023'].small, large: byWidth['1023'].large };
  const phone = { medium: byWidth['639'].medium, small: byWidth['639'].small, large: byWidth['639'].large };
  for (const [name, bp] of [['base', base], ['tablet', tablet], ['phone', phone]]) {
    assert.ok(bp.small && bp.medium && bp.large, `${name} defines all three: ${JSON.stringify(bp)}`);
    assert.ok(bp.small < bp.medium && bp.medium < bp.large, `${name}: Small < Medium < Large (${JSON.stringify(bp)})`);
    // Steps stay clearly visible: at least ~15% between sizes, matching the desktop design.
    assert.ok(bp.small <= bp.medium * 0.85, `${name}: Small is at least 15% below Medium`);
    assert.ok(bp.large >= bp.medium * 1.15, `${name}: Large is at least 15% above Medium`);
  }
});
