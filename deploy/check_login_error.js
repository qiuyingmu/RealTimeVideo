#!/usr/bin/env node
const { Client } = require('ssh2');
const HOST = '47.108.204.59';
const conn = new Client();

function exec(cmd) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return reject(err);
      let out = '', errOut = '';
      stream.on('data', d => out += d.toString());
      stream.stderr.on('data', d => errOut += d.toString());
      stream.on('close', code => resolve({ code, out, errOut }));
    });
  });
}

conn.on('ready', async () => {
  // 获取完整后端日志，从启动开始
  console.log('=== 后端完整日志 (最近80条) ===');
  const r = await exec('docker logs --tail 80 realtimevideo-backend 2>&1 | head -80');
  console.log(r.out);

  // 模拟登录请求，获取实际错误
  console.log('\n=== 模拟登录请求 ===');
  const login = await exec(`curl -s -w "\nHTTP_CODE:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}'`);
  console.log(login.out);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
