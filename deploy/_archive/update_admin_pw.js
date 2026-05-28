#!/usr/bin/env node
const { Client } = require('ssh2');
const bcrypt = require('bcryptjs');
const HOST = '47.108.204.59';
const conn = new Client();

// 生成 BCrypt hash (强度12, 与后端一致)
const hash = bcrypt.hashSync('Admin@123456', 12);
console.log(`🔑 生成的哈希: ${hash}`);

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
  try {
    // 1. 更新 admin 密码
    console.log('🔄 更新 admin 密码...');
    const safeHash = hash.replace(/'/g, "\\'");
    const sql = `UPDATE users SET password='${hash}', failed_login_attempts=0, account_non_locked=TRUE, lock_time=NULL WHERE username='admin';`;
    const r1 = await exec(`docker exec realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video -e "${sql}" 2>&1`);
    console.log(r1.out);

    // 2. 验证
    console.log('\n✅ 验证新密码...');
    const r2 = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"Admin@123456"}'`);
    console.log(r2.out);

    // 3. 同时重置 adminQYM (被锁定)
    console.log('\n🔄 重置 adminQYM...');
    const sql2 = `UPDATE users SET failed_login_attempts=0, account_non_locked=TRUE, lock_time=NULL WHERE username='adminQYM';`;
    const r3 = await exec(`docker exec realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video -e "${sql2}" 2>&1`);
    console.log(r3.out);

    console.log('\n🎉 完成!');
    
  } catch (err) {
    console.error('❌', err.message);
  } finally {
    conn.end();
  }
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
