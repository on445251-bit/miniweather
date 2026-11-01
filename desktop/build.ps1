# LightWeather desktop build script (pure JDK, no Gradle, no third-party deps)
# Usage:
#   .\build.ps1              compile sources into build\classes
#   .\build.ps1 -Preview     compile + render light/dark preview images + window screenshot
#   .\build.ps1 -Package     compile + package build\dist\LightWeather\LightWeather.exe (jpackage app-image) + zip
param(
    [switch]$Preview,
    [switch]$Package,
    [switch]$SkipCompile
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$srcDir = Join-Path $root 'src\main\java'
$buildDir = Join-Path $root 'build'
$classes = Join-Path $buildDir 'classes'

$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
    $jdk = 'C:\Program Files\Zulu\zulu-17'
}
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
$jarTool = Join-Path $jdk 'bin\jar.exe'
$jpackage = Join-Path $jdk 'bin\jpackage.exe'

New-Item -ItemType Directory -Force -Path $classes | Out-Null

if (-not $SkipCompile) {
    Write-Host "[1/4] Compiling Java sources ..." -ForegroundColor Cyan
    if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $files = Get-ChildItem $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & $javac -encoding UTF-8 -d $classes $files
    if ($LASTEXITCODE -ne 0) { throw "javac failed" }
    Write-Host "      OK: $($files.Count) files compiled" -ForegroundColor Green
}

if ($Preview) {
    Write-Host "[2/4] Rendering previews ..." -ForegroundColor Cyan
    $light = Join-Path $buildDir 'preview-light.png'
    $dark = Join-Path $buildDir 'preview-dark.png'
    $framePng = Join-Path $buildDir 'preview-frame.png'
    $a1 = @('-cp', $classes, 'com.lightweather.desktop.App', '--screenshot', $light, '--theme', 'light')
    $a2 = @('-cp', $classes, 'com.lightweather.desktop.App', '--screenshot', $dark, '--theme', 'dark')
    $a3 = @('-cp', $classes, 'com.lightweather.desktop.App', '--screenshot-frame', $framePng)
    & $java @a1
    & $java @a2
    & $java @a3
    Write-Host "      previews: $light , $dark , $framePng" -ForegroundColor Green
}

if ($Package) {
    Write-Host "[3/4] Building jar ..." -ForegroundColor Cyan
    $jarDir = Join-Path $buildDir 'jar'
    New-Item -ItemType Directory -Force -Path $jarDir | Out-Null
    $jarFile = Join-Path $jarDir 'lightweather.jar'
    & $jarTool --create --file $jarFile --main-class com.lightweather.desktop.App -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "jar failed" }

    Write-Host "[4/4] Packaging app-image with jpackage ..." -ForegroundColor Cyan
    $dist = Join-Path $buildDir 'dist'
    if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    $ico = Join-Path $buildDir 'lightweather.ico'
    & $java -cp $classes com.lightweather.desktop.App --make-icon $ico
    $iconArg = @()
    if (Test-Path $ico) { $iconArg = @('--icon', $ico) }
    & $jpackage --type app-image --name LightWeather `
        --input $jarDir --main-jar 'lightweather.jar' --main-class com.lightweather.desktop.App `
        --app-version 2.1.0 --vendor "LightWeather" --description "LightWeather desktop - open source weather client" `
        --add-modules 'java.base,java.desktop,java.logging,java.xml,jdk.unsupported' `
        --jlink-options '--strip-debug --no-header-files --no-man-pages --compress=1' `
        --java-options '-Dfile.encoding=UTF-8' --dest $dist @iconArg
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

    $appDir = Join-Path $dist 'LightWeather'
    $zip = Join-Path $dist 'LightWeather-win64.zip'
    Compress-Archive -Path (Join-Path $appDir '*') -DestinationPath $zip -Force
    Write-Host "      EXE: $(Join-Path $appDir 'LightWeather.exe')" -ForegroundColor Green
    Write-Host "      ZIP: $zip" -ForegroundColor Green
}
