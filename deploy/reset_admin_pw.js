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
  try {
    // 1. 在服务器上安装 bcryptjs
    console.log('📦 安装 bcryptjs (服务器端)...');
    const r1 = await exec('npm install bcryptjs -g 2>&1', 60000);
    console.log(r1.out.substring(0, 300));

    // 2. 生成 BCrypt hash (密码: Admin@123456, 强度12)
    console.log('\n🔑 生成密码哈希...');
    const r2 = await exec('node -e "const b=require(\\"bcryptjs\\");console.log(b.hashSync(\\"Admin@123456\\",12))" 2>&1');
    const hash = r2.out.trim();
    console.log(`Hash: ${hash}`);

    // 3. 更新 admin 密码
    console.log('\n🔄 更新 admin 密码...');
    // Escape single quotes in the hash by doubling them for SQL
    const safeHash = hash.replace(/'/g, "''");
    const r3 = await exec(`docker exec realtimevideo-db mysql -uvideoapp -pVideoApp@2026 realtime_video -e "UPDATE users SET password='${safeHash}', failed_login_attempts=0, account_non_locked=TRUE, lock_time=NULL WHERE username='admin';" 2>&1`);
    console.log(r3.out);

    // 4. 验证
    console.log('\n✅ 验证新密码...');
    const r4 = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"Admin@123456"}'`);
    console.log(r4.out);

    console.log('\n🎉 完成!');
    console.log('管理员登录信息:');
    console.log('  用户名: admin');
    console.log('  密码: Admin@123456');

  } catch (err) {
    console.error('❌', err.message);
  } finally {
    conn.end();
  }
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
