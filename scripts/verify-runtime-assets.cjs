const fs = require('node:fs');
const path = require('node:path');

function argument(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const root = path.resolve(argument('--root', path.resolve(__dirname, '..')));
const platform = argument('--platform', process.platform);
const arch = argument('--arch', process.arch);
const platformDir = platform === 'win32' ? 'win-x64' : platform === 'darwin' ? 'mac-arm64' : undefined;

if (!platformDir || (platform === 'darwin' && arch !== 'arm64') || (platform === 'win32' && arch !== 'x64')) {
  throw new Error(`Unsupported platform/architecture: ${platform}/${arch}`);
}

const jar = path.join(root, 'runtime', 'backend', 'qa-backend-0.1.0.jar');
const runtimeDir = path.join(root, 'runtime', 'jre', platformDir);
const java = path.join(runtimeDir, 'bin', platform === 'win32' ? 'java.exe' : 'java');
const metadata = path.join(runtimeDir, 'runtime.json');

if (!fs.existsSync(jar)) throw new Error(`Backend jar not found: ${jar}`);
if (!fs.existsSync(java)) throw new Error(`Java executable not found: ${java}`);
if (!fs.existsSync(metadata)) throw new Error(`Runtime metadata not found: ${metadata}`);

const runtime = JSON.parse(fs.readFileSync(metadata, 'utf8'));
if (runtime.platform !== platform) throw new Error(`Runtime platform mismatch: expected ${platform}, got ${runtime.platform}`);
if (runtime.arch !== arch) throw new Error(`Runtime architecture mismatch: expected ${arch}, got ${runtime.arch}`);
if (platform === 'darwin' && (fs.statSync(java).mode & 0o111) === 0) throw new Error(`Java executable is not executable: ${java}`);

console.log(JSON.stringify({ platform, arch, jar, java, valid: true }));
