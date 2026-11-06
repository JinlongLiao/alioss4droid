#!/usr/bin/env bash
#
# 一键构建 + 安装 OSS Browser 到当前 USB 连接的 Android 设备。
#
# 小米 / Redmi HyperOS 对 `adb install` 流式安装有额外的 "USB 安装" 校验，
# 常见报错：INSTALL_FAILED_USER_RESTRICTED。
# 本脚本改走稳定的 "push 到 /data/local/tmp + pm install" 路径绕过。
#
# Usage:
#   ./install.sh                 # 构建并安装
#   ./install.sh --launch        # 构建并安装，安装后自动启动
#   ./install.sh --no-build      # 只装不构建（APK 已现成）
#   ./install.sh --clean         # 构建前 clean
#
set -euo pipefail

NO_BUILD=0
LAUNCH=0
CLEAN=0

for arg in "$@"; do
  case "$arg" in
    --no-build) NO_BUILD=1 ;;
    --launch)   LAUNCH=1 ;;
    --clean)    CLEAN=1 ;;
    -h|--help)
      grep -E '^#( |!)' "$0" | sed 's/^# //; s/^#!.*$//'
      exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

APK="app/build/outputs/apk/debug/app-debug.apk"
# Debug build appends `.debug` via applicationIdSuffix; keep in sync with app/build.gradle.kts.
PKG="io.github.jinlongliao.ossbrowser.debug"
ACT="io.github.jinlongliao.ossbrowser.MainActivity"
REMOTE="/data/local/tmp/oss-browser-debug.apk"

step() { printf "\n==> %s\n" "$*"; }

# ---------- 1. build ----------
if [[ $NO_BUILD -eq 0 ]]; then
  tasks=()
  [[ $CLEAN -eq 1 ]] && tasks+=("clean")
  tasks+=("assembleDebug")
  step "Gradle ${tasks[*]}"
 bash ./gradlew "${tasks[@]}" --console=plain
fi

if [[ ! -f "$APK" ]]; then
  echo "未找到 APK: $APK" >&2
  exit 1
fi

# ---------- 2. device check ----------
step "检查 adb 设备"
devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ -z "$devices" ]]; then
  echo "没有检测到已连接的 Android 设备" >&2
  exit 1
fi
echo "$devices"

# ---------- 3. push ----------
step "推送 APK → $REMOTE"
adb push "$APK" "$REMOTE"

# ---------- 4. pm install ----------
step "pm install -r -t"
out=$(adb shell pm install -r -t "$REMOTE" 2>&1)
echo "$out"
if ! grep -q "Success" <<< "$out"; then
  adb shell rm -f "$REMOTE" >/dev/null || true
  echo "安装失败" >&2
  exit 1
fi

# ---------- 5. cleanup ----------
step "清理远端临时文件"
adb shell rm -f "$REMOTE" >/dev/null

# ---------- 6. launch ----------
if [[ $LAUNCH -eq 1 ]]; then
  step "启动 $PKG"
  adb shell am start -n "$PKG/$ACT"
fi

printf "\n\033[32m✔ 完成：%s 已安装\033[0m\n" "$PKG"
