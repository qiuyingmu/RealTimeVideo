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
  // 查数据库用户
  console.log('=== 数据库中的用户 ===');
  const r1 = await exec(`docker exec realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video -e "SELECT id,username,role FROM users;" 2>&1`);
  console.log(r1.out);

  // 查初始化 SQL 
  console.log('\n=== init.sql 中的用户 ===');
  const r2 = await exec('cat /opt/realtime-video/deploy/init.sql 2>&1 | head -30');
  console.log(r2.out);

  // 查后端配置中是否有默认密码
  console.log('\n=== 后端 application-prod.yml ===');
  const r3 = await exec('docker exec realtimevideo-backend find / -name "application*.yml" -o -name "application*.yaml" -o -name "application*.properties" 2>/dev/null | head -5');
  console.log(r3.out);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
