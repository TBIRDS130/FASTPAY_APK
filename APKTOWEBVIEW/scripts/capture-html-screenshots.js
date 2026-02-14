/**
 * Capture screenshots of hand-written HTML demos using Puppeteer.
 * Output: outputs/html/*.png
 * Targets: demos/*.html (output.html, activate-animation-demo.html, etc.)
 */

const path = require('path');
const fs = require('fs');
const puppeteer = require('puppeteer');

const ROOT = path.resolve(__dirname, '..');
const DEMOS_DIR = path.join(ROOT, 'demos');
const OUTPUT_DIR = path.join(ROOT, 'outputs', 'html');
const VIEWPORT = { width: 375, height: 812 };

async function capture() {
  if (!fs.existsSync(DEMOS_DIR)) {
    console.error('demos/ not found.');
    process.exit(1);
  }
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }
  const htmlFiles = fs.readdirSync(DEMOS_DIR)
    .filter((f) => f.endsWith('.html') && !f.startsWith('.'));
  const browser = await puppeteer.launch({ headless: 'new' });
  for (const file of htmlFiles) {
    const name = path.basename(file, '.html');
    const htmlPath = path.join(DEMOS_DIR, file);
    const page = await browser.newPage();
    await page.setViewport(VIEWPORT);
    await page.goto('file://' + htmlPath, { waitUntil: 'networkidle0' });
    await page.screenshot({ path: path.join(OUTPUT_DIR, name + '.png') });
    await page.close();
    console.log('Captured', name + '.png');
  }
  await browser.close();
  console.log('Done. Output:', OUTPUT_DIR);
}

capture().catch((err) => {
  console.error(err);
  process.exit(1);
});
