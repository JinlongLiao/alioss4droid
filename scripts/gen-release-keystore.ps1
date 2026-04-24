# ---------------------------------------------------------------------------
# alioss4droid - generate release keystore (Windows PowerShell)
#
# Usage (from the project root):
#   powershell -ExecutionPolicy Bypass -File .\scripts\gen-release-keystore.ps1
#
# The script will:
#   1. Prompt you for passwords (stored only in the generated keystore.properties)
#   2. Create alioss4droid-release.jks in the project root
#   3. Create keystore.properties in the project root
#
# Do NOT commit the generated .jks or keystore.properties to git.
# ---------------------------------------------------------------------------
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$KeystoreFile = Join-Path $ProjectRoot "alioss4droid-release.jks"
$PropsFile = Join-Path $ProjectRoot "keystore.properties"

if (Test-Path $KeystoreFile) {
    Write-Host "keystore already exists at $KeystoreFile"
    Write-Host "Remove it manually before running this script again."
    exit 1
}

$KeyAlias = "alioss4droid"
$DName = "CN=alioss4droid, O=jinlongliao, C=CN"

$StorePwdSec = Read-Host "Keystore password (min 6 chars)" -AsSecureString
$KeyPwdSec = Read-Host "Key password (can be same as keystore)" -AsSecureString

$BSTR1 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($StorePwdSec)
$StorePwd = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR1)
$BSTR2 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($KeyPwdSec)
$KeyPwd = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR2)

Write-Host "Generating keystore..."
& keytool -genkeypair -v `
    -keystore $KeystoreFile `
    -keyalg RSA -keysize 2048 -validity 36500 `
    -alias $KeyAlias `
    -dname $DName `
    -storetype PKCS12 `
    -storepass $StorePwd `
    -keypass $KeyPwd

if (-not (Test-Path $KeystoreFile)) {
    Write-Host "keytool failed; make sure JDK is on PATH."
    exit 1
}

@"
storeFile=alioss4droid-release.jks
storePassword=$StorePwd
keyAlias=$KeyAlias
keyPassword=$KeyPwd
"@ | Out-File -FilePath $PropsFile -Encoding ASCII -NoNewline

Write-Host ""
Write-Host "Done."
Write-Host "Keystore: $KeystoreFile"
Write-Host "Props:    $PropsFile"
Write-Host "Both are git-ignored. Keep them safe (losing them means Play Console cannot accept future updates)."
