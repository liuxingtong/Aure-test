$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$JreSource = if ($env:QA_JRE_SOURCE) { $env:QA_JRE_SOURCE } elseif ($env:JAVA_HOME) { $env:JAVA_HOME } else { '' }
$Java = Join-Path $JreSource 'bin\java.exe'
if (-not (Test-Path $Java)) { throw 'Set QA_JRE_SOURCE or JAVA_HOME to a Windows Java 17 runtime before packaging.' }

mvn -q -f (Join-Path $Root 'apps/backend/pom.xml') -DskipTests package
$BackendDir = Join-Path $Root 'runtime\backend'
$RuntimeDir = Join-Path $Root 'runtime\jre\win-x64'
New-Item -ItemType Directory -Force -Path $BackendDir | Out-Null
if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }
New-Item -ItemType Directory -Force -Path (Split-Path $RuntimeDir) | Out-Null
Copy-Item -Recurse $JreSource $RuntimeDir
Copy-Item (Join-Path $Root 'apps/backend/target/qa-backend-0.1.0.jar') (Join-Path $BackendDir 'qa-backend-0.1.0.jar') -Force
'{"platform":"win32","arch":"x64"}' | Set-Content (Join-Path $RuntimeDir 'runtime.json') -Encoding UTF8
Write-Host "Prepared Windows x64 backend and Java runtime under $Root\runtime"
