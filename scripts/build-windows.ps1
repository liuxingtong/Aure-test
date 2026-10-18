$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

npm install
npm --workspace apps/desktop run package:win
$env:JAVA_HOME = $env:JAVA_HOME
mvn -f apps/backend/pom.xml -DskipTests package
Write-Host 'Windows build completed. Run .\scripts\run-windows.ps1 to launch both services.'
