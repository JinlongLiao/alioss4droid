<#
.SYNOPSIS
  One-shot build + install OSS Browser onto the attached Android device.

.DESCRIPTION
  Xiaomi / Redmi HyperOS enforces an extra "USB install" check on
  `adb install`, which frequently fails with INSTALL_FAILED_USER_RESTRICTED
  even after toggling the developer option. This script takes the stable
  path: adb push to /data/local/tmp and then `adb shell pm install`.

.PARAMETER NoBuild
  Skip Gradle build, install the existing APK.

.PARAMETER Launch
  Start the app after install.

.PARAMETER Clean
  Run `gradle clean` before assembleLiteDebug (ignored with -NoBuild).

.EXAMPLE
  .\install.ps1 -Launch
  .\install.ps1 -NoBuild -Launch
  .\install.ps1 -Clean  -Launch
#>
[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$Launch,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$apkPath          = "app\build\outputs\apk\lite\debug\app-lite-debug.apk"
# Debug build appends `.debug` via applicationIdSuffix; keep these in sync with app/build.gradle.kts.
$packageId        = "io.github.jinlongliao.ossbrowser.debug"
$launcherActivity = "io.github.jinlongliao.ossbrowser.MainActivity"
$remoteApk        = "/data/local/tmp/oss-browser-lite-debug.apk"

function Step([string]$msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

# ---------- 1. build ----------
if (-not $NoBuild) {
    $tasks = @()
    if ($Clean) { $tasks += "clean" }
    $tasks += "assembleLiteDebug"
    Step ("gradle " + ($tasks -join " "))
    & ".\gradlew.bat" @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
}

if (-not (Test-Path $apkPath)) {
    throw "APK not found: $apkPath (run without -NoBuild first)"
}

# ---------- 2. device check ----------
Step "adb devices"
$lines = & adb devices
$devices = $lines | Where-Object { $_ -match "^\S+\s+device$" }
if (-not $devices) {
    throw "No Android device attached. Check USB debugging."
}
Write-Host $devices

# ---------- 3. push ----------
Step "push APK -> $remoteApk"
& adb push $apkPath $remoteApk
if ($LASTEXITCODE -ne 0) { throw "adb push failed" }

# ---------- 4. pm install ----------
Step "pm install -r -t"
$installOutput = & adb shell pm install -r -t $remoteApk 2>&1 | Out-String
Write-Host $installOutput.Trim()
if ($LASTEXITCODE -ne 0 -or $installOutput -notmatch "Success") {
    & adb shell rm -f $remoteApk | Out-Null
    throw "Install failed: $($installOutput.Trim())"
}

# ---------- 5. cleanup ----------
Step "cleanup remote tmp file"
& adb shell rm -f $remoteApk | Out-Null

# ---------- 6. launch ----------
if ($Launch) {
    Step "launch $packageId"
    & adb shell am start -n "$packageId/$launcherActivity" | Out-Host
}

Write-Host ""
Write-Host "[OK] installed: $packageId" -ForegroundColor Green
