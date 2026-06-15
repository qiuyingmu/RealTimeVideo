#!/bin/bash
# ============================================================
# RealTimeVideo 数据库自动备份脚本
#
# 用法：
#   bash deploy/backup-db.sh              # 手动执行一次备份
#   bash deploy/backup-db.sh --setup-cron # 安装定时任务（每日凌晨3点）
# ============================================================

set -e

BACKUP_DIR="/opt/realtime-video/backups"
DB_CONTAINER="realtimevideo-db"
DB_NAME="realtime_video"
DB_USER="videoapp"
DB_PASS="${MYSQL_PASSWORD:-VideoApp@2026}"
RETENTION_DAYS=7

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

function info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
function warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
function error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ──────── 执行备份 ────────

function do_backup() {
    mkdir -p "$BACKUP_DIR"

    # 文件名含日期
    local DATE=$(date +%Y%m%d_%H%M%S)
    local FILE="$BACKUP_DIR/realtime_video_$DATE.sql.gz"

    info "开始备份数据库: $DB_NAME"

    # 检查容器是否运行
    if ! docker ps --format '{{.Names}}' | grep -q "^$DB_CONTAINER$"; then
        error "数据库容器 $DB_CONTAINER 未运行，备份失败"
        return 1
    fi

    # 用 mysqldump 备份并压缩
    docker exec "$DB_CONTAINER" mysqldump \
        -u"$DB_USER" -p"$DB_PASS" \
        --single-transaction \
        --routines \
        --triggers \
        "$DB_NAME" | gzip > "$FILE"

    # 验证
    local SIZE=$(du -h "$FILE" | cut -f1)
    if [ -s "$FILE" ]; then
        info "备份完成: $FILE ($SIZE)"
    else
        error "备份文件为空，删除"
        rm -f "$FILE"
        return 1
    fi

    # 删除 7 天前的旧备份
    info "清理 $RETENTION_DAYS 天前的旧备份..."
    find "$BACKUP_DIR" -name "realtime_video_*.sql.gz" -mtime +$RETENTION_DAYS -delete

    # 统计
    local COUNT=$(ls "$BACKUP_DIR"/*.sql.gz 2>/dev/null | wc -l)
    info "当前共 $COUNT 个备份文件"
}

# ──────── 安装定时任务 ────────

function setup_cron() {
    local SCRIPT_PATH=$(realpath "$0")
    local CRON_JOB="0 3 * * * cd $(dirname "$SCRIPT_PATH")/.. && bash $SCRIPT_PATH >> /var/log/realtime-video-backup.log 2>&1"

    info "安装定时备份任务（每日凌晨 3:00）..."

    # 检查是否已存在
    if crontab -l 2>/dev/null | grep -q "$SCRIPT_PATH"; then
        warn "定时任务已存在，跳过"
    else
        (crontab -l 2>/dev/null; echo "$CRON_JOB") | crontab -
        info "定时任务安装成功"
    fi

    echo ""
    echo "  ┌────────────────────────────────────────┐"
    echo "  │  备份文件位置: $BACKUP_DIR  │"
    echo "  │  保留天数:     $RETENTION_DAYS 天                     │"
    echo "  │  查看日志:     cat /var/log/realtime-video-backup.log │"
    echo "  └────────────────────────────────────────┘"
}

# ──────── 主入口 ────────

case "${1:-}" in
    --setup-cron)
        do_backup
        setup_cron
        ;;
    --help|-h)
        echo "用法: bash $0 [选项]"
        echo ""
        echo "选项:"
        echo "  (无)          执行一次备份"
        echo "  --setup-cron  执行备份 + 安装每日定时任务"
        echo "  --help        显示帮助"
        ;;
    *)
        do_backup
        ;;
esac
