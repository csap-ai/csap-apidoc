#!/bin/bash

echo "====== 开始构建 CSAP API Devtools ======"

# 1. 清理旧的静态资源
echo "1. 清理旧的静态资源..."
rm -rf src/main/resources/static
echo "✅ 清理完成"

# 2. 进入前端项目目录
cd devtools

# 3. 构建前端项目
echo "2. 构建前端项目..."
npm run build:prod
if [ $? -ne 0 ]; then
  echo "❌ 前端构建失败"
  exit 1
fi
echo "✅ 前端构建完成（已自动重命名为 csap-api-devtools.html）"

# 4. 复制构建产物到 resources
echo "3. 复制构建产物..."
cp -rf dist ../src/main/resources/static
cd ../
echo "✅ 复制完成"

# 5. 打包 Maven 项目
echo "4. 打包 Maven 项目..."
mvn clean install
if [ $? -ne 0 ]; then
  echo "❌ Maven 打包失败"
  exit 1
fi

echo "====== 构建完成 ======"
echo ""
echo "📱 访问地址："
echo "  - http://localhost:8085/devtools-ui  （Devtools 管理界面）⭐"
echo ""
echo "🔌 API 地址："
echo "  - http://localhost:8085/devtools/*  (Devtools API)"
echo "  - http://localhost:8085/csap/yaml/* (YAML API)"
echo ""
echo "💡 提示："
echo "  - 前端路由基于 /devtools-ui"
echo "  - 所有页面路径：/devtools-ui, /devtools-ui/api, /devtools-ui/login"
