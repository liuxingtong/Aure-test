$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$BackendJar = Join-Path $Root 'apps/backend/target/qa-backend-0.1.0.jar'
$DesktopExe = Join-Path $Root 'apps/desktop/release/win-unpacked/问答桌面应用.exe'

if (-not (Test-Path $BackendJar)) {
  throw "Backend jar not found: $BackendJar. Run .\scripts\build-windows.ps1 first."
}
if (-not (Test-Path $DesktopExe)) {
  throw "Windows desktop executable not found: $DesktopExe. Run .\scripts\build-windows.ps1 first."
}

$envFile = Join-Path $Root '.env.local'
if (Test-Path $envFile) {
  Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)\s*=\s*(.*)\s*$') {
      [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
  }
}

$backend = Start-Process -FilePath 'java' -ArgumentList '-jar', $BackendJar -WorkingDirectory $Root -PassThru
try {
  for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try {
      Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/api/v1/health | Out-Null
      break
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  Start-Process -FilePath $DesktopExe -WorkingDirectory $Root
  Wait-Process -Id $backend.Id
} finally {
  if (-not $backend.HasExited) { Stop-Process -Id $backend.Id -Force }
}
