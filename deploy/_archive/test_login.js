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
      stream.on('close', code => { clearTimeout(timer); resolve({ code, out, errOut }); });
    });
  });
}

conn.on('ready', async () => {
  console.log('=== 测试 admin/Admin@123456 ===');
  const r1 = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin@123456"}'`);
  console.log(r1.out);

  console.log('\n=== 测试 user/User@123456 ===');
  const r2 = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"user","password":"User@123456"}'`);
  console.log(r2.out);

  // 如果有 token，测试认证后的请求
  try {
    const resp = JSON.parse(r1.out.split('\n')[0]);
    if (resp.success && resp.data?.accessToken) {
      const token = resp.data.accessToken;
      console.log('\n=== 访问 /api/ezviz/channels (需认证) ===');
      const r3 = await exec(`curl -s -w "\nHTTP:%{http_code}" http://127.0.0.1:8080/api/ezviz/channels \
        -H "Authorization: Bearer ${token}"`);
      console.log(r3.out);
    }
  } catch(e) {}

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
