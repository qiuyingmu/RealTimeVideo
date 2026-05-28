#!/usr/bin/env node
const { Client } = require('ssh2');

const HOST = '47.108.204.59';
const conn = new Client();

const commands = [
  { label: '系统资源', cmd: `echo "=== CPU ===" && top -bn1 | head -5 && echo "=== 内存 ===" && free -h && echo "=== 磁盘 ===" && df -h /` },
  { label: 'Docker 容器', cmd: `docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"` },
  { label: 'Docker 资源占用', cmd: `docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"` },
  { label: '端口监听', cmd: `ss -tlnp | grep -E ':(80|8080|3306|443)'` },
  { label: '后端日志(最近5条)', cmd: `docker logs --tail 5 realtimevideo-backend 2>&1` },
  { label: 'Nginx 日志(最近5条)', cmd: `docker logs --tail 5 realtimevideo-frontend 2>&1` },
  { label: 'Docker 镜像列表', cmd: `docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"` },
];

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
  console.log('✅ SSH 连接成功\n');
  for (const { label, cmd } of commands) {
    console.log(`╔══════════════════════════════════════╗`);
    console.log(`║  ${label.padEnd(35)}║`);
    console.log(`╚══════════════════════════════════════╝`);
    try {
      const r = await exec(cmd);
      console.log(r.out || '(空)');
      if (r.errOut) console.error('STDERR:', r.errOut);
    } catch (e) {
      console.error(`❌ 执行失败: ${e.message}`);
    }
    console.log('');
  }
  conn.end();
  console.log('✅ 诊断完成');
});

conn.on('error', err => {
  console.error('❌ SSH 连接失败:', err.message);
  process.exit(1);
});

conn.connect({
  host: HOST, port: 22,
  username: 'root',
  password: 'Zq010722.',
  readyTimeout: 15000,
});
