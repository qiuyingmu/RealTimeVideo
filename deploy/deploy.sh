#!/bin/bash
#
# RealTimeVideo 一键部署脚本
# 在目标服务器上运行
#

set -e

PROJECT_DIR="/opt/realtime-video"

echo "=========================================="
echo "  RealTimeVideo 部署脚本"
echo "=========================================="

# 1. 确保目录存在
echo "[1/6] 创建项目目录..."
mkdir -p $PROJECT_DIR

# 2. 复制文件（假设构建包已上传到 /tmp/realtime-video-build）
echo "[2/6] 复制构建文件..."
if [ -d "/tmp/realtime-video-build" ]; then
    cp -r /tmp/realtime-video-build/* $PROJECT_DIR/
else
    echo "⚠️  未找到构建包，使用当前目录"
    PROJECT_DIR=$(pwd)
fi

cd $PROJECT_DIR

# 3. 配置环境变量
echo "[3/6] 配置环境变量..."
if [ ! -f ".env" ]; then
    cp deploy/env.production .env
    echo "⚠️  请编辑 .env 文件填入真实配置，然后重新运行本脚本"
    echo "    需要配置: JWT_SECRET, EZS_APP_KEY, EZS_APP_SECRET"
    exit 1
fi

# 加载环境变量
set -a
source .env
set +a

# 4. 构建前端
echo "[4/6] 构建前端..."
docker compose build frontend

# 5. 构建后端
echo "[5/6] 构建后端..."
docker compose build backend

# 6. 启动所有服务
echo "[6/6] 启动服务..."
docker compose up -d

echo ""
echo "=========================================="
echo "  🎉 部署完成！"
echo "=========================================="
echo ""
echo "  访问地址: http://服务器IP"
echo ""
echo "  查看日志: docker compose logs -f"
echo "  停止服务: docker compose down"
echo "  重启服务: docker compose restart"
echo "=========================================="
