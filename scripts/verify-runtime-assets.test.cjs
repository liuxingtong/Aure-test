const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const test = require('node:test');

const verifier = path.resolve(__dirname, 'verify-runtime-assets.cjs');

function runVerifier(root, platform, arch) {
  return () => execFileSync(process.execPath, [verifier, '--root', root, '--platform', platform, '--arch', arch], { encoding: 'utf8' });
}

test('fails when the backend jar is missing', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'qa-runtime-'));
  fs.mkdirSync(path.join(root, 'runtime', 'jre', 'mac-arm64', 'bin'), { recursive: true });
  fs.writeFileSync(path.join(root, 'runtime', 'jre', 'mac-arm64', 'bin', 'java'), 'java');
  assert.throws(runVerifier(root, 'darwin', 'arm64'), /backend jar/i);
});

test('fails when the platform java executable is missing', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'qa-runtime-'));
  fs.mkdirSync(path.join(root, 'runtime', 'backend'), { recursive: true });
  fs.writeFileSync(path.join(root, 'runtime', 'backend', 'qa-backend-0.1.0.jar'), 'jar');
  assert.throws(runVerifier(root, 'win32', 'x64'), /java executable/i);
});

test('fails when runtime metadata does not match the requested platform', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'qa-runtime-'));
  const runtimeDir = path.join(root, 'runtime', 'jre', 'mac-arm64');
  fs.mkdirSync(path.join(root, 'runtime', 'backend'), { recursive: true });
  fs.mkdirSync(path.join(runtimeDir, 'bin'), { recursive: true });
  fs.writeFileSync(path.join(root, 'runtime', 'backend', 'qa-backend-0.1.0.jar'), 'jar');
  fs.writeFileSync(path.join(runtimeDir, 'bin', 'java'), 'java');
  fs.writeFileSync(path.join(runtimeDir, 'runtime.json'), JSON.stringify({ platform: 'darwin', arch: 'x64' }));
  assert.throws(runVerifier(root, 'darwin', 'arm64'), /architecture/i);
});
