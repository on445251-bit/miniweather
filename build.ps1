# ============================================================================
# build.ps1 — 一键构建轻天气 WeatherApp（Windows PowerShell）
#
# 前置：已运行 setup_env.ps1（或系统已配置 ANDROID_HOME / JAVA_HOME）
# 产物：app\build\outputs\apk\debug\app-debug.apk
# ============================================================================
$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ProjectDir

# 自动读取 setup_env.ps1 生成的环境
$EnvLocal = Join-Path $ProjectDir "env.local.ps1"
if (Test-Path $EnvLocal) { . $EnvLocal }

# 兜底常见路径
if (-not $env:ANDROID_HOME) {
    $candidates = @("$env:LOCALAPPDATA\Android\Sdk", (Join-Path $ProjectDir "android-sdk"))
    foreach ($p in $candidates) { if (Test-Path $p) { $env:ANDROID_HOME = $p; break } }
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  ANDROID_HOME = $($env:ANDROID_HOME)" -ForegroundColor Gray
Write-Host "  JAVA_HOME    = $($env:JAVA_HOME)" -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Cyan

if (-not $env:ANDROID_HOME) {
    Write-Host "[ERR] 未检测到 Android SDK，请先运行：.\setup_env.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] 开始构建 Debug APK…" -ForegroundColor Gray
.\gradlew.bat assembleDebug

$Apk = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $Apk) {
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "[DONE] 构建成功！" -ForegroundColor Green
    Write-Host "  APK: $Apk" -ForegroundColor Gray
    Write-Host "  安装: adb install -r `"$Apk`"" -ForegroundColor Gray
    Write-Host "============================================" -ForegroundColor Cyan
} else {
    Write-Host "[ERR] 未找到产物，构建可能失败。" -ForegroundColor Red
    exit 1
}
