#!/bin/bash
#
# 构建部署包 — 在开发机上运行
# 生成 realtime-video-build.tar.gz 上传到服务器
#

set -e

echo "=========================================="
echo "  构建部署包"
echo "=========================================="

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="/tmp/realtime-video-build"

echo "项目目录: $PROJECT_DIR"

# 1. 打包
echo "[1/4] 编译后端..."
cd "$PROJECT_DIR/backend"
mvn clean package -DskipTests -q
echo "  ✅ 后端编译完成"

# 2. 安装前端依赖
echo "[2/4] 安装前端依赖..."
cd "$PROJECT_DIR/frontend"
npm ci --quiet 2>/dev/null || npm install --quiet
echo "  ✅ 前端依赖安装完成"

# 3. 创建部署包目录
echo "[3/4] 创建部署包..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/deploy"
mkdir -p "$BUILD_DIR/backend/target"

# 复制关键文件
cp -r "$PROJECT_DIR/deploy"/*.sh "$BUILD_DIR/deploy/" 2>/dev/null || true
cp "$PROJECT_DIR/deploy/nginx.conf" "$BUILD_DIR/deploy/"
cp "$PROJECT_DIR/deploy/bt-nginx.conf" "$BUILD_DIR/deploy/"
cp "$PROJECT_DIR/deploy/Dockerfile.backend" "$BUILD_DIR/deploy/"
cp "$PROJECT_DIR/deploy/Dockerfile.frontend" "$BUILD_DIR/deploy/"
cp "$PROJECT_DIR/deploy/init.sql" "$BUILD_DIR/deploy/"
cp "$PROJECT_DIR/deploy/env.production" "$BUILD_DIR/deploy/"
cp "$PROJECT_DIR/docker-compose.yml" "$BUILD_DIR/"
cp "$PROJECT_DIR/docker-compose.backend-only.yml" "$BUILD_DIR/"
cp -r "$PROJECT_DIR/frontend" "$BUILD_DIR/frontend"
# 后端源码（Docker 多阶段构建需要源码来检测缓存失效）
cp "$PROJECT_DIR/backend/pom.xml" "$BUILD_DIR/backend/"
cp -r "$PROJECT_DIR/backend/src" "$BUILD_DIR/backend/src"
cp "$PROJECT_DIR/backend/target/realtime-video-backend-1.0.0.jar" "$BUILD_DIR/backend/target/"
# .dockerignore (如果存在)
cp "$PROJECT_DIR/backend/.dockerignore" "$BUILD_DIR/backend/" 2>/dev/null || true
echo "  ✅ 文件复制完成"

# 4. 打包
echo "[4/4] 生成 tar.gz..."
cd /tmp
tar czf realtime-video-build.tar.gz realtime-video-build/
echo ""
echo "=========================================="
echo "  🎉 部署包已生成！"
echo "=========================================="
echo ""
echo "  包路径: /tmp/realtime-video-build.tar.gz"
echo "  大小: $(du -h /tmp/realtime-video-build.tar.gz | cut -f1)"
echo ""
echo "  上传到服务器:"
echo "  scp /tmp/realtime-video-build.tar.gz root@服务器IP:/tmp/"
echo ""
echo "  在服务器上执行:"
echo "  cd /tmp && tar xzf realtime-video-build.tar.gz"
echo "  cd realtime-video-build && bash deploy/deploy.sh"
echo "=========================================="
