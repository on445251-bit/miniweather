# 轻天气 (LightWeather)

[![Build APK](https://github.com/on445251-bit/miniweather/actions/workflows/build.yml/badge.svg)](https://github.com/on445251-bit/miniweather/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/on445251-bit/miniweather?label=latest%20release)](https://github.com/on445251-bit/miniweather/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

一个**极轻量**的天气应用，同一仓库两份实现：**Android**（Java + ViewBinding）与 **Windows 桌面版**（纯 Java 17 Swing，打包成 `LightWeather.exe`）。零第三方 SDK、零 API Key。

> v2.0 起主打「看天气一眼就够」：当前实况、逐时降水、7 天温度曲线、空气质量、生活指数、天气提醒，全部在一屏内。
> v2.1 新增 **Windows 桌面 EXE 版**（`desktop/`），与手机端功能对齐。

## 📦 下载 APK
- **GitHub Actions 自动构建**：进 [Actions](https://github.com/on445251-bit/miniweather/actions) 页面，点最新的 `Build WeatherApp APK` 运行，右侧 **Artifacts** 下载 `weather-app-release`（解压得到 `app-debug.apk` / `app-release-unsigned.apk`）。
- **正式版 Release**：在 [Releases](https://github.com/on445251-bit/miniweather/releases) 页面下载带版本号的 APK（打 `v*` tag 自动生成）。
- 安装：`adb install app-debug.apk`（需开启手机“未知来源”安装权限）。

## 🖥 下载桌面版（Windows）
- 在 [Releases](https://github.com/on445251-bit/miniweather/releases) 下载 `LightWeather-win64.zip`，解压后双击 `LightWeather.exe` 即可运行（**自带精简 JRE，无需安装 Java**，也不需要管理员权限）。
- 绿色版：不写注册表、不装服务，配置与缓存保存在 `%APPDATA%\LightWeather\`。

![桌面版界面](desktop/docs/preview-window.png)

| 浅色 | 深色 |
| --- | --- |
| ![浅色](desktop/docs/preview-light.png) | ![深色](desktop/docs/preview-dark.png) |

桌面版特性：同 Android 端的实况/六宫格、7 天温度曲线、空气质量、生活指数、天气提醒、日出日落与昼长、逐时（点击查看详情）、多城市侧栏、三种单位切换、明暗主题、离线缓存、10 分钟自动刷新；界面全部由 Java2D 手绘（无 UI 框架、无第三方库）。

## 特性

### 天气实况
- 🌤 当前温度 / 天气状况 / 体感温度
- 💧 湿度、🌬 风向风速、🧭 气压、☁️ 云量、🌧 降水量 —— 六宫格指标
- 🌡 **三种温度单位**：℃ / ℉
- 🌀 **四种风速单位**：km/h、m/s、mph、蒲福风级
- 🧭 **三种气压单位**：hPa、mmHg、inHg

### 预报
- 🕑 **24 小时逐时预报**：温度 + 昼夜图标 + **降水概率**，点击任意小时查看该时刻详情（温度/降水概率/降水量/湿度/风速）
- 📅 **7 天预报**：最高/最低温 + 天气图标 + 降水概率
- 📈 **7 天温度曲线**：自绘 `TempCurveView`，渐变温度带 + 高低温折线，一眼看出趋势

### 空气质量与健康
- 🍃 **空气质量卡**：欧洲 AQI 等级（优/良/轻度/中度/重度/严重）+ 建议文案 + PM2.5/PM10/O₃/NO₂/SO₂ 浓度 + 首要污染物
- 🧴 **生活指数**：穿衣、紫外线、洗车、运动、晾晒、感冒 6 项，本地规则计算，无需额外接口
- ⚠️ **天气提醒**：高温 / 低温 / 大风 / 暴雨 / 雷电 / 降雪 / 温差 / 空气污染，按严重程度分级展示

### 日出日落
- 🌅 日出 / 日落时刻与**昼长**（如「10 小时 25 分」）

### 交互
- 🔍 **城市搜索**：中文 / 英文均可，**同名城市多结果时弹出候选列表**（带省/国家）
- 📍 **自动定位**：「我的位置」一键定位
- ↻ **下拉刷新** + **回到前台 10 分钟自动刷新**
- 🏙 **多城市管理**：抽屉切换城市，长按/管理页删除城市
- 🚀 启动闪屏（按昼夜切换图标与背景）
- 🌙 跟随系统深色模式（Material 3 DayNight），也可在菜单里强制 **浅色 / 深色**
- 🎨 **天气背景动画**：晴/多云/雨不同渐变，雨丝下落、云朵漂移
- 📲 **桌面小部件**：4×1，点击刷新，展示温度/状况/更新时间
- 📤 **分享截图**：一键把当前天气卡片截成 PNG 分享

### 工程
- 💾 **完整离线缓存**：断网时展示上次的完整数据（含逐时/7 天/AQI），并标注「缓存数据 · 更新于 HH:mm」
- 📦 零第三方 SDK：仅 AppCompat + Material + RecyclerView + SwipeRefreshLayout
- 🧩 单 Gradle 模块、全 Java、无 Kotlin

## 数据源
- [Open-Meteo](https://open-meteo.com/) —— 完全免费、无需密钥
  - 地理编码：`https://geocoding-api.open-meteo.com/v1/search`
  - 天气预报：`https://api.open-meteo.com/v1/forecast`
  - 空气质量：`https://air-quality-api.open-meteo.com/v1/air-quality`

## 构建方式

### 方式一：Android Studio（推荐）
1. 用 Android Studio 打开本目录。
2. 等待 Gradle 同步完成（会自动下载 Gradle 8.4 与 AGP 8.1.4）。
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
./gradlew assembleDebug          # 调试包
# 或 ./gradlew assembleRelease   # 已开启混淆与资源压缩
```

### 安装到手机
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 桌面版（Windows）
只需 JDK 17（含 `jpackage`），不需要 Gradle、不需要 Android SDK：
```powershell
cd desktop
.\build.ps1 -Package     # 编译 + jpackage 生成 build\dist\LightWeather\LightWeather.exe 与 LightWeather-win64.zip
.\build.ps1 -Preview     # 只渲染预览图（离屏，不需要真实桌面）
```
> 生成安装器（`.msi`/`.exe` setup）需要额外装 [WiX Toolset](https://wixtoolset.org/)，本项目只产出免安装的 app-image + zip。

---

## ☁️ GitHub Actions 云端自动构建

项目内置 `.github/workflows/build.yml`：push 到 `main`/`master`（或手动触发）即自动构建 **Debug + Release APK**，并作为 **Actions Artifact** 提供下载。

特性：
- 自动配 JDK 17（`actions/setup-java@temurin`）
- 自动装 Android SDK 编译包（cmdline-tools + `sdkmanager`）
- 兼容两种仓库结构：项目在仓库根，或在 `WeatherApp/` 子目录
- 打 Tag（如 `v2.0.0`）时，额外自动生成 **GitHub Release** 并附 APK

## 权限说明
- `INTERNET`：拉取天气与空气质量（必需）
- `ACCESS_FINE/COARSE_LOCATION`：仅在点击“定位”按钮时使用，可拒绝（仅该按钮不可用）

## 自定义默认城市
打开 `app/src/main/java/com/example/weather/MainActivity.java`，
把 `restoreLastCity()` 末尾的 `searchByCity("北京")` 换成你常用的城市即可。

## 目录结构
```
.
├── app/
│   ├── build.gradle                 # 依赖与编译选项
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/weather/
│       │   ├── MainActivity.java        # UI + 交互 + 抽屉/单位/渲染
│       │   ├── WeatherClient.java       # 网络 + JSON 解析（预报 / 地理 / AQI）
│       │   ├── WeatherData.java         # 数据模型
│       │   ├── WeatherPrefs.java        # 单位/城市/完整离线缓存
│       │   ├── WeatherCodes.java        # WMO 天气代码 → 文案/图标
│       │   ├── WeatherBackground.java   # 天气背景动画（雨/云/晴）
│       │   ├── WeatherWidget.java       # 桌面小部件
│       │   ├── AirQuality.java          # AQI / UV 分级与配色
│       │   ├── LifeIndex.java           # 生活指数规则
│       │   ├── WeatherAlert.java        # 天气提醒规则
│       │   ├── TempCurveView.java       # 7 天温度曲线（Canvas 自绘）
│       │   ├── LifeIndexAdapter.java    # 生活指数网格
│       │   ├── SplashActivity.java      # 启动闪屏
│       │   ├── HourlyAdapter.java       # 逐时列表
│       │   ├── DailyAdapter.java        # 7 天列表
│       │   └── ShareHelper.java         # 截图分享
│       └── res/                     # 布局 / 矢量图标 / 主题
├── build.gradle
├── settings.gradle
├── desktop/                         # Windows 桌面版（纯 JDK，独立构建）
│   ├── build.ps1                    # 编译 / 预览图 / jpackage 打包
│   ├── docs/                        # README 用的界面截图
│   └── src/main/java/com/lightweather/desktop/
│       ├── App.java                 # 入口（含 --screenshot / --make-icon 自检模式）
│       ├── MainFrame.java           # 窗口外壳：顶栏搜索 + 城市侧栏 + 状态栏
│       ├── WeatherPanel.java        # 全部卡片的自绘面板（核心 UI）
│       ├── Theme.java               # 明暗配色与字体
│       ├── Icons.java               # 天气图标（Java2D 矢量）
│       ├── AppIcon.java             # 应用图标 + ICO 生成
│       ├── Json.java                # 极简 JSON 解析器
│       ├── WeatherClient.java       # Open-Meteo 请求与解析
│       ├── WeatherData.java         # 数据模型
│       ├── WeatherCodes.java        # WMO 代码 → 文案/图标类型
│       ├── AirQuality.java          # AQI / UV 分级
│       ├── LifeIndex.java           # 生活指数
│       ├── WeatherAlert.java        # 天气提醒
│       └── Prefs.java               # 设置与离线缓存（%APPDATA%）
└── gradle/wrapper/                 # Gradle Wrapper（已就绪）
```

## 更新日志
见 [CHANGELOG.md](CHANGELOG.md)。
