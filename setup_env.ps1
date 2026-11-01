# ============================================================================
# setup_env.ps1 — 轻天气 WeatherApp 环境配置（Windows PowerShell）
#
# 作用：
#   1. 检查/提示 JDK 17
#   2. 下载 Android Command-Line Tools（如未配置 ANDROID_HOME）
#   3. 用 sdkmanager 安装：platform-tools / android-34 / build-tools 34.0.0
#   4. 接受许可协议
#   5. 写出 env.local.ps1 供 build.ps1 读取
#
# 用法（以“管理员”或普通用户 PowerShell，建议设置执行策略）：
#   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#   .\setup_env.ps1
# ============================================================================
$ErrorActionPreference = "Stop"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  轻天气 WeatherApp 环境配置 (Windows)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$EnvFile    = Join-Path $ProjectDir "env.local.ps1"

# ---- 1. JDK 17 ----
$needJdk = $true
if (Get-Command java -ErrorAction SilentlyContinue) {
    $ver = (java -version 2>&1 | Select-Object -First 1)
    if ($ver -match '"(17|21)') {
        Write-Host "[OK] 已检测到兼容 JDK: $ver" -ForegroundColor Green
        $needJdk = $false
    } else {
        Write-Host "[INFO] 当前 Java 版本: $ver（建议 17+）" -ForegroundColor Yellow
    }
}
if ($needJdk) {
    Write-Host "[TIP] 请安装 JDK 17+：https://adoptium.net/temurin/releases/" -ForegroundColor Yellow
    Write-Host "       安装后设置 JAVA_HOME 并重新运行本脚本。" -ForegroundColor Yellow
    exit 1
}

# ---- 2. Android SDK ----
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $SdkRoot = $env:ANDROID_HOME
    Write-Host "[OK] 使用已存在的 ANDROID_HOME=$SdkRoot" -ForegroundColor Green
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $SdkRoot = $env:ANDROID_SDK_ROOT
    Write-Host "[OK] 使用已存在的 ANDROID_SDK_ROOT=$SdkRoot" -ForegroundColor Green
} else {
    $SdkRoot = Join-Path $ProjectDir "android-sdk"
    Write-Host "[INFO] 未检测到 Android SDK，将下载到：$SdkRoot" -ForegroundColor Yellow
    $clt = Join-Path $SdkRoot "cmdline-tools"
    New-Item -ItemType Directory -Force -Path $clt | Out-Null
    $zip = Join-Path $clt "clt.zip"
    $URL = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    Write-Host "   下载中… ($URL)" -ForegroundColor Gray
    Invoke-WebRequest -Uri $URL -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $clt -Force
    Remove-Item $zip
    # 重命名成 latest 以符合 sdkmanager 期望（解压后是 cmdline-tools/cmdline-tools/）
    $src = Join-Path $clt "cmdline-tools\cmdline-tools"
    if (-not (Test-Path $src)) { $src = Join-Path $clt "cmdline-tools" }
    $dst = Join-Path $clt "latest"
    if (Test-Path $src) { Move-Item -Force $src $dst }
    Write-Host "[OK] Command-Line Tools 已就绪" -ForegroundColor Green
}

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot

# ---- 3. 安装编译依赖 ----
$sdkmanager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
    Write-Host "[ERR] 未找到 sdkmanager，请检查 SDK 安装。" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] 接受许可协议…" -ForegroundColor Gray
# Windows 没有 yes，用循环管道传入 y 给 sdkmanager
1..30 | ForEach-Object { "y" } | & $sdkmanager --licenses | Out-Null

Write-Host "[INFO] 安装编译包（首次约需下载数百 MB）…" -ForegroundColor Gray
& $sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# ---- 4. 写出环境变量 ----
$jhome = (Get-Item (Get-Command java).Source).Directory.Parent.FullName
@"
# 由 setup_env.ps1 自动生成
`$env:ANDROID_HOME = "$SdkRoot"
`$env:ANDROID_SDK_ROOT = "$SdkRoot"
`$env:JAVA_HOME = "$jhome"
`$env:Path = "`$env:ANDROID_HOME\platform-tools;`$env:Path"
"@ | Set-Content -Encoding UTF8 $EnvFile

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "[DONE] 环境配置完成！" -ForegroundColor Green
Write-Host "  环境变量已写入：$EnvFile" -ForegroundColor Gray
Write-Host "  下一步：.\build.ps1" -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Cyan
