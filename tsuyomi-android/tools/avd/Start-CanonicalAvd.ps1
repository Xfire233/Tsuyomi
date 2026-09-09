# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

param(
    [string]$Name = 'Tsuyomi_API29',
    [switch]$NoStop,
    [switch]$Cold
)

$ErrorActionPreference = 'Stop'
$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $sdkRoot = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw 'Set ANDROID_SDK_ROOT or ANDROID_HOME.'
}

$emulator = Join-Path $sdkRoot 'emulator/emulator.exe'
if (-not (Test-Path $emulator)) {
    throw "emulator.exe not found: $emulator"
}

$android = Get-Command android -ErrorAction SilentlyContinue
if (-not $NoStop -and $android) {
    & android emulator stop $Name 2>$null
}

# Public DNS: the emulator DNS proxy (10.0.2.3) follows the host resolver, which
# breaks after a local proxy/TUN (e.g. Clash) is torn down. Never pass -wipe-data.
$dns = '8.8.8.8,1.1.1.1'
$launchArgs = @(
    '-avd', $Name,
    '-dns-server', $dns,
    '-netdelay', 'none',
    '-netspeed', 'full',
    '-crash-report-mode', 'disabled'
)
if ($Cold) {
    $launchArgs += '-no-snapshot-load'
}
Start-Process -FilePath $emulator -ArgumentList $launchArgs | Out-Null
Write-Host "Started $Name with -dns-server $dns (userdata preserved)."
