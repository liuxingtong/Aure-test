$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

npm install
& (Join-Path $PSScriptRoot 'prepare-backend-runtime.ps1')
npm --workspace apps/desktop run package:win
Write-Host 'Windows build completed. The packaged app starts Spring Boot automatically.'
