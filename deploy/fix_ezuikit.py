#!/usr/bin/env python3
"""Fix EZUIKit CDN proxy in nginx and rebuild frontend"""

import paramiko
import os

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
REMOTE_DIR = "/opt/realtime-video"
LOCAL_NGINX_CONF = "D:\\EnlinYue\\Claw\\Code\\RealTimeVideo\\deploy\\nginx.conf"

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
        print(out[:800])
    if err.strip():
        print(err[:500], end="")
    return exit_status

# Upload updated nginx.conf
print("\n📤 Uploading updated nginx.conf...")
sftp = ssh.open_sftp()
sftp.put(LOCAL_NGINX_CONF, f"{REMOTE_DIR}/deploy/nginx.conf")
sftp.close()
print("✅ nginx.conf uploaded!")

# Rebuild frontend
print("\n🐳 Rebuilding frontend image...")
run(f"cd {REMOTE_DIR} && docker compose build frontend", timeout=600)

# Restart
print("\n🚀 Restarting all services...")
run(f"cd {REMOTE_DIR} && docker compose up -d", timeout=120)

# Check status
import time
time.sleep(3)
print("\n📊 Checking status...")
run(f"cd {REMOTE_DIR} && docker compose ps", timeout=10)

# Quick test - check if /ezuikit_cdn is accessible
print("\n🔍 Testing /ezuikit_cdn proxy...")
run(f"curl -s -o /dev/null -w '%{{http_code}} %{{content_type}}' http://localhost/ezuikit_cdn/PlayCtrlWasm/playCtrl1/HasSIMD/Decoder.js", timeout=15)

ssh.close()
print("\n✅ Done!")
