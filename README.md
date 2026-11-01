# 轻天气 (WeatherApp)

[![Build APK](https://github.com/on445251-bit/miniweather/actions/workflows/build.yml/badge.svg)](https://github.com/on445251-bit/miniweather/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/on445251-bit/miniweather?label=latest%20release)](https://github.com/on445251-bit/miniweather/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](#)

一个**极轻量**的 Android 天气应用：零第三方 SDK、零 API Key、单 Activity、原生 ViewBinding。

## 📦 下载 APK
- **GitHub Actions 自动构建**：进 [Actions](https://github.com/on445251-bit/miniweather/actions) 页面，点最新的 `Build WeatherApp APK` 运行，右侧 **Artifacts** 下载 `weather-app-release`（解压得到 `app-release.apk`）。
- **正式版 Release**：在 [Releases](https://github.com/on445251-bit/miniweather/releases) 页面下载带版本号的 APK（打 `v*` tag 自动生成）。
- 安装：`adb install app-release.apk`（需开启手机“未知来源”安装权限）。

## 特性
- 🌤 当前天气：温度 / 天气状况 / 体感 / 湿度 / 风速
- 🕑 24 小时逐时预报
- 📅 7 天预报
- 🔍 城市搜索（中文 / 英文均可，基于 Open-Meteo Geocoding）
- 📍 自动定位（“我的位置”按钮，需授权）
- ↻ 下拉刷新
- 🌙 跟随系统深色模式（Material 3 DayNight）
- 🌡 **℃ / ℉ 切换**：右上角菜单一键切换，全局持久化
- 🏙 **多城市管理**：左侧抽屉（点左上角图标）查看/切换城市，支持“添加当前城市”
- 🎨 **天气背景动画**：晴天 / 多云 / 雨天不同渐变背景，雨丝下落、云朵漂移
- 📲 **桌面小部件**：4×1 天气小部件，点击刷新，展示最近一次的天气
- 🚀 **启动闪屏**：轻量全屏 SplashActivity（800ms 展示图标），并自动按昼夜切换图标
- 🌙 **昼夜图标自动切换**：基于本地时间（18:00–06:00 视为夜间），主界面 / 列表 / 小部件图标与背景自动用日/月变体
- 📍 **“我的位置”快捷项**：抽屉顶部固定项，点击即回到上次定位（无记录则重新定位）
- 📤 **分享截图**：右上角菜单一键把当前天气卡片截成 PNG，经系统分享到其他 App
- 📦 方法论：仅依赖 AppCompat + Material + RecyclerView + SwipeRefresh + DrawerLayout，APK 极小

## 数据源
- [Open-Meteo](https://open-meteo.com/) —— 完全免费、无需密钥
  - 地理编码：`https://geocoding-api.open-meteo.com/v1/search`
  - 天气预报：`https://api.open-meteo.com/v1/forecast`

## 构建方式

### 方式一：Android Studio（推荐）
1. 用 Android Studio 打开本目录 `WeatherApp`。
2. 等待 Gradle 同步完成（会自动下载 8.4 与 AGP 8.1.4）。
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`。
4. 生成的 `app-debug.apk` 在 `app/build/outputs/apk/debug/`。

### 方式二：命令行（需 JDK 17 + Android SDK）

**一键脚本（推荐，会自动装 SDK 包）**
```bash
# Linux / macOS
bash setup_env.sh      # 首次：下载 SDK 命令工具 + 装编译包 + 生成 env.local
bash build.sh          # 构建，产物 app/build/outputs/apk/debug/app-debug.apk

# Windows (PowerShell，建议先 Set-ExecutionPolicy RemoteSigned)
.\setup_env.ps1
.\build.ps1
```

**手动**
```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk17
cd WeatherApp
./gradlew assembleDebug          # 调试包
# 或 ./gradlew assembleRelease   # 已开启混淆与资源压缩
```

### 安装到手机
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ☁️ GitHub Actions 云端自动构建

项目内置 `.github/workflows/build.yml`：push 到 `main`/`master`（或手动触发）即自动构建 **Release APK**（已混淆 + 资源压缩），并作为 **Actions Artifact** 提供下载。

特性：
- 自动配 JDK 17（`actions/setup-java@temurin`）
- 自动装 Android SDK 编译包（`android-actions/setup-android` + `sdkmanager`）
- 兼容两种仓库结构：项目在仓库根，或在 `WeatherApp/` 子目录
- 打 Tag（如 `v1.0`）时，额外自动生成 **GitHub Release** 并附 APK

用法：
1. 把 `WeatherApp/` 推到 GitHub 仓库
2. Actions 页面查看构建，下载 `weather-app-release` 产物
3. （可选）`git tag v1.0 && git push --tags` 自动出 Release

> 提示：CI 默认构建 Release 包；如需 Debug 包，把工作流里的 `assembleRelease` 改成 `assembleDebug` 即可。

## 权限说明
- `INTERNET`：拉取天气（必需）
- `ACCESS_FINE/COARSE_LOCATION`：仅在点击“定位”按钮时使用，可拒绝（仅该按钮不可用）

## 自定义默认城市
打开 `app/src/main/java/com/example/weather/MainActivity.java`，
把 `searchByCity("北京")` 与 `b.etCity.setText("北京")` 换成你常用的城市即可。

## 目录结构
```
WeatherApp/
├── app/
│   ├── build.gradle                # 依赖与编译选项
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/weather/
│       │   ├── MainActivity.java     # UI + 交互 + 抽屉/单位/动画
│       │   ├── WeatherClient.java    # 网络 + JSON 解析
│       │   ├── WeatherData.java      # 数据模型
│       │   ├── WeatherPrefs.java     # 单位/城市/缓存 持久化
│       │   ├── WeatherCodes.java     # WMO 天气代码 → 文案/图标
│       │   ├── WeatherBackground.java# 天气背景动画（雨/云）
│       │   ├── WeatherWidget.java    # 桌面小部件
│       │   ├── SplashActivity.java   # 启动闪屏
│       │   ├── HourlyAdapter.java    # 逐时列表
│       │   └── DailyAdapter.java     # 7 天列表
│       └── res/                     # 布局 / 矢量图标 / 主题
├── build.gradle
├── settings.gradle
└── gradle/wrapper/                 # Gradle Wrapper（已就绪）
```
