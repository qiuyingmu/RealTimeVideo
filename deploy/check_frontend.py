#!/usr/bin/env python3
"""Check frontend status and logs"""

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
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    print(f"\n[{cmd[:60]}...]")
    if out.strip():
        print(out)
    if err.strip():
        print(err)
    return exit_status

time.sleep(5)
print("=== DOCKER PS ===")
run(f"cd {REMOTE_DIR} && docker compose ps")

print("\n=== FRONTEND LOGS ===")
run(f"cd {REMOTE_DIR} && docker compose logs --tail=30 frontend")

print("\n=== NGINX CONFIG ===")
run(f"cat {REMOTE_DIR}/deploy/nginx.conf")

ssh.close()
