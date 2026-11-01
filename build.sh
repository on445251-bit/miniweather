#!/usr/bin/env bash
# ============================================================================
# build.sh — 一键构建轻天气 WeatherApp（Linux / macOS）
#
# 前置：已运行 setup_env.sh（或系统已配置 ANDROID_HOME / JAVA_HOME）
# 产物：app/build/outputs/apk/debug/app-debug.apk
# ============================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# 自动读取 setup_env.sh 生成的环境
if [ -f "$PROJECT_DIR/env.local" ]; then
  # shellcheck disable=SC1091
  source "$PROJECT_DIR/env.local"
fi

# 兜底：若仍未设置，尝试常见路径
if [ -z "${ANDROID_HOME:-}" ]; then
  for p in "$HOME/Android/Sdk" "$PROJECT_DIR/android-sdk"; do
    [ -d "$p" ] && export ANDROID_HOME="$p" && break
  done
fi

echo "============================================"
echo "  ANDROID_HOME = ${ANDROID_HOME:-<未设置>}"
echo "  JAVA_HOME    = ${JAVA_HOME:-<未设置>}"
echo "============================================"

if [ -z "${ANDROID_HOME:-}" ]; then
  echo "[ERR] 未检测到 Android SDK，请先运行：bash setup_env.sh"
  exit 1
fi

echo "[INFO] 开始构建 Debug APK…"
./gradlew assembleDebug

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "============================================"
  echo "[DONE] 构建成功！"
  echo "  APK: $APK"
  echo "  安装: adb install -r \"$APK\""
  echo "============================================"
else
  echo "[ERR] 未找到产物，构建可能失败。"
  exit 1
fi
