# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

param(
    [switch]$Force,
    [switch]$ReviewWorkOnly
)

$ErrorActionPreference = 'Stop'
$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $sdkRoot = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw 'Set ANDROID_SDK_ROOT or ANDROID_HOME.'
}

$avdManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/avdmanager.bat'
$image = 'system-images;android-29;default;x86_64'
$avdHome = if ([string]::IsNullOrWhiteSpace($env:ANDROID_AVD_HOME)) {
    Join-Path $HOME '.android/avd'
} else {
    $env:ANDROID_AVD_HOME
}

function Set-ConfigValue([string]$Path, [string]$Key, [string]$Value) {
    $lines = @(Get-Content $Path)
    $pattern = "^\s*$([regex]::Escape($Key))\s*="
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match $pattern) {
            $found = $true
            "$Key = $Value"
        } else {
            $line
        }
    }
    if (-not $found) {
        $updated += "$Key = $Value"
    }
    Set-Content -Path $Path -Value $updated -Encoding UTF8
}

function New-ReviewAvd(
    [string]$Name,
    [int]$Width,
    [int]$Height,
    [int]$Density
) {
    $config = Join-Path $avdHome "$Name.avd/config.ini"
    if (Test-Path $config) {
        if (-not $Force) {
            throw "AVD already exists: $Name. Re-run with -Force to recreate it."
        }
        & $avdManager delete avd --name $Name | Out-Host
    }

    'no' | & $avdManager create avd --name $Name --package $image --device pixel_2 --force | Out-Host
    if (-not (Test-Path $config)) {
        throw "AVD config was not created: $config"
    }

    Set-ConfigValue $config 'hw.lcd.width' $Width
    Set-ConfigValue $config 'hw.lcd.height' $Height
    Set-ConfigValue $config 'hw.lcd.density' $Density
    Set-ConfigValue $config 'hw.ramSize' '1536M'
    Set-ConfigValue $config 'hw.initialOrientation' 'portrait'
    Set-ConfigValue $config 'hw.keyboard' 'yes'
    Set-ConfigValue $config 'PlayStore.enabled' 'no'
    Set-ConfigValue $config 'tag.id' 'default'
    Set-ConfigValue $config 'abi.type' 'x86_64'
    Set-ConfigValue $config 'image.sysdir.1' 'system-images\android-29\default\x86_64\'
    Set-ConfigValue $config 'fastboot.forceColdBoot' 'yes'
    Set-ConfigValue $config 'fastboot.forceFastBoot' 'no'
    Set-ConfigValue $config 'showDeviceFrame' 'no'
}

if ($ReviewWorkOnly) {
    New-ReviewAvd 'Tsuyomi_Review_Work_API29' 1080 2400 420
    Write-Host 'Review work AVD created. It is non-canonical and must not own final human evidence.'
    return
}

New-ReviewAvd 'Tsuyomi_API29' 1080 2400 420
New-ReviewAvd 'Tsuyomi_EInk_API29' 1264 1680 240
Write-Host 'Review and acceptance AVDs created. Start with -wipe-data -no-snapshot for acceptance runs.'
