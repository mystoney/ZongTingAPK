#!/bin/bash
# 纵听部署脚本：上传 APK + 更新 version.json（自动同步版本号）
# 用法:
#   ./deploy.sh test      # 只部署测试版
#   ./deploy.sh release   # 只部署正式版
#   ./deploy.sh           # 两个都部署
#
# 前提：先运行 ./gradlew assembleBetaDebug assembleProdDebug 完成编译
# incrBuildNum 已在编译后自动递增 version.properties 并 push GitHub

set -e

SERVER="172.16.1.93"
SSH_USER="root"
SSH_PORT="2222"

# 从 version.properties 读取当前版本
VER_FILE="/root/ZongTing/app/version.properties"
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

echo "=========================================="
echo " Deploying: v${VERSION_NAME} (build ${BUILD_NUM})"
echo "=========================================="

MODE="${1:-both}"

do_test() {
    local src="/root/ZongTing/app/build/outputs/apk/beta/debug/app-beta-debug.apk"
    local dest="/usr/ZongTing/test/zongting-test.apk"
    local vjson="/usr/ZongTing/test/version.json"
    local url="http://172.16.1.93:8080/ZongTing/test/zongting-test.apk"
    local vname="${VERSION_NAME}-beta"

    if [ ! -f "$src" ]; then
        echo "WARNING: $src not found, skipping test"
        return
    fi

    echo ""
    echo "[TEST] Uploading APK..."
    scp -P "$SSH_PORT" "$src" "$SSH_USER@$SERVER:$dest"

    echo "[TEST] Updating version.json..."
    python3 -c "
import json
v = {'versionCode': $BUILD_NUM, 'versionName': '$vname', 'apkUrl': '$url', 'updateContent': '新增定时关闭功能（15/30/45/60分钟/自定义），支持锁屏通知栏显示剩余时间'}
print(json.dumps(v, indent=2, ensure_ascii=False))
" | ssh -p "$SSH_PORT" "$SSH_USER@$SERVER" "cat > $vjson"

    echo "[TEST] Done!"
}

do_release() {
    local src="/root/ZongTing/app/build/outputs/apk/prod/app-prod-debug.apk"
    local dest="/usr/ZongTing/release/zongting-release.apk"
    local vjson="/usr/ZongTing/release/version.json"
    local url="http://172.16.1.93:8080/ZongTing/release/zongting-release.apk"

    if [ ! -f "$src" ]; then
        echo "WARNING: $src not found, skipping release"
        return
    fi

    echo ""
    echo "[RELEASE] Uploading APK..."
    scp -P "$SSH_PORT" "$src" "$SSH_USER@$SERVER:$dest"

    echo "[RELEASE] Updating version.json..."
    python3 -c "
import json
v = {'versionCode': $BUILD_NUM, 'versionName': '$VERSION_NAME', 'apkUrl': '$url'}
print(json.dumps(v, indent=2, ensure_ascii=False))
" | ssh -p "$SSH_PORT" "$SSH_USER@$SERVER" "cat > $vjson"

    echo "[RELEASE] Done!"
}

case "$MODE" in
    test)     do_test ;;
    release)  do_release ;;
    both)     do_test; do_release ;;
    *)        echo "Usage: $0 [test|release|both]"; exit 1 ;;
esac

echo ""
echo "=========================================="
echo " Deployment complete: v${VERSION_NAME} (build ${BUILD_NUM})"
echo "=========================================="
