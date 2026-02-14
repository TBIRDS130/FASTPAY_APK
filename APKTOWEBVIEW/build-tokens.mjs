/**
 * Token-first build: design tokens (tokens/*.json) → Android resources + web apk-config.js
 * Run: npm run build:tokens
 */
import StyleDictionary from 'style-dictionary';
import { copyFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname);

function escapeXml(str) {
  if (typeof str !== 'string') return str;
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

StyleDictionary.registerFormat({
  name: 'android/colors-theme',
  format: ({ dictionary }) => {
    const tokens = dictionary.allTokens.filter((t) => Array.isArray(t.path) && t.path[0] === 'color');
    const lines = tokens.map((t) => {
      const name = t.path.slice(1).join('_');
      const value = typeof t.value === 'string' ? t.value : t.value;
      return `    <color name="${name}">${escapeXml(value)}</color>`;
    });
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + lines.join('\n') + '\n</resources>\n';
  },
});

function sizeValue(token) {
  const v = token.original?.value ?? token.value;
  if (typeof v === 'object' && v && v.unit != null) return `${v.value}${v.unit}`;
  const name = (token.path && token.path[token.path.length - 1]) || '';
  return String(token.value) + (name.includes('text_size') || name.includes('_size') ? 'sp' : 'dp');
}

StyleDictionary.registerFormat({
  name: 'android/dimens-theme',
  format: ({ dictionary }) => {
    const tokens = dictionary.allTokens.filter((t) => Array.isArray(t.path) && t.path[0] === 'size');
    const lines = tokens.map((t) => {
      const name = t.path.slice(1).join('_');
      const value = sizeValue(t);
      return `    <dimen name="${name}">${escapeXml(value)}</dimen>`;
    });
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + lines.join('\n') + '\n</resources>\n';
  },
});

StyleDictionary.registerFormat({
  name: 'android/strings-activation',
  format: ({ dictionary }) => {
    const tokens = dictionary.allTokens.filter((t) => Array.isArray(t.path) && t.path[0] === 'string');
    const lines = tokens.map((t) => {
      const name = t.path.slice(1).join('_');
      const value = escapeXml(String(t.value));
      return `    <string name="${name}">${value}</string>`;
    });
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + lines.join('\n') + '\n</resources>\n';
  },
});

StyleDictionary.registerFormat({
  name: 'javascript/apk-config',
  format: ({ dictionary }) => {
    const colors = {};
    const strings = {};
    const dimens = {};
    const animation = {};
    for (const t of dictionary.allTokens) {
      if (!Array.isArray(t.path) || t.path.length < 2) continue;
      const key = t.path.slice(1).join('_');
      const v = t.value;
      if (t.path[0] === 'color') colors[key] = typeof v === 'string' ? v : v;
      else if (t.path[0] === 'string') strings[key] = String(v);
      else if (t.path[0] === 'size') {
        dimens[key] = sizeValue(t);
      } else if (t.path[0] === 'animation') animation[key] = typeof v === 'number' ? v : Number(v);
    }
    const config = { colors, strings, dimens, animation };
    return (
      '/**\n * APK Config - Generated from design tokens (token-first)\n * Source: tokens/**/*.json\n * Regenerate: npm run build:tokens\n */\nconst APK_CONFIG = ' +
      JSON.stringify(config, null, 2) +
      ';\n'
    );
  },
});

const config = {
  source: ['tokens/**/*.json'],
  platforms: {
    android: {
      transformGroup: 'android',
      buildPath: 'build/tokens/android/',
      files: [
        { destination: 'colors_theme.xml', format: 'android/colors-theme' },
        { destination: 'dimens_theme.xml', format: 'android/dimens-theme' },
        { destination: 'strings_activation.xml', format: 'android/strings-activation' },
      ],
    },
    web: {
      transformGroup: 'js',
      buildPath: 'build/tokens/web/',
      files: [{ destination: 'apk-config.js', format: 'javascript/apk-config' }],
    },
  },
};

const sd = new StyleDictionary(config);
await sd.buildAllPlatforms();

// Copy web output to APKTOWEBVIEW for HTML demo (demos/ loads ../apk-config.js)
copyFileSync(join(root, 'build', 'tokens', 'web', 'apk-config.js'), join(root, 'apk-config.js'));
console.log('Copied build/tokens/web/apk-config.js → apk-config.js');
console.log('Done. HTML demos use apk-config.js from APKTOWEBVIEW.');
