# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

$ErrorActionPreference = 'Stop'

function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Missing required command: $Name"
    }
    return $command.Source
}

$java = Require-Command 'java'
$node = Require-Command 'node'
$npm = Require-Command 'npm'
$python = Require-Command 'python'

$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $sdkRoot = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($sdkRoot) -or -not (Test-Path $sdkRoot)) {
    throw 'Set ANDROID_SDK_ROOT (or ANDROID_HOME) to an installed Android SDK.'
}

$sdkManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/sdkmanager.bat'
$avdManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/avdmanager.bat'
$adb = Join-Path $sdkRoot 'platform-tools/adb.exe'
$emulator = Join-Path $sdkRoot 'emulator/emulator.exe'
foreach ($path in @($sdkManager, $avdManager, $adb, $emulator)) {
    if (-not (Test-Path $path)) {
        throw "Missing Android SDK tool: $path"
    }
}

Write-Host "java: $java"
& java -version
Write-Host "node: $(& node --version)"
Write-Host "npm: $(& npm --version)"
Write-Host "reuse: $(& python -m reuse --version | Select-Object -First 1)"
Write-Host "sdk: $sdkRoot"
& $emulator -version | Select-Object -First 1

$requiredPackages = @{
    'platforms;android-36' = (Join-Path $sdkRoot 'platforms/android-36/android.jar')
    'system-images;android-29;default;x86_64' = (Join-Path $sdkRoot 'system-images/android-29/default/x86_64/package.xml')
    'platform-tools' = $adb
    'emulator' = $emulator
}
foreach ($required in $requiredPackages.GetEnumerator()) {
    if (-not (Test-Path $required.Value)) {
        throw "Missing Android SDK package: $($required.Key)"
    }
}

$localProperties = Join-Path $PSScriptRoot '../local.properties'
$sdkLine = "sdk.dir=$($sdkRoot.Replace('\', '\\'))"
Set-Content -Path $localProperties -Value $sdkLine -Encoding UTF8
Write-Host 'Environment ready; local.properties refreshed.'
