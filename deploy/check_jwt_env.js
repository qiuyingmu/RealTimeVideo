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
  // 检查 .env 文件
  console.log('=== .env 文件 ===');
  const r1 = await exec('cat /opt/realtime-video/.env 2>/dev/null || echo ".env 不存在"');
  console.log(r1.out);

  // 检查环境变量
  console.log('\n=== 容器环境变量 ===');
  const r2 = await exec('docker exec realtimevideo-backend env | grep -E "JWT_SECRET|EZS_APP"');
  console.log(r2.out || '(无匹配)');

  // 检查 JWT 相关代码
  console.log('\n=== 后端 JWT 配置 ===');
  const r3 = await exec('docker exec realtimevideo-backend find / -name "*.class" -path "*jwt*" 2>/dev/null | head -5');
  console.log(r3.out || '(无.class文件)');
  
  const r4 = await exec('docker exec realtimevideo-backend find / -name "*.class" -path "*security*" 2>/dev/null | head -5');
  console.log(r4.out || '(无security .class)');

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
