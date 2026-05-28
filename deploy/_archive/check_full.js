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
  // 获取从启动开始到现在的完整日志，过滤错误部分
  console.log('=== 后端启动完整日志 (前50行) ===');
  const r1 = await exec('docker logs realtimevideo-backend 2>&1 | head -50');
  console.log(r1.out);

  console.log('\n=== 包含 ERROR 或 Exception 的行 ===');
  const r2 = await exec('docker logs realtimevideo-backend 2>&1 | grep -i -E "error|exception|cause|at com.enlin" | head -40');
  console.log(r2.out || '(无匹配)');

  console.log('\n=== Nginx 日志中返回 500 的请求 ===');
  const r3 = await exec('docker logs realtimevideo-frontend 2>&1 | grep " 500 " | head -10');
  console.log(r3.out || '(无 500 请求)');

  console.log('\n=== 用正确密码测试登录 ===');
  const r4 = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}'`);
  console.log(r4.out);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
