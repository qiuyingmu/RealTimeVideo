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
  // 1. 后端完整启动日志
  console.log('=== 后端完整日志 (最近30条) ===');
  const logs = await exec('docker logs --tail 30 realtimevideo-backend 2>&1');
  console.log(logs.out);
  
  console.log('\n=== 检查后端是否可访问 ===');
  const health = await exec('curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/');
  console.log(`后端 / 状态: ${health.out}`);
  
  const health2 = await exec('curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/health');
  console.log(`后端 /api/health 状态: ${health2.out}`);

  const health3 = await exec('curl -s http://127.0.0.1:8080/api/health');
  console.log(`后端 /api/health 响应: ${health3.out}`);

  console.log('\n=== 检查 Docker compose 配置 ===');
  const compose = await exec('cat /opt/realtime-video/docker-compose.yml');
  console.log(compose.out);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
