$ErrorActionPreference = "Stop"

Write-Host "Running OH verification..." -ForegroundColor Cyan
& .\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Verification passed." -ForegroundColor Green
Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk"
