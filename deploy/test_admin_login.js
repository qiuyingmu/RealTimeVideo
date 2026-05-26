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
  // 尝试各种密码
  const passwords = ['admin123', 'Admin@123456', 'admin', 'Admin123', '123456', 'password', 'Admin@2024', 'Admin@2025', 'Admin@2026', 'realtimevideo'];
  
  for (const pw of passwords) {
    const r = await exec(`curl -s -w "|%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"${pw}"}'`);
    const parts = r.out.split('|');
    const body = parts[0];
    const httpCode = parts[1];
    const success = body.includes('"success":true');
    console.log(`admin / ${pw.padEnd(20)} → ${success ? '✅' : '❌'} HTTP ${httpCode}`);
  }

  // 尝试 adminQYM
  const r = await exec(`curl -s -w "|%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"adminQYM","password":"admin123"}'`);
  console.log(`adminQYM / admin123 → ${r.out.includes('success":true') ? '✅' : '❌'} HTTP ${r.out.split('|')[1]}`);

  conn.end();
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
