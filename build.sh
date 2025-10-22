#!/bin/bash

# 分贝仪应用构建脚本

echo "开始构建分贝仪应用..."

# 检查Gradle是否安装
if ! command -v gradle &> /dev/null; then
    echo "Gradle未安装，请先安装Gradle"
    echo "或使用Android Studio进行构建"
    exit 1
fi

# 清理项目
echo "清理项目..."
gradle clean

# 构建Debug版本
echo "构建Debug版本..."
gradle assembleDebug

# 构建Release版本
echo "构建Release版本..."
gradle assembleRelease

echo "构建完成！"
echo "Debug APK路径: app/build/outputs/apk/debug/app-debug.apk"
echo "Release APK路径: app/build/outputs/apk/release/app-release.apk"