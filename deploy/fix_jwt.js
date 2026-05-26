#!/usr/bin/env node
const { Client } = require('ssh2');
const path = require('path');
const fs = require('fs');
const HOST = '47.108.204.59';
const conn = new Client();

function log(label, msg) { console.log(`[${new Date().toISOString().substring(11,19)}] [${label}] ${msg}`); }

function exec(cmd, timeout = 120000) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return reject(err);
      let out = '', errOut = '';
      const timer = setTimeout(() => { stream.close(); reject(new Error(`Timeout: ${cmd.substring(0,60)}`)); }, timeout);
      stream.on('data', d => out += d.toString());
      stream.stderr.on('data', d => errOut += d.toString());
      stream.on('close', code => { clearTimeout(timer); resolve({ code, out, errOut }); });
    });
  });
}

function sftpPut(local, remote) {
  return new Promise((resolve, reject) => {
    conn.sftp((err, sftp) => {
      if (err) return reject(err);
      sftp.fastPut(local, remote, err2 => { sftp.end(); err2 ? reject(err2) : resolve(); });
    });
  });
}

conn.on('ready', async () => {
  try {
    // 1. 上传修复后的 JwtService.java
    log('FIX', '上传修复后的 JwtService.java...');
    const localFile = path.resolve(__dirname, '..', 'backend', 'src', 'main', 'java', 'com', 'realtimevideo', 'service', 'JwtService.java');
    const remoteDir = '/tmp/backend-fix/com/realtimevideo/service/';
    await exec(`mkdir -p ${remoteDir}`);
    await sftpPut(localFile, `${remoteDir}JwtService.java`);
    log('FIX', '✅ 文件上传完成');

    // 2. 更新 .env 文件中的 JWT_SECRET
    log('ENV', '更新 .env 中的 JWT_SECRET...');
    // 先在 /opt/realtime-video/.env 中替换 JWT_SECRET
    const newSecret = 'JwWOo6aC/V5uV4wNm9jo4YsTBpKzOhDfhD5+y0rVM44=';
    
    // 使用 sed 替换 .env 中的 JWT_SECRET
    const envResult = await exec(
      `sed -i 's|JWT_SECRET=.*|JWT_SECRET=${newSecret}|' /opt/realtime-video/.env && echo "---.env内容---" && grep JWT_SECRET /opt/realtime-video/.env`,
      10000
    );
    console.log(envResult.out);

    // 3. 在服务器上用 docker compose 重新构建并启动后端
    log('DOCKER', '重建并重启后端容器...');
    log('DOCKER', '复制修复后的文件到 Docker build context...');
    
    // 方案：直接在服务器上修改对应文件然后重建
    // 先检查 Dockerfile.backend 的内容
    const dfResult = await exec('cat /opt/realtime-video/deploy/Dockerfile.backend');
    console.log('Dockerfile:', dfResult.out);
    
    // 方案：将修复后的 Java 文件放到服务器上对应位置，然后 docker compose build
    // 先确认本地 backend 源码目录结构
    await exec('mkdir -p /opt/realtime-video/backend/src/main/java/com/realtimevideo/service/');
    await sftpPut(localFile, '/opt/realtime-video/backend/src/main/java/com/realtimevideo/service/JwtService.java');
    log('DOCKER', '文件已同步到 build context');
    
    // 4. 重建并启动
    log('DOCKER', '执行 docker compose build backend...');
    const buildResult = await exec('cd /opt/realtime-video && docker compose build backend 2>&1', 600000);
    console.log(buildResult.out.substring(0, 500));
    
    log('DOCKER', '启动新后端容器...');
    const upResult = await exec('cd /opt/realtime-video && docker compose up -d backend 2>&1', 30000);
    console.log(upResult.out);

    // 5. 等待后端启动并验证
    log('VERIFY', '等待后端启动...');
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000));
      const health = await exec('curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/health');
      if (health.out.trim() === '200') {
        log('VERIFY', '✅ 后端启动成功!');
        break;
      }
      if (i === 29) log('VERIFY', '⚠️ 后端启动超时，请手动检查');
      else process.stdout.write('.');
    }
    console.log();

    // 6. 测试登录
    log('TEST', '测试登录接口...');
    const login = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"admin123"}'`);
    console.log(login.out);

  } finally {
    conn.end();
  }
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
