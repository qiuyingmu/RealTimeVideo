#!/bin/bash
# ============================================================
# RealTimeVideo — 宝塔面板部署脚本
#
# 前提：宝塔面板已安装，且服务器已安装 Docker + Docker Compose
#
# 步骤：
#   1. 将构建包上传到服务器任意目录
#   2. 解压到 /opt/realtime-video
#   3. 配置 .env 文件
#   4. 运行此脚本
#   5. 在宝塔面板中配置站点
# ============================================================

set -e

PROJECT_DIR="/opt/realtime-video"

echo "========================================"
echo "  RealTimeVideo 宝塔面板部署"
echo "========================================"

# 1. 检查目录
echo "[1/6] 检查项目目录..."
cd "$PROJECT_DIR" || { echo "❌ 目录 $PROJECT_DIR 不存在"; exit 1; }

# 2. 检查 .env
echo "[2/6] 检查环境变量..."
if [ ! -f ".env" ]; then
    echo "⚠️  未找到 .env 文件，从模板创建..."
    cp deploy/env.production .env
    echo ""
    echo "请编辑 $PROJECT_DIR/.env 填入真实配置："
    echo "  - EZS_APP_KEY"
    echo "  - EZS_APP_SECRET"
    echo "  - JWT_SECRET"
    echo "  - CORS_ALLOWED_ORIGINS（重要！填你的域名，如 https://video.example.com）"
    echo "然后重新运行本脚本"
    exit 1
fi

# 检查 CORS 是否已配置
if ! grep -q "CORS_ALLOWED_ORIGINS" .env 2>/dev/null; then
    echo ""
    echo "⚠️  未检测到 CORS_ALLOWED_ORIGINS，建议添加（否则登录会报 403）："
    echo "   echo 'CORS_ALLOWED_ORIGINS=https://你的域名' >> .env"
    echo "  然后重新运行本脚本"
    echo ""
fi

# 加载环境变量
set -a
source .env
set +a

# 3. 停止旧容器（使用专属项目名避免冲突）
echo "[3/6] 停止旧容器..."
docker compose -p realtime-video -f docker-compose.backend-only.yml down 2>/dev/null || true

# 4. 构建并启动后端服务
echo "[4/6] 启动后端服务（数据库 + API）..."
docker compose -p realtime-video -f docker-compose.backend-only.yml up -d --build

# 5. 健康检查
echo "[5/6] 健康检查（等待 15 秒）..."
sleep 15
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8081/api/health 2>/dev/null || echo "000")

# 6. 输出结果
echo ""
echo "========================================"
echo "  部署结果"
echo "========================================"
echo ""
echo "  后端 API:    $([ "$HEALTH" = "200" ] && echo '✅ 正常' || echo "❌ 异常 ($HEALTH)")"
echo "  后端端口:    127.0.0.1:8081（仅本机可访问）"
echo ""
if [ "$HEALTH" != "200" ]; then
    echo "  查看日志: docker compose -p realtime-video logs -f backend"
fi
echo ""
echo "━━━━ 下一步：配置宝塔站点 ━━━━"
echo ""
echo "  1. 宝塔面板 → 网站 → 添加站点（输入你的域名）"
echo "  2. 上传前端文件："
echo "     将 frontend/dist/ 下的所有文件上传到站点根目录"
echo "     （scp -r realtime-video-build/frontend/dist/* /www/wwwroot/你的域名/）"
echo ""
echo "  3. 修改站点配置："
echo "     将 deploy/bt-nginx.conf 的内容替换站点的配置文件"
echo "     注意修改 server_name 和 root 路径"
echo ""
echo "  4. （可选）在宝塔面板中开启 SSL"
echo ""
echo "========================================"
