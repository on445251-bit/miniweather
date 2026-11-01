#!/bin/sh
# ============================================================================
# upload_github.sh — 用 GitHub REST API 把本项目直接推送到 GitHub（无需本地 git）
#
# 适用：本机没有 git 的环境；或想一键建仓+上传。
# 注意：每个文件会作为一个 commit 提交到默认分支（main）。
#
# 用法（推荐在本地终端，避免 token 明文出现在对话）：
#   GITHUB_TOKEN=ghp_xxx REPO_NAME=weather-app sh upload_github.sh
#
# 可选环境变量：
#   GITHUB_TOKEN   必填，Personal Access Token（需 repo 权限）
#   REPO_NAME      仓库名，默认 weather-app
#   GITHUB_OWNER   用户名（默认自动从 /user 获取）
#   BRANCH         分支，默认 main
#   DRY_RUN=1      只列出将要上传的文件，不真正提交
# ============================================================================
set -e

DIR="${DIR:-.}"
TOKEN="${GITHUB_TOKEN:-}"
REPO="${REPO_NAME:-weather-app}"
BRANCH="${BRANCH:-main}"
OWNER="${GITHUB_OWNER:-}"
DRY="${DRY_RUN:-0}"

API="https://api.github.com"

if [ -z "$TOKEN" ] && [ "$DRY" != "1" ]; then
  echo "错误：请通过环境变量提供 GITHUB_TOKEN" >&2
  echo "示例: GITHUB_TOKEN=ghp_xxx REPO_NAME=weather-app sh $0" >&2
  exit 1
fi

auth() {
  if [ -n "$TOKEN" ]; then
    curl -sS -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" "$@"
  else
    curl -sS -H "Accept: application/vnd.github+json" "$@"
  fi
}

# 收集要上传的文件（排除构建产物 / 本地环境文件 / SDK 下载）
LIST=$(find "$DIR" -type f \
  -not -path '*/.git/*' \
  -not -path '*/build/*' \
  -not -path '*/.gradle/*' \
  -not -path '*/android-sdk/*' \
  -not -name 'env.local' -not -name 'env.local.ps1' \
  -not -name '.DS_Store' -not -name '*.zip' | sort)

echo "============================================"
echo " 将上传到仓库: ${OWNER:-<自动>}/$REPO ($BRANCH)"
echo " 文件数量: $(echo "$LIST" | grep -c .)"
echo "============================================"

if [ "$DRY" = "1" ]; then
  echo "$LIST" | sed 's/^/  /'
  exit 0
fi

# 获取 owner
if [ -z "$OWNER" ]; then
  OWNER=$(auth "$API/user" | grep '"login"' | head -1 | sed -E 's/.*"login"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')
  echo "Owner: $OWNER"
fi

# 创建仓库（已存在则忽略）
auth -X POST "$API/user/repos" \
  -H "Content-Type: application/json" \
  --data-binary "{\"name\":\"$REPO\",\"private\":false,\"auto_init\":false,\"description\":\"轻量安卓天气应用 (Open-Meteo)\"}" \
  >/dev/null 2>&1 || true
echo "[OK] 仓库已就绪: https://github.com/$OWNER/$REPO"

echo "$LIST" | while IFS= read -r f; do
  [ -z "$f" ] && continue
  rel="${f#$DIR/}"
  [ "$rel" = "$f" ] && rel="$f"
  b64=$(base64 -w0 "$f")
  # 若已存在则取 sha 用于覆盖
  sha=$(auth "$API/repos/$OWNER/$REPO/contents/$rel" | grep '"sha"' | head -1 | sed -E 's/.*"sha"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')
  if [ -n "$sha" ]; then
    json="{\"message\":\"chore: update $rel\",\"content\":\"$b64\",\"sha\":\"$sha\",\"branch\":\"$BRANCH\"}"
  else
    json="{\"message\":\"feat: add $rel\",\"content\":\"$b64\",\"branch\":\"$BRANCH\"}"
  fi
  code=$(auth -X PUT "$API/repos/$OWNER/$REPO/contents/$rel" \
    -H "Content-Type: application/json" \
    --data-binary "$json" -o /dev/null -w '%{http_code}')
  echo "$code  $rel"
done

echo "============================================"
echo "[DONE] 上传完成！"
echo "  仓库: https://github.com/$OWNER/$REPO"
echo "  Actions 构建: https://github.com/$OWNER/$REPO/actions"
echo "============================================"
