#!/usr/bin/env bash
# ============================================================================
# setup_env.sh — 轻天气 WeatherApp 环境配置（Linux / macOS）
#
# 作用：
#   1. 检查/安装 JDK 17
#   2. 下载 Android Command-Line Tools（如未配置 ANDROID_HOME）
#   3. 用 sdkmanager 安装编译所需包：platform-tools / android-34 / build-tools 34.0.0
#   4. 接受许可协议
#   5. 输出环境变量（写入 ./env.local 供 build.sh 读取）
#
# 用法：
#   bash setup_env.sh
#   source ./env.local   # 或让 build.sh 自动读取
# ============================================================================
set -euo pipefail

echo "============================================"
echo "  轻天气 WeatherApp 环境配置 (Linux/macOS)"
echo "============================================"

# ---- 0. 基本工具检测 ----
for t in curl unzip java; do
  if ! command -v "$t" >/dev/null 2>&1; then
    echo "[WARN] 未找到 '$t'，请先安装（macOS: brew install $t；Ubuntu: sudo apt install $t）。"
  fi
done

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$PROJECT_DIR/env.local"

# ---- 1. JDK 17 ----
need_jdk=0
if command -v java >/dev/null 2>&1; then
  ver="$(java -version 2>&1 | head -1)"
  if echo "$ver" | grep -qE '"17|21'; then
    echo "[OK] 已检测到兼容 JDK: $ver"
  else
    echo "[INFO] 当前 Java 版本: $ver（建议 17+）"
    need_jdk=1
  fi
else
  need_jdk=1
fi

if [ "$need_jdk" -eq 1 ]; then
  echo "[TIP] 请安装 JDK 17+，例如："
  echo "   Ubuntu : sudo apt install openjdk-17-jdk"
  echo "   macOS  : brew install openjdk@17  (然后配置 JAVA_HOME)"
  echo "   或者下载：https://adoptium.net/temurin/releases/"
  echo "安装后请设置 JAVA_HOME 并重新运行本脚本。"
  exit 1
fi

# ---- 2. Android SDK ----
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
  echo "[OK] 使用已存在的 ANDROID_HOME=$ANDROID_HOME"
  SDK_ROOT="$ANDROID_HOME"
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
  SDK_ROOT="$ANDROID_SDK_ROOT"
  echo "[OK] 使用已存在的 ANDROID_SDK_ROOT=$SDK_ROOT"
else
  SDK_ROOT="$PROJECT_DIR/android-sdk"
  echo "[INFO] 未检测到 Android SDK，将下载 Command-Line Tools 到：$SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  cd "$SDK_ROOT/cmdline-tools"
  OS="$(uname -s)"
  case "$OS" in
    Linux*)  URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" ;;
    Darwin*) URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip" ;;
    *) echo "[ERR] 不支持的系统：$OS"; exit 1 ;;
  esac
  echo "   下载中… ($URL)"
  curl -fSL --retry 3 -o clt.zip "$URL"
  unzip -q clt.zip
  rm clt.zip
  # 解压后是 cmdline-tools/cmdline-tools/，移动内层到 latest
  mv cmdline-tools/cmdline-tools "latest" 2>/dev/null || mv cmdline-tools "latest" 2>/dev/null || true
  cd "$PROJECT_DIR"
  echo "[OK] Command-Line Tools 已就绪"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

# ---- 3. 安装编译依赖 ----
SDKMANAGER="$(dirname "$(find "$SDK_ROOT" -name sdkmanager -type f | head -1)")/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo "[ERR] 未找到 sdkmanager，请检查 SDK 安装。"
  exit 1
fi
export PATH="$(dirname "$SDKMANAGER"):$PATH"

echo "[INFO] 接受许可协议…"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

echo "[INFO] 安装编译包（首次约需下载数百 MB，请耐心等待）…"
"$SDKMANAGER" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# ---- 4. 写出环境变量 ----
cat > "$ENV_FILE" <<EOF
# 由 setup_env.sh 自动生成，source 后可直接运行 build.sh
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export PATH="\$ANDROID_HOME/platform-tools:\$PATH"
EOF

echo "============================================"
echo "[DONE] 环境配置完成！"
echo "  环境变量已写入：$ENV_FILE"
echo "  下一步："
echo "    source ./env.local"
echo "    bash build.sh"
echo "============================================"
