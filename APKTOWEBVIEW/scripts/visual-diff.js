/**
 * Visual regression: compare outputs/html/*.png to baselines/apk/*.png
 * Uses pixelmatch. Run: npm run test:visual
 */

const path = require('path');
const fs = require('fs');
const { PNG } = require('pngjs');
const pixelmatch = require('pixelmatch');

const ROOT = path.resolve(__dirname, '..');
const BASELINES = path.join(ROOT, 'baselines', 'html');
const OUTPUTS = path.join(ROOT, 'outputs', 'html');
const DIFF_OUT = path.join(ROOT, 'outputs', 'diff');
const THRESHOLD = 0.05; // 5% pixel difference allowed

function loadPng(p) {
  const data = fs.readFileSync(p);
  return PNG.sync.read(data);
}

function compare(name) {
  const basePath = path.join(BASELINES, name + '.png');
  const outPath = path.join(OUTPUTS, name + '.png');
  if (!fs.existsSync(basePath)) {
    return { ok: false, error: 'No baseline' };
  }
  if (!fs.existsSync(outPath)) {
    return { ok: false, error: 'No output (run capture first)' };
  }
  const img1 = loadPng(basePath);
  const img2 = loadPng(outPath);
  if (img1.width !== img2.width || img1.height !== img2.height) {
    return { ok: false, error: `Size mismatch ${img1.width}x${img1.height} vs ${img2.width}x${img2.height}` };
  }
  const { width, height } = img1;
  const diff = new PNG({ width, height });
  const numDiff = pixelmatch(img1.data, img2.data, diff.data, width, height, { threshold: 0.1 });
  const total = width * height;
  const ratio = numDiff / total;
  if (!fs.existsSync(DIFF_OUT)) fs.mkdirSync(DIFF_OUT, { recursive: true });
  fs.writeFileSync(path.join(DIFF_OUT, name + '-diff.png'), PNG.sync.write(diff));
  return { ok: ratio <= THRESHOLD, numDiff, ratio };
}

async function main() {
  if (!fs.existsSync(BASELINES)) {
    console.log('No baselines/html/ found. Copy outputs/html/*.png to baselines/html/ to create baselines.');
    process.exit(0);
  }
  if (!fs.existsSync(OUTPUTS)) {
    console.log('No outputs/html/ found. Run: node scripts/capture-html-screenshots.js');
    process.exit(1);
  }
  const names = fs.readdirSync(BASELINES)
    .filter((f) => f.endsWith('.png'))
    .map((f) => path.basename(f, '.png'));
  let failed = 0;
  for (const name of names) {
    const r = compare(name);
    if (r.ok) {
      console.log('OK', name);
    } else {
      console.error('FAIL', name, r.error || `${(r.ratio * 100).toFixed(2)}% diff`);
      failed++;
    }
  }
  if (failed > 0) process.exit(1);
  console.log('All visual tests passed.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
