#!/usr/bin/env node
const { Client } = require('ssh2');
const bcrypt = require('bcryptjs');
const HOST = '47.108.204.59';
const conn = new Client();

// 生成 BCrypt hash (强度12)
const hash = bcrypt.hashSync('Admin@123456', 12);
console.log(`🔑 哈希: ${hash}`);

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
    // 用 base64 避免 shell 转义 $
    const sql = `UPDATE users SET password='${hash}', failed_login_attempts=0, account_non_locked=TRUE, lock_time=NULL WHERE username='admin';`;
    const b64 = Buffer.from(sql).toString('base64');
    
    console.log('🔄 更新 admin 密码...');
    const r1 = await exec(`echo ${b64} | base64 -d | docker exec -i realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video 2>&1`);
    console.log(r1.out);

    // 验证
    console.log('\n✅ 验证登录...');
    const r2 = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"Admin@123456"}'`);
    console.log(r2.out);

    // 重置 adminQYM 锁定
    console.log('\n🔄 重置 adminQYM...');
    const sql2 = "UPDATE users SET failed_login_attempts=0, account_non_locked=TRUE, lock_time=NULL WHERE username='adminQYM';";
    const b642 = Buffer.from(sql2).toString('base64');
    const r3 = await exec(`echo ${b642} | base64 -d | docker exec -i realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video 2>&1`);
    console.log(r3.out);

    console.log('\n🎉 完成!');
    console.log('========================================');
    console.log('  admin / Admin@123456');
    console.log('  user  / User@123456');
    console.log('========================================');
    
  } catch (err) {
    console.error('❌', err.message);
  } finally {
    conn.end();
  }
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
