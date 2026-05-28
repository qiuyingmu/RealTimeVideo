#!/usr/bin/env python3
"""Check frontend logs after fix attempt"""

import paramiko
import time

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
REMOTE_DIR = "/opt/realtime-video"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, PORT, USER, PASSWORD, look_for_keys=False, allow_agent=False, timeout=15)

def run(cmd, timeout=15):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout, get_pty=True)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    print(f"\n[{cmd[:50]}...]")
    if out.strip():
        print(out)
    if err.strip():
        print(err)

run(f"cd {REMOTE_DIR} && docker compose ps -a")

time.sleep(3)
print("\n=== LATEST FRONTEND LOGS ===")
run(f"cd {REMOTE_DIR} && docker compose logs --tail=20 frontend")

print("\n=== NGINX CONFIG ON SERVER ===")
run(f"cat {REMOTE_DIR}/deploy/nginx.conf | head -50")

ssh.close()
