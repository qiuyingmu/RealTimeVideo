#!/bin/bash
# ============================================================
# RealTimeVideo 一键部署脚本（在阿里云服务器上运行）
# 已将变更推送到 GitHub，服务器直接拉取即可
#
# 使用方法：
#   ssh root@47.108.204.59
#   # 粘贴以下命令执行
# ============================================================

set -e

echo "========================================"
echo "  RealTimeVideo 部署开始"
echo "  仓库: https://github.com/qiuyingmu/RealTimeVideo"
echo "========================================"

# 1. 进入项目目录
cd /opt/realtime-video || { echo "❌ 目录不存在，请先 git clone"; exit 1; }

# 2. 拉取最新代码
echo "[1/5] 拉取最新代码..."
git fetch origin main
git reset --hard origin/main

# 3. 使用 Docker Maven 构建后端
echo "[2/5] 构建后端 JAR..."
cd /opt/realtime-video/backend
docker run --rm \
  -v "$(pwd)":/build \
  -v maven-repo:/root/.m2 \
  -w /build \
  maven:3.9-eclipse-temurin-17 \
  mvn clean package -DskipTests -B

# 4. 使用 Docker Node 构建前端
echo "[3/5] 构建前端..."
cd /opt/realtime-video/frontend
docker run --rm \
  -v "$(pwd)":/build \
  -w /build \
  node:20-alpine \
  sh -c "npm ci && npm run build"

# 5. 重启服务
echo "[4/5] 重启 Docker 服务..."
cd /opt/realtime-video
docker compose down
docker compose up -d --build

# 6. 健康检查
echo "[5/5] 健康检查（等待 15 秒）..."
sleep 15
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health 2>/dev/null || echo "000")
FRONTEND=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:80 2>/dev/null || echo "000")

echo ""
echo "========================================"
echo "  部署结果"
echo "  后端 API:    $([ "$HEALTH" = "200" ] && echo '✅ 正常' || echo "❌ 异常 ($HEALTH)")"
echo "  前端页面:    $([ "$FRONTEND" = "200" ] || [ "$FRONTEND" = "304" ] && echo '✅ 正常' || echo "❌ 异常 ($FRONTEND)")"
echo "========================================"
echo ""
echo "如需查看日志: docker compose logs -f backend"
echo "========================================"
