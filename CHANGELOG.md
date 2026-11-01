# 更新日志 (CHANGELOG)

本项目遵循“功能分批追加”的开发方式，所有版本均保持**零第三方 SDK、免 API Key、单 Gradle 模块**的轻量原则。

---

## v1.4.0 — 截图分享 / 构建脚本化
- ✨ 新增「分享截图」：右上角菜单一键把当前天气卡片截取为 PNG，经 FileProvider 调起系统分享（微信/QQ/短信等）
- 🛠 新增 `ShareHelper`（Bitmap 截图 + FileProvider）
- 📄 新增 `CHANGELOG.md`、`setup_env.ps1/.sh` 与 `build.ps1/.sh` 环境配置与一键构建脚本
- 📦 新增 `res/xml/file_paths.xml` 与 FileProvider 声明

## v1.3.0 — 昼夜图标 + 启动闪屏 + 我的位置快捷项
- 🌙 `WeatherCodes` 支持昼夜：18:00–06:00 自动切换日/月图标与渐变背景
- 🚀 新增 `SplashActivity` 全屏启动闪屏（800ms），主题按昼夜切换
- 📍 抽屉顶部固定「我的位置」快捷项，点击恢复上次定位或重新定位
- 🐛 修复重启后“我的位置”误回退到北京的问题

## v1.2.0 — 桌面小部件 + 背景动画
- 📲 新增 4×1 桌面小部件 `WeatherWidget`：展示最近天气，点击刷新/打开 App
- 🎨 新增 `WeatherBackground` 天气背景动画（雨丝下落 / 云朵漂移 + 渐变）

## v1.1.0 — 多城市 + 温度单位切换
- 🏙 左侧抽屉管理多城市（保存/切换/添加当前城市）
- 🌡 右上角菜单一键切换 ℃ / ℉，全局持久化

## v1.0.0 — 基础天气应用
- 🌤 当前天气（温度/状况/体感/湿度/风速）
- 🕑 24 小时逐时预报
- 📅 7 天预报
- 🔍 城市搜索（Open-Meteo Geocoding）
- 📍 自动定位
- ↻ 下拉刷新
- 🌙 Material 3 DayNight 深色模式
- 📡 数据源：Open-Meteo（免费、无需密钥）
