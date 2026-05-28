#!/usr/bin/env python3
"""SCP upload + SSH deploy script for RealTimeVideo"""

import paramiko
import os
import sys
import time

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
LOCAL_TAR = r"D:\EnlinYue\Claw\Code\RealTimeVideo\deploy\realtime-video-deploy.tar.gz"
REMOTE_DIR = "/opt/realtime-video"

def run_ssh(client, command, timeout=300, label=""):
    """Run a command via SSH and print output"""
    print(f"\n{'='*60}")
    print(f"[{label or command[:50]}]")
    print(f"{'='*60}")
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout, get_pty=True)
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip():
        print(out)
    if err.strip():
        print(err, file=sys.stderr)
    if exit_status != 0:
        print(f"⚠️  Exit code: {exit_status}")
    return exit_status, out, err

def main():
    # Validate local tarball
    if not os.path.exists(LOCAL_TAR):
        print(f"❌ Local tarball not found: {LOCAL_TAR}")
        sys.exit(1)
    size = os.path.getsize(LOCAL_TAR)
    print(f"✅ Local tarball: {LOCAL_TAR} ({size/1024:.1f} KB)")

    # Connect
    print(f"\n🔌 Connecting to {HOST}:{PORT} as {USER}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, PORT, USER, PASSWORD, look_for_keys=False, allow_agent=False, timeout=15)
    print("✅ Connected!")

    # SCP upload
    print(f"\n📤 Uploading tarball to {REMOTE_DIR}/...")
    sftp = ssh.open_sftp()
    
    # Ensure remote dir exists
    run_ssh(ssh, f"mkdir -p {REMOTE_DIR}", label="Create remote dir")
    
    remote_path = f"{REMOTE_DIR}/realtime-video-deploy.tar.gz"
    sftp.put(LOCAL_TAR, remote_path)
    sftp.close()
    print("✅ Upload complete!")

    # Check if .env exists
    print("\n🔍 Checking .env file...")
    _, out, _ = run_ssh(ssh, f"ls -la {REMOTE_DIR}/.env 2>/dev/null; echo '---EXIT:$?'", label="Check .env")
    
    # Extract tarball (overwrite existing files)
    print("\n📦 Extracting tarball (overwrite)...")
    run_ssh(ssh, f"cd {REMOTE_DIR} && tar xzf realtime-video-deploy.tar.gz", label="Extract")
    run_ssh(ssh, f"rm -f {REMOTE_DIR}/realtime-video-deploy.tar.gz", label="Cleanup tarball")

    # Ensure .env exists
    print("\n🔧 Ensuring .env file...")
    run_ssh(ssh, f"""
if [ ! -f {REMOTE_DIR}/.env ]; then
    cp {REMOTE_DIR}/deploy/env.production {REMOTE_DIR}/.env
    echo "✅ Created .env from env.production"
fi
""", label="Setup .env")

    # Verify docker-compose.yml exists
    run_ssh(ssh, f"ls -la {REMOTE_DIR}/docker-compose.yml", label="Verify docker-compose.yml")
    run_ssh(ssh, f"ls -la {REMOTE_DIR}/deploy/Dockerfile.backend", label="Verify Dockerfile.backend")

    # Step 1: Build backend
    print("\n🐳 Building backend image (this may take 3-8 min first time)...")
    run_ssh(ssh, f"cd {REMOTE_DIR} && docker compose build backend", timeout=600, label="docker compose build backend")

    # Step 2: Start services
    print("\n🚀 Starting services...")
    run_ssh(ssh, f"cd {REMOTE_DIR} && docker compose up -d", timeout=120, label="docker compose up -d")

    # Step 3: Check status
    print("\n📊 Checking deployment status...")
    run_ssh(ssh, f"cd {REMOTE_DIR} && docker compose ps", label="docker compose ps")
    run_ssh(ssh, f"echo '--- Last 20 lines of backend logs ---' && cd {REMOTE_DIR} && docker compose logs --tail=20 backend", label="Backend logs (tail)")

    ssh.close()
    
    print("\n" + "="*60)
    print("🎉 部署完成！")
    print("="*60)
    print(f"   访问地址: http://{HOST}")
    print(f"   查看日志: docker compose logs -f")
    print(f"   停止服务: docker compose down")
    print("="*60)

if __name__ == "__main__":
    main()
