[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string[]] $ApkPath,

    [ValidateSet('debug', 'preview', 'release')]
    [string] $Channel = 'preview',

    [string] $SdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) {
    $SdkRoot = Join-Path $projectRoot '.android'
}

$buildTools = Join-Path $SdkRoot 'build-tools/35.0.0'
$aapt = Join-Path $buildTools 'aapt.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
if (-not (Test-Path -LiteralPath $aapt) -or -not (Test-Path -LiteralPath $apksigner)) {
    throw 'Android Build Tools 35.0.0 not found. Set ANDROID_HOME or pass -SdkRoot.'
}

$exports = foreach ($path in $ApkPath) {
    $apk = (Resolve-Path -LiteralPath $path).Path
    & $apksigner verify $apk
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $apk" }

    $badging = & $aapt dump badging $apk
    if ($LASTEXITCODE -ne 0) { throw "Could not read APK metadata: $apk" }
    $package = $badging | Where-Object { $_ -like 'package:*' }
    if ($package -notmatch "^package: name='app\.locationlab' versionCode='[0-9]+' versionName='(?<version>[0-9]+\.[0-9]+\.[0-9]+)'") {
        throw "Expected a LocationLab APK with an X.Y.Z version: $apk"
    }
    $version = $Matches.version
    $native = $badging | Where-Object { $_ -like 'native-code:*' }
    if ($native -notmatch "^native-code:\s+'(?<abi>arm64-v8a|armeabi-v7a)'\s*$") {
        throw "Expected an APK built for one supported ARM architecture: $apk"
    }
    $abi = $Matches.abi
    $debuggable = 'application-debuggable' -in $badging
    if (($Channel -eq 'debug') -ne $debuggable) {
        throw "APK does not match the '$Channel' channel: $apk"
    }
    $destination = Join-Path $projectRoot "dist/v$version/LocationLab-v$version-$Channel-$abi.apk"
    if ((Test-Path -LiteralPath $destination) -and
        (Get-FileHash -LiteralPath $destination).Hash -ne (Get-FileHash -LiteralPath $apk).Hash) {
        throw "An APK with different contents already has this name. Bump the version before publishing: $destination"
    }
    [pscustomobject]@{ Source = $apk; Destination = $destination }
}

if ($exports | Group-Object Destination | Where-Object Count -GT 1) {
    throw 'Multiple APKs resolve to the same output name.'
}
foreach ($item in $exports) {
    if (-not (Test-Path -LiteralPath $item.Destination)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $item.Destination) | Out-Null
        Copy-Item -LiteralPath $item.Source -Destination $item.Destination
    }
    Get-Item -LiteralPath $item.Destination
}
