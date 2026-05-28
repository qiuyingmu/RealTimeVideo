#!/usr/bin/env python3
"""Verify backend started successfully"""

import paramiko

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
REMOTE_DIR = "/opt/realtime-video"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, PORT, USER, PASSWORD, look_for_keys=False, allow_agent=False, timeout=15)

def run(cmd, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout, get_pty=True)
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    return out

# Check if backend is running
ps_out = run(f"cd {REMOTE_DIR} && docker compose ps --filter status=running backend 2>/dev/null")
print(f"[docker compose ps backend]:\n{ps_out}")

# Full logs
logs = run(f"cd {REMOTE_DIR} && docker compose logs --tail=50 backend", timeout=30)
print(f"[Backend logs]:\n{logs}")

# Check for "Started" or errors
if "Started" in logs:
    print("\n✅ BACKEND STARTED SUCCESSFULLY!")
elif "ERROR" in logs:
    print("\n❌ BACKEND HAS ERRORS!")
else:
    print("\n⚠️ Backend log truncated, checking again in 5s...")
    import time
    time.sleep(5)
    logs2 = run(f"cd {REMOTE_DIR} && docker compose logs --tail=50 backend", timeout=30)
    print(logs2)
    if "Started" in logs2:
        print("\n✅ BACKEND STARTED SUCCESSFULLY!")
    elif "ERROR" in logs2:
        print("\n❌ BACKEND HAS ERRORS!")

ssh.close()
