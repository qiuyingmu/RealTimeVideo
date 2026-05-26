#!/usr/bin/env python3
"""Fix utf8mb4 charset issue and rebuild backend on server"""

import paramiko

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
REMOTE_DIR = "/opt/realtime-video"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, PORT, USER, PASSWORD, look_for_keys=False, allow_agent=False, timeout=15)
print("✅ Connected!")

def run(cmd, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout, get_pty=True)
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip():
        print(out[:500])
    if err.strip():
        print(err[:500], end="")
    return exit_status

# Fix docker-compose.yml on server
print("\n📝 Fixing docker-compose.yml on server...")
run(f"sed -i 's/utf8mb4/UTF-8/g' {REMOTE_DIR}/docker-compose.yml", timeout=10)

# Fix application.yml on server
print("📝 Fixing application.yml on server...")
run(f"sed -i 's/utf8mb4/UTF-8/g' {REMOTE_DIR}/backend/src/main/resources/application.yml", timeout=10)

# Verify fix
print("\n🔍 Verifying fix...")
run(f"grep 'characterEncoding' {REMOTE_DIR}/docker-compose.yml", timeout=10)
run(f"grep 'characterEncoding' {REMOTE_DIR}/backend/src/main/resources/application.yml", timeout=10)

# Rebuild backend (will use Docker cache, only recompile src)
print("\n🐳 Rebuilding backend image...")
run(f"cd {REMOTE_DIR} && docker compose build backend", timeout=600)

# Restart
print("\n🚀 Restarting services...")
run(f"cd {REMOTE_DIR} && docker compose up -d", timeout=120)

# Check logs
print("\n📊 Checking backend startup...")
import time
time.sleep(5)
run(f"cd {REMOTE_DIR} && docker compose logs --tail=30 backend", timeout=30)

ssh.close()
print("\n✅ Done!")
