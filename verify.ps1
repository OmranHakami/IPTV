$ErrorActionPreference = "Stop"

Write-Host "Running OH verification..." -ForegroundColor Cyan
& .\gradlew.bat clean testTvDebugUnitTest testTabletDebugUnitTest lintTvDebug lintTabletDebug assembleTvDebug assembleTabletDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Verification passed." -ForegroundColor Green
Write-Host "TV APK: app\build\outputs\apk\tv\debug\app-tv-debug.apk"
Write-Host "Tablet APK: app\build\outputs\apk\tablet\debug\app-tablet-debug.apk"
