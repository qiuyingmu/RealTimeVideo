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
      const timer = setTimeout(() => { stream.close(); reject(new Error('Timeout')); }, timeout);
      stream.on('data', d => out += d.toString());
      stream.stderr.on('data', d => errOut += d.toString());
      stream.on('close', code => { clearTimeout(timer); resolve({ code, out, errOut }); });
    });
  });
}

conn.on('ready', async () => {
  try {
    // 1. 清理卡住的容器
    log('CLEAN', '清理旧容器...');
    const r1 = await exec('docker rm -f realtimevideo-backend 2>&1');
    console.log(r1.out);

    // 2. 确认网络存在
    log('NET', '检查网络...');
    const r2 = await exec('docker network ls --filter name=realtime-video --format "{{.Name}}"');
    if (!r2.out.trim()) {
      log('NET', '网络不存在，创建...');
      await exec('docker network create opt_realtime-video_default');
    } else {
      log('NET', `网络已存在: ${r2.out.trim()}`);
    }

    // 3. 用 docker-compose 启动
    log('UP', 'docker compose up -d backend...');
    const r3 = await exec('cd /opt/realtime-video && docker compose up -d backend 2>&1', 60000);
    console.log(r3.out);

    // 4. 等待启动
    log('WAIT', '等待后端启动...');
    let started = false;
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000));
      const health = await exec('curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/health');
      if (health.out.trim() === '200') {
        log('OK', '✅ 后端启动成功!');
        started = true;
        break;
      }
      process.stdout.write('.');
    }
    console.log();

    if (started) {
      // 5. 测试登录
      log('TEST', '测试登录...');
      const login = await exec(`curl -s -w "\nHTTP:%{http_code}" -X POST http://127.0.0.1:8080/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin123"}'`);
      console.log(login.out);

      // 6. 检查日志
      log('LOG', '最近日志...');
      const logs = await exec('docker logs --tail 20 realtimevideo-backend 2>&1');
      console.log(logs.out);
      
      // 7. 验证前端
      log('WEB', '验证前端...');
      const web = await exec('curl -s -o /dev/null -w "%{http_code}" http://localhost/');
      console.log(`前端: ${web.out}`);
    }

  } catch (err) {
    console.error('❌', err.message);
  } finally {
    conn.end();
  }
});

conn.on('error', err => { console.error('❌', err.message); process.exit(1); });
conn.connect({ host: HOST, port: 22, username: 'root', password: 'Zq010722.', readyTimeout: 15000 });
