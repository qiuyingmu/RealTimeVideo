#!/usr/bin/env node
const { Client } = require('ssh2');
const HOST = '47.108.204.59';
const conn = new Client();

function exec(cmd, timeout = 30000) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return reject(err);
      let out = '', errOut = '';
      const timer = setTimeout(() => { stream.close(); reject(new Error('Timeout')); }, timeout);
      stream.on('data', d => out += d.toString());
      stream.stderr.on('data', d => errOut += d.toString());
      stream.on('close', code => { clearTimeout(timer); resolve({ code, out, errOut }); });
    });
  });
}

conn.on('ready', async () => {
  console.log('=== 所有容器状态 ===');
  const r1 = await exec('docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"');
  console.log(r1.out);

  console.log('\n=== 容器详情 ===');
  const r2 = await exec('docker inspect realtimevideo-backend --format "State={{.State.Status}} ExitCode={{.State.ExitCode}} Error={{.State.Error}}"');
  console.log(r2.out);

  console.log('\n=== 容器日志 ===');
  const r3 = await exec('docker logs realtimevideo-backend 2>&1');
  console.log(r3.out || '(空)');

  console.log('\n=== docker-compose 启动 (重建后) ===');
  const r4 = await exec('cd /opt/realtime-video && docker compose up -d backend 2>&1', 30000);
  console.log(r4.out);

  console.log('\n=== 重启后状态 ===');
  await new Promise(r => setTimeout(r, 3000));
  const r5 = await exec('docker ps -a --filter name=realtimevideo-backend --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"');
  console.log(r5.out);

  if (r5.out.includes('Up')) {
    console.log('\n=== 启动日志 (最近20行) ===');
    const r6 = await exec('docker logs --tail 20 realtimevideo-backend 2>&1');
    console.log(r6.out);
    
    console.log('\n=== 健康检查 ===');
    const r7 = await exec('curl -s -w "\nHTTP:%{http_code}" http://127.0.0.1:8080/api/health');
    console.log(r7.out);
  }

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
