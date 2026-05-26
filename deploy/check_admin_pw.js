#!/usr/bin/env node
const { Client } = require('ssh2');
const HOST = '47.108.204.59';
const conn = new Client();

function exec(cmd, timeout = 15000) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return reject(err);
      let out = '', errOut = '';
      const timer = setTimeout(() => { stream.close(); reject(new Error('Timeout')); }, timeout);
      stream.on('data', d => out += d.toString());
      stream.stderr.on('data', d => errOut += d.toString());
      stream.on('close', code => resolve({ code, out, errOut }));
    });
  });
}

conn.on('ready', async () => {
  console.log('=== admin用户密码hash ===');
  const r1 = await exec(`docker exec realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video -e "SELECT id,username,password,role,failed_login_attempts,account_non_locked FROM users WHERE username='admin';" 2>&1`);
  console.log(r1.out);

  console.log('=== 所有用户 ===');
  const r2 = await exec(`docker exec realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video -e "SELECT id,username,role,failed_login_attempts,account_non_locked FROM users ORDER BY id;" 2>&1`);
  console.log(r2.out);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
