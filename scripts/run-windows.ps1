$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$DesktopExe = Join-Path $Root 'apps/desktop/release/win-unpacked/yunhe.exe'

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

Start-Process -FilePath $DesktopExe -WorkingDirectory $Root
