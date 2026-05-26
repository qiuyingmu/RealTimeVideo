#!/usr/bin/env python3
"""Check backend config files on server"""

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

for path in ["backend/src/main/resources/application.yml", "backend/src/main/resources/application-prod.yml", "backend/src/main/resources/application-dev.yml"]:
    cmd = f"cat {REMOTE_DIR}/{path} 2>/dev/null || echo 'FILE NOT FOUND: {path}'"
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=10)
    out = stdout.read().decode("utf-8", errors="replace")
    print(f"\n{'='*60}")
    print(f"[{path}]")
    print(f"{'='*60}")
    print(out)

ssh.close()
