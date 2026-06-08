#!/bin/bash
# 纵听部署脚本：写 version.json 到 APK 编译输出根目录
# HTTP server (python3 -m http.server 8080) 直接 serve 整个 outputs/ 目录
# 用法: ./deploy.sh [test|release]  # 默认 test

set -e

# 部署到本机（172.16.1.91 = 本机本身）
SERVER_IP="172.16.1.91"
SERVER_PORT="8080"
SERVER_BASE="http://${SERVER_IP}:${SERVER_PORT}"

# 从 version.properties 读取当前版本
VER_FILE="/data/Code/ZongTing/app/version.properties"
if [ ! -f "$VER_FILE" ]; then
    echo "ERROR: $VER_FILE not found"
    exit 1
fi

BUILD_NUM=$(grep "^buildNumber=" "$VER_FILE" | cut -d= -f2)
VERSION_NAME=$(grep "^versionName=" "$VER_FILE" | cut -d= -f2)

if [ -z "$BUILD_NUM" ] || [ -z "$VERSION_NAME" ]; then
    echo "ERROR: Could not read buildNumber or versionName from $VER_FILE"
    exit 1
fi

MODE="${1:-test}"
OUTPUTS_DIR="/data/Code/ZongTing/app/build/outputs"

echo "=========================================="
echo " Deploying: v${VERSION_NAME} (build ${BUILD_NUM}) — ${MODE}"
echo "=========================================="

case "$MODE" in
    test)
        APK_REL="apk/beta/debug/app-beta-debug.apk"
        VNAME="${VERSION_NAME}-beta"
        CHANNEL="test"
        NOTES="${NOTES:-修复 PadPortrait 歌词字体放大问题；layout 模块拆分；PlayerScreen 182 行}"
        ;;
    release)
        APK_REL="apk/prod/release/app-prod-release.apk"
        VNAME="${VERSION_NAME}"
        CHANNEL="release"
        NOTES="${NOTES:-Release build}"
        ;;
    *)
        echo "Usage: $0 [test|release]"
        exit 1
        ;;
esac

APK_URL="${SERVER_BASE}/${APK_REL}"
VJSON="${OUTPUTS_DIR}/version.json"
APK_SRC="${OUTPUTS_DIR}/${APK_REL}"

if [ ! -f "$APK_SRC" ]; then
    echo "WARNING: $APK_SRC not found"
    echo "  Run ./gradlew :app:assembleBetaDebug (or assembleProdRelease) first."
    exit 1
fi

# 写 version.json
python3 <<EOF > "$VJSON"
import json
v = {
    "versionCode": $BUILD_NUM,
    "versionName": "$VNAME",
    "apkUrl": "$APK_URL",
    "updateContent": "$NOTES",
    "releaseNotes": "$NOTES",
    "forceUpdate": False,
    "channel": "$CHANNEL"
}
print(json.dumps(v, indent=2, ensure_ascii=False))
EOF

chmod 644 "$VJSON"

echo ""
echo "[${MODE^^}] version.json updated: $VJSON"
echo "[${MODE^^}] APK URL:             $APK_URL"
cat "$VJSON"
echo ""
echo "=========================================="
echo " Deployment complete: v${VERSION_NAME} (build ${BUILD_NUM})"
echo "=========================================="
