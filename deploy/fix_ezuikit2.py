#!/usr/bin/env python3
"""Upload fixed nginx.conf and rebuild frontend (v2)"""

import paramiko

HOST = "47.108.204.59"
PORT = 22
USER = "root"
PASSWORD = "Zq010722."
REMOTE_DIR = "/opt/realtime-video"
LOCAL_NGINX = "D:\\EnlinYue\\Claw\\Code\\RealTimeVideo\\deploy\\nginx.conf"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, PORT, USER, PASSWORD, look_for_keys=False, allow_agent=False, timeout=15)
print("✅ Connected!")

def run(cmd, timeout=60):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout, get_pty=True)
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip():
        print(out[:500])
    if err.strip():
        print(err[:300], end="")
    return exit_status

# Upload nginx.conf
print("\n📤 Uploading nginx.conf...")
sftp = ssh.open_sftp()
sftp.put(LOCAL_NGINX, f"{REMOTE_DIR}/deploy/nginx.conf")
sftp.close()
print("✅ Uploaded!")

# Rebuild frontend (nobody version change, Docker should cache npm build)
print("\n🐳 Rebuilding frontend image...")
run(f"cd {REMOTE_DIR} && docker compose build frontend", timeout=600)

# Restart
print("\n🚀 Restarting frontend...")
run(f"cd {REMOTE_DIR} && docker compose up -d", timeout=120)

# Wait for Nginx to be ready
import time
time.sleep(5)

# Verify
print("\n📊 Checking status...")
run(f"cd {REMOTE_DIR} && docker compose ps", timeout=10)

print("\n🔍 Testing EZUIKit proxy...")
run("curl -s -o /dev/null -w 'Decoder.js: HTTP %{http_code}, Content-Type: %{content_type}\n' http://localhost/ezuikit_cdn/PlayCtrlWasm/playCtrl1/HasSIMD/Decoder.js", timeout=15)
run("curl -s -o /dev/null -w 'Decoder.wasm: HTTP %{http_code}, Content-Type: %{content_type}\n' http://localhost/ezuikit_cdn/PlayCtrlWasm/playCtrl1/HasSIMD/Decoder.wasm 2>/dev/null || echo 'WASM not found'", timeout=15)

ssh.close()
print("\n✅ Done!")
