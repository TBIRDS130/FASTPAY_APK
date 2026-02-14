#!/usr/bin/env node
/**
 * apk-to-webview CLI
 * Usage: npx apk-to-webview extract [options]
 *   extract  - Run extract-apk-values.ps1 (generates apk-config.js)
 */

const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');

const ROOT = path.resolve(__dirname);

function isWindows() {
  return process.platform === 'win32';
}

function runPowerShell(scriptPath, args = []) {
  const pwsh = isWindows() ? 'powershell.exe' : 'pwsh';
  const execArgs = ['-ExecutionPolicy', 'Bypass', '-File', scriptPath, ...args];
  return new Promise((resolve, reject) => {
    const proc = spawn(pwsh, execArgs, {
      cwd: ROOT,
      stdio: 'inherit',
      shell: true,
    });
    proc.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`Exit ${code}`))));
  });
}

function showHelp() {
  console.log(`
apk-to-webview - APK to Web/HTML tooling (extract only; HTML is hand-written)

Usage: npx apk-to-webview extract [options]

Commands:
  extract              Generate apk-config.js from APK resources
    --apk-root PATH    APK project root (default: ../FASTPAY_BASE)
    --output PATH      Output path for apk-config.js

Examples:
  npx apk-to-webview extract
  npx apk-to-webview extract --apk-root ../FASTPAY_BASE
`);
}

async function main() {
  const args = process.argv.slice(2);
  const cmd = args[0];
  const rest = args.slice(1);

  if (!cmd || cmd === '--help' || cmd === '-h') {
    showHelp();
    process.exit(0);
  }

  const getArg = (name) => {
    const i = rest.indexOf(name);
    if (i >= 0 && rest[i + 1]) return rest[i + 1];
    return null;
  };

  const flatArgs = [];
  const apkRoot = getArg('--apk-root');
  const output = getArg('--output');
  if (apkRoot) flatArgs.push('-ApkRoot', apkRoot);
  if (output) flatArgs.push('-OutputPath', output);

  if (cmd === 'extract') {
    const script = path.join(ROOT, 'extract-apk-values.ps1');
    if (!fs.existsSync(script)) {
      console.error('extract-apk-values.ps1 not found');
      process.exit(1);
    }
    await runPowerShell(script, flatArgs);
    return;
  }

  console.error(`Unknown command: ${cmd}`);
  showHelp();
  process.exit(1);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
