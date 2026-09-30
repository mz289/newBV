param(
    [string]$Avd = 'Television_4K',
    [string]$DnsServers = '223.5.5.5,119.29.29.29',
    [ValidateSet('auto', 'host', 'software', 'swiftshader', 'swangle', 'lavapipe')]
    [string]$Gpu = 'software',
    [string]$SdkPath
)

$ErrorActionPreference = 'Stop'
if (-not $SdkPath) {
    $SdkPath = $env:ANDROID_HOME
}
if (-not $SdkPath) {
    $SdkPath = $env:ANDROID_SDK_ROOT
}
if (-not $SdkPath) {
    $properties = Join-Path $PSScriptRoot '../local.properties'
    if (Test-Path -LiteralPath $properties) {
        $sdkLine = Get-Content -LiteralPath $properties |
            Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $SdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
        }
    }
}
if (-not $SdkPath) {
    throw 'Set ANDROID_HOME or pass -SdkPath to your Android SDK.'
}
$emulatorPath = Join-Path $SdkPath 'emulator/emulator.exe'
if (-not (Test-Path -LiteralPath $emulatorPath)) {
    throw "Android Emulator not found: $emulatorPath"
}

# Cold boot so a saved snapshot cannot restore the broken network state.
# Close this AVD in Android Studio before running this script.
& $emulatorPath -avd $Avd -dns-server $DnsServers -gpu $Gpu -no-snapshot-load -netdelay none -netspeed full
if ($LASTEXITCODE -ne 0) {
    throw "Android Emulator exited with code $LASTEXITCODE"
}
