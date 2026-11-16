$ErrorActionPreference = "Stop"

Write-Host "Running OH verification..." -ForegroundColor Cyan
& .\gradlew.bat clean testTvDebugUnitTest lintTvDebug assembleTvDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Verification passed." -ForegroundColor Green
Write-Host "TV APK: app\build\outputs\apk\tv\debug\app-tv-debug.apk"
