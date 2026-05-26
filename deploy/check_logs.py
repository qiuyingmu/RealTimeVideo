#!/usr/bin/env python3
"""Check backend logs on the server"""

import paramiko
import sys

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
REMOTE_DIR = "/opt/realtime-video"

def run_ssh(client, command, timeout=30, label=""):
    print(f"\n{'='*60}")
    print(f"[{label}]")
    print(f"{'='*60}")
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout, get_pty=True)
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip():
        print(out)
    if err.strip():
        print(err, file=sys.stderr)
    return exit_status, out, err

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, PORT, USER, PASSWORD, look_for_keys=False, allow_agent=False, timeout=15)
print("✅ Connected!")

# Full backend logs
run_ssh(ssh, f"cd {REMOTE_DIR} && docker compose logs --tail=100 backend", timeout=30, label="Backend full logs")

# Check nginx config
run_ssh(ssh, f"cat {REMOTE_DIR}/deploy/nginx.conf", timeout=10, label="nginx.conf")

ssh.close()
