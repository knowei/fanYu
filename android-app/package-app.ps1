param(
    [ValidateSet("release", "debug")]
    [string]$BuildType = "release",
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $JavaHome = $env:JAVA_HOME
    } elseif (Test-Path "D:\environ\Java\jdk-21") {
        $JavaHome = "D:\environ\Java\jdk-21"
    } else {
        throw "JDK not found. Set JAVA_HOME or pass -JavaHome with a JDK 17/21 path."
    }
}

$javaExecutable = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $javaExecutable)) {
    throw "Invalid Java path: $javaExecutable"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$(Join-Path $JavaHome 'bin');$env:Path"

$gradleTask = if ($BuildType -eq "release") { ":app:assembleRelease" } else { ":app:assembleDebug" }
$gradle = Join-Path $projectDir "gradlew.bat"

Push-Location $projectDir
try {
    & $gradle $gradleTask --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $gradleConfig = Get-Content (Join-Path $projectDir "app\build.gradle.kts") -Raw
    $versionMatch = [regex]::Match($gradleConfig, 'versionName\s*=\s*"([^"]+)"')
    $versionName = if ($versionMatch.Success) { $versionMatch.Groups[1].Value } else { "unknown" }
    $sourceApk = Join-Path $projectDir "app\build\outputs\apk\$BuildType\app-$BuildType.apk"
    if (-not (Test-Path $sourceApk)) {
        throw "Build completed but APK was not found: $sourceApk"
    }

    $releaseDir = Join-Path $projectDir "releases"
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
    $outputApk = Join-Path $releaseDir "FanYu-v$versionName-$BuildType.apk"
    Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputApk).Hash

    Write-Host ""
    Write-Host "Package ready: $outputApk" -ForegroundColor Green
    Write-Host "SHA256: $hash"
} finally {
    Pop-Location
}
