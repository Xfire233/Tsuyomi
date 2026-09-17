# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0
#
# Sign and verify one Android release artifact outside Gradle.
#
# This is the only supported way to turn `:app:assembleRelease` output into a
# distributable APK. Signing deliberately does not happen in Gradle: the
# keystore password must never enter the configuration cache, a build scan or a
# CI job. See docs/process/RELEASE_PROCEDURE.md for the full procedure and the
# key-custody boundary.

param(
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$SourceRevision,
    [string]$UnsignedApk,
    [string]$OutputDirectory,
    [string]$KeyDirectory = (Join-Path $env:USERPROFILE '.tsuyomi\signing\android-release'),
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$ExpectedCertificate = '0be46968ea9f184b8a5857334d4e4d46eb67ea14b0601c07a5fa3b07f00a7bb7'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path (Join-Path $androidRoot '..')).Path

$sdkBuildToolsRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools'
if (-not (Test-Path -LiteralPath $sdkBuildToolsRoot -PathType Container)) { throw "Android build-tools are unavailable: $sdkBuildToolsRoot" }
$buildTools = Get-ChildItem -LiteralPath $sdkBuildToolsRoot -Directory |
    Where-Object { $_.Name -match '^\d+(\.\d+)*$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if ($null -eq $buildTools) { throw "No usable Android build-tools revision under $sdkBuildToolsRoot" }

$java = Join-Path $JavaHome 'bin\java.exe'
$apksigner = Join-Path $buildTools.FullName 'lib\apksigner.jar'
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$aapt = Join-Path $buildTools.FullName 'aapt2.exe'

$keystore = Join-Path $KeyDirectory 'tsuyomi-android-release.p12'
$passwordFile = Join-Path $KeyDirectory 'password.dpapi'
$certificate = Join-Path $KeyDirectory 'certificate.der'

if (-not $UnsignedApk) {
    $UnsignedApk = Join-Path $androidRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot ('.local\release\' + $VersionName)
}

foreach ($required in @($java, $apksigner, $zipalign, $aapt, $keystore, $passwordFile, $certificate, $UnsignedApk)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required signing input is unavailable: $required" }
}
if ((Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedCertificate) {
    throw 'Unexpected release-signing identity; refusing to sign with an unknown key.'
}

$secret = $null
function Invoke-Native([string]$Executable, [string[]]$Arguments) {
    $previousErrorPolicy = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $nativeOutput = & $Executable @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            $safeOutput = $nativeOutput | Out-String
            if (-not [string]::IsNullOrEmpty($secret)) { $safeOutput = $safeOutput.Replace($secret, '<redacted>') }
            throw "Native artifact operation failed: $safeOutput"
        }
        return ($nativeOutput | Out-String)
    } finally { $ErrorActionPreference = $previousErrorPolicy }
}

$badging = Invoke-Native $aapt @('dump', 'badging', $UnsignedApk)
$expectedIdentity = "package: name='org\.tsuyomi\.android' versionCode='$VersionCode' versionName='" + [regex]::Escape($VersionName) + "'"
if ($badging -notmatch $expectedIdentity) { throw "Unexpected release package identity; expected versionCode=$VersionCode versionName=$VersionName." }

$manifest = Invoke-Native $aapt @('dump', 'xmltree', $UnsignedApk, '--file', 'AndroidManifest.xml')
if ($manifest -match 'android:(debuggable|testOnly)[^\r\n]*(0xffffffff|true)') { throw 'Debuggable/test-only APK cannot carry the release identity.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($UnsignedApk)
try {
    if (@($archive.Entries | Where-Object { $_.FullName -match '^assets/.*\.hxp$' }).Count -ne 0) { throw 'A release artifact must not embed source-test HXP fixtures.' }
} finally { $archive.Dispose() }

if (Test-Path -LiteralPath $OutputDirectory) { throw "Release destination already exists; published artifacts are immutable: $OutputDirectory" }
[void][IO.Directory]::CreateDirectory($OutputDirectory)

$alignedApk = Join-Path $OutputDirectory 'aligned-unsigned.apk'
$releaseApk = Join-Path $OutputDirectory ('Tsuyomi-' + $VersionName + '.apk')
[void](Invoke-Native $zipalign @('-P', '16', '4', $UnsignedApk, $alignedApk))

$environmentName = 'TSUYOMI_ANDROID_RELEASE_PASSWORD'
$previousEnvironment = [Environment]::GetEnvironmentVariable($environmentName, 'Process')
$pointer = [IntPtr]::Zero
$secure = $null
try {
    $secure = ConvertTo-SecureString -String ([IO.File]::ReadAllText($passwordFile))
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $secret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    [Environment]::SetEnvironmentVariable($environmentName, $secret, 'Process')
    [void](Invoke-Native $java @(
        '-jar', $apksigner, 'sign',
        '--ks', $keystore,
        '--ks-key-alias', 'tsuyomi-android-release',
        '--ks-pass', ('env:' + $environmentName),
        '--key-pass', ('env:' + $environmentName),
        '--min-sdk-version', '29',
        '--v1-signing-enabled', 'false',
        '--v2-signing-enabled', 'false',
        '--v3-signing-enabled', 'true',
        '--v4-signing-enabled', 'false',
        '--out', $releaseApk, $alignedApk
    ))
} finally {
    [Environment]::SetEnvironmentVariable($environmentName, $previousEnvironment, 'Process')
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($null -ne $secure) { $secure.Dispose() }
    $secret = $null
    Remove-Item -LiteralPath $alignedApk -ErrorAction SilentlyContinue
}

[void](Invoke-Native $zipalign @('-c', '-P', '16', '4', $releaseApk))
$verification = Invoke-Native $java @('-jar', $apksigner, 'verify', '--verbose', '--print-certs', '--min-sdk-version', '29', $releaseApk)
if ($verification -notmatch ('Signer #1 certificate SHA-256 digest: ' + $ExpectedCertificate)) {
    throw 'Signed artifact certificate does not match the retained release identity.'
}
$schemes = [ordered]@{
    v1 = ($verification -match 'Verified using v1 scheme \(JAR signing\): true')
    v2 = ($verification -match 'Verified using v2 scheme \(APK Signature Scheme v2\): true')
    v3 = ($verification -match 'Verified using v3 scheme \(APK Signature Scheme v3\): true')
    v4 = ($verification -match 'Verified using v4 scheme \(APK Signature Scheme v4\): true')
}
if ($schemes.v1 -or $schemes.v2 -or (-not $schemes.v3) -or $schemes.v4) {
    throw ("Unexpected signature schemes; the release identity is v3 only: " + ($schemes | ConvertTo-Json -Compress))
}

$receipt = [ordered]@{
    status = 'RELEASE_ARTIFACT_NOT_PHONE_ACCEPTED'
    applicationId = 'org.tsuyomi.android'
    variant = 'release'
    versionName = $VersionName
    versionCode = $VersionCode
    minSdk = 29
    debuggable = $false
    testOnly = $false
    sourceRevision = $SourceRevision
    unsignedApkSha256 = (Get-FileHash -LiteralPath $UnsignedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    apk = ('Tsuyomi-' + $VersionName + '.apk')
    apkSha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    apkBytes = (Get-Item -LiteralPath $releaseApk).Length
    certificateSha256 = $ExpectedCertificate
    signatureSchemes = $schemes
    alignment16KiBVerified = $true
    buildTools = $buildTools.Name
    signatureVerification = $verification.Trim()
    keyCustody = 'Private key and password stay outside the worktree under the maintainer profile; Windows-user-bound DPAPI is not off-machine recovery custody.'
    phoneAcceptance = 'PENDING: the user accepts the exact published artifact on their own phone; no acceptance is implied by this receipt.'
}
$receiptJson = $receipt | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'release.json'), $receiptJson)
$receiptJson
