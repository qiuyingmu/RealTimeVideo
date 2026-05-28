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
    // 1. 更新 .env 中的 JWT_SECRET
    log('ENV', '更新 JWT_SECRET...');
    const newSecret = 'JwWOo6aC/V5uV4wNm9jo4YsTBpKzOhDfhD5+y0rVM44=';
    const envResult = await exec(
      `sed -i 's|JWT_SECRET=.*|JWT_SECRET='"${newSecret}"'|' /opt/realtime-video/.env && echo "---" && grep JWT_SECRET /opt/realtime-video/.env`,
      10000
    );
    console.log(envResult.out);

    // 2. 上传 JAR 到服务器
    log('UPLOAD', '上传 JAR 包到服务器...');
    const jarPath = path.resolve(__dirname, '..', 'backend', 'target', 'realtime-video-backend-1.0.0.jar');
    const jarSize = fs.statSync(jarPath).size;
    log('UPLOAD', `JAR 大小: ${(jarSize / 1024 / 1024).toFixed(1)}MB`);
    await sftpPut(jarPath, '/tmp/realtime-video-backend-1.0.0.jar');
    log('UPLOAD', '✅ 上传完成');

    // 3. 在服务器上用 Dockerfile 快速构建新镜像
    // 创建临时 Dockerfile，基于现有 JRE 镜像直接复制 JAR
    log('DOCKER', '构建新后端镜像...');
    const dockerfile = `
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache tzdata && \\
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \\
    echo "Asia/Shanghai" > /etc/timezone

COPY realtime-video-backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
`;
    
    // 写入 Dockerfile 并构建
    const buildDir = '/tmp/backend-quick-build';
    await exec(`mkdir -p ${buildDir}`);
    
    // 写入 Dockerfile（用 echo 避免特殊字符问题）
    // 使用 base64 编码传输 Dockerfile
    const dfBase64 = Buffer.from(dockerfile).toString('base64');
    await exec(`echo ${dfBase64} | base64 -d > ${buildDir}/Dockerfile.quick`, 10000);
    await exec(`cp /tmp/realtime-video-backend-1.0.0.jar ${buildDir}/`, 10000);
    
    // 构建镜像
    log('DOCKER', 'docker build...');
    const buildResult = await exec(`cd ${buildDir} && docker build -t realtime-video-backend:latest -f Dockerfile.quick . 2>&1`, 120000);
    console.log(buildResult.out.substring(0, 300));

    // 4. 停止旧容器并启动新容器
    log('DOCKER', '停止旧容器...');
    await exec('docker stop realtimevideo-backend && docker rm realtimevideo-backend', 15000);
    
    log('DOCKER', '启动新容器...');
    // 从 docker-compose.yml 获取启动参数（手动启动以使用相同配置）
    envResult2 = await exec('docker run -d --name realtimevideo-backend --restart unless-stopped --network opt_realtime-video_default -p 127.0.0.1:8080:8080 --env-file /opt/realtime-video/.env realtime-video-backend:latest', 15000);
    console.log(envResult2.out);

    // 5. 等待启动
    log('VERIFY', '等待后端启动...');
    let started = false;
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000));
      const health = await exec('curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/health');
      if (health.out.trim() === '200') {
        log('VERIFY', '✅ 后端启动成功!');
        started = true;
        break;
      }
      process.stdout.write('.');
    }
    console.log();

    if (started) {
      // 6. 测试登录
      log('TEST', '测试登录接口...');
      const login = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin123"}'`);
      console.log(login.out);

      // 7. 验证萤石云
      log('CHECK', '检查启动日志中是否有新错误...');
      const errors = await exec('docker logs --tail 40 realtimevideo-backend 2>&1 | grep -i -E "error|exception" | head -10');
      if (errors.out.trim()) {
        console.log('⚠️ 发现以下错误:');
        console.log(errors.out);
      } else {
        log('CHECK', '✅ 启动日志无错误');
      }
    }

    log('DONE', '部署完成!');
    console.log('\n========================================');
    console.log('  修复完成!');
    console.log('========================================');
    console.log(`  访问: http://${HOST}`);
    console.log('========================================');

  } catch (err) {
    console.error('\n❌ 失败:', err.message);
  } finally {
    conn.end();
  }
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
