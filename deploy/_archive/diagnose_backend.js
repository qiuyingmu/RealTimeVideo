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
  console.log('=== Docker 容器状态 ===');
  const ps = await exec('docker ps -a --filter name=realtimevideo-backend --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"');
  console.log(ps.out);

  console.log('\n=== 后端完整日志 ===');
  const logs = await exec('docker logs --tail 60 realtimevideo-backend 2>&1');
  console.log(logs.out);

  console.log('\n=== 尝试连接 ===');
  const c1 = await exec('curl -s -w "\nHTTP:%{http_code}" http://127.0.0.1:8080/api/health');
  console.log('localhost:', c1.out);

  // 检查网络
  console.log('\n=== 容器网络 ===');
  const net = await exec('docker inspect realtimevideo-backend --format "{{json .NetworkSettings.Networks}}"');
  console.log(net.out);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
